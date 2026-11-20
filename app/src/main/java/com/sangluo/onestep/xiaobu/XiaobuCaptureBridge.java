package com.sangluo.onestep.xiaobu;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Asks the OneStep app to capture the virtual display and reads the PNG back from shared storage. */
final class XiaobuCaptureBridge {
    private static final String MODULE = "com.sangluo.onestepc16";
    private static final String SERVICE = "com.sangluo.onestep.xiaobu.XiaobuCaptureService";
    private static final String RECEIVER = "com.sangluo.onestep.xiaobu.XiaobuCaptureReceiver";
    private static final String ACTION = "com.sangluo.onestep.xiaobu.CAPTURE_DISPLAY";
    private static final String PREFIX = "/sdcard/onestep_xiaobu_capture_";
    private static final long TIMEOUT_MS = 8000;
    private static final int MSG_CAPTURE = 1;
    private static final int MSG_DONE = 2;
    private static final String TAG = "OneStepXiaobu";

    private XiaobuCaptureBridge() {}

    static Bitmap capture(Context context, int display) {
        try {
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                Log.w(TAG, "capture needs all-files access");
                return null;
            }
            String path = PREFIX + display + "_" + SystemClock.elapsedRealtime() + ".png";
            String name = displayName(context, display);
            boolean requested = bindCapture(context, display, name, path);
            if (!requested) {
                Intent request = new Intent();
                request.setComponent(new ComponentName(MODULE, SERVICE));
                request.putExtra("display", display);
                request.putExtra("name", name);
                request.putExtra("path", path);
                boolean started = false;
                try {
                    started = context.startService(request) != null;
                } catch (Throwable error) {
                    Log.w(TAG, "capture service unavailable", error);
                }
                if (!started) {
                    Intent broadcast = new Intent(ACTION);
                    broadcast.setComponent(new ComponentName(MODULE, RECEIVER));
                    broadcast.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                    broadcast.putExtra("display", display);
                    broadcast.putExtra("name", name);
                    broadcast.putExtra("path", path);
                    context.sendBroadcast(broadcast);
                }
            }

            File file = new File(path);
            long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
            while (SystemClock.elapsedRealtime() < deadline) {
                if (file.exists() && file.length() > 0) {
                    Bitmap bitmap = BitmapFactory.decodeFile(path);
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    if (bitmap == null) Log.w(TAG, "screenshot decode failed " + path);
                    return bitmap;
                }
                Thread.sleep(50);
            }
            Log.w(TAG, "screenshot timeout display " + display);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (Throwable error) {
            Log.w(TAG, "screenshot request failed", error);
        }
        return null;
    }

    private static boolean bindCapture(Context context, int display, String name, String path) {
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        final Messenger[] remote = new Messenger[1];
        final ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName component, IBinder binder) {
                remote[0] = new Messenger(binder);
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName component) {
                done.countDown();
            }
        };
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(MODULE, SERVICE));
        try {
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) return false;
        } catch (Throwable error) {
            Log.w(TAG, "capture bind unavailable", error);
            return false;
        }
        boolean delivered = false;
        try {
            if (!connected.await(2000, TimeUnit.MILLISECONDS) || remote[0] == null) return false;
            Message message = Message.obtain(null, MSG_CAPTURE);
            Bundle data = message.getData();
            data.putInt("display", display);
            data.putString("name", name);
            data.putString("path", path);
            message.replyTo = new Messenger(new Handler(Looper.getMainLooper()) {
                @Override public void handleMessage(Message reply) {
                    if (reply.what == MSG_DONE) done.countDown();
                }
            });
            remote[0].send(message);
            delivered = true;
            if (!done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "capture bind timeout display " + display);
            }
        } catch (Throwable error) {
            Log.w(TAG, "capture bind request failed", error);
        } finally {
            try {
                context.unbindService(connection);
            } catch (Throwable ignored) { }
        }
        return delivered;
    }

    private static String displayName(Context context, int display) {
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display target = manager == null ? null : manager.getDisplay(display);
            return target == null ? null : target.getName();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
