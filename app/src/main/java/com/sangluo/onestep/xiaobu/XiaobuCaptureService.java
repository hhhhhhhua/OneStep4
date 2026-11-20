package com.sangluo.onestep.xiaobu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

/** Capture service hosted by the OneStep app; captures the OneStep display through root. */
public final class XiaobuCaptureService extends Service {
    private static final String TAG = "OneStepXiaobu";
    private static final String CHANNEL = "onestep_xiaobu_capture";
    private static final int NOTIFICATION_ID = 0x0E54;
    private static final int MSG_CAPTURE = 1;
    private static final int MSG_DONE = 2;

    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message message) {
            if (message.what != MSG_CAPTURE) return;
            final Bundle data = message.getData();
            final int display = data.getInt("display", -1);
            final String name = data.getString("name");
            final String path = data.getString("path");
            final Messenger reply = message.replyTo;
            final Context app = getApplicationContext();
            if (!XiaobuCaptureExecutor.allowed(app, display) || path == null
                    || !path.startsWith(XiaobuCaptureExecutor.PREFIX)) {
                Log.w(TAG, "capture bind rejected display=" + display + " path=" + path);
                reply(reply, false);
                return;
            }
            new Thread(new Runnable() {
                @Override public void run() {
                    boolean ok = true;
                    try {
                        XiaobuCaptureExecutor.capture(app, display, name, path);
                    } catch (Throwable error) {
                        ok = false;
                        Log.w(TAG, "capture bind failed", error);
                    } finally {
                        reply(reply, ok);
                    }
                }
            }, "onestep-xiaobu-bind").start();
        }
    });

    private static void reply(Messenger messenger, boolean ok) {
        if (messenger == null) return;
        try {
            messenger.send(Message.obtain(null, MSG_DONE, ok ? 1 : 0, 0));
        } catch (Throwable ignored) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL, "小布识屏截图", NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setContentTitle("OneStep")
                    .setContentText("小布识屏截图服务运行中")
                    .setOngoing(true)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
        } catch (Throwable error) {
            Log.w(TAG, "foreground notification unavailable", error);
        }
    }

    @Override public IBinder onBind(Intent intent) {
        int uid = Binder.getCallingUid();
        if (!allowedUid(uid)) {
            Log.w(TAG, "capture bind rejected uid " + uid);
            return null;
        }
        return messenger.getBinder();
    }

    private boolean allowedUid(int uid) {
        if (uid == android.os.Process.myUid() || uid == 0 || uid == 1000) return true;
        String[] packages = getPackageManager().getPackagesForUid(uid);
        if (packages != null) {
            for (String name : packages) {
                if (XiaobuDisplay.TARGET_PACKAGE.equals(name)) return true;
            }
        }
        return false;
    }

    @Override public int onStartCommand(Intent intent, int flags, final int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        final int display = intent.getIntExtra("display", -1);
        final String name = intent.getStringExtra("name");
        final String path = intent.getStringExtra("path");
        if (!XiaobuCaptureExecutor.allowed(this, display) || path == null
                || !path.startsWith(XiaobuCaptureExecutor.PREFIX)) {
            Log.w(TAG, "capture service rejected display=" + display + " path=" + path);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    XiaobuCaptureExecutor.capture(app, display, name, path);
                } finally {
                    stopSelf(startId);
                }
            }
        }, "onestep-xiaobu-capture").start();
        return START_NOT_STICKY;
    }
}
