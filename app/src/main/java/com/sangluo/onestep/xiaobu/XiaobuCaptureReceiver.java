package com.sangluo.onestep.xiaobu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Broadcast fallback for {@link XiaobuCaptureService}. */
public final class XiaobuCaptureReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.sangluo.onestep.xiaobu.CAPTURE_DISPLAY";
    private static final String TAG = "OneStepXiaobu";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        final int display = intent.getIntExtra("display", -1);
        final String name = intent.getStringExtra("name");
        final String path = intent.getStringExtra("path");
        if (!XiaobuCaptureExecutor.allowed(context, display) || path == null
                || !path.startsWith(XiaobuCaptureExecutor.PREFIX)) {
            Log.w(TAG, "capture request rejected display=" + display + " path=" + path);
            return;
        }
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    XiaobuCaptureExecutor.capture(app, display, name, path);
                } finally {
                    pending.finish();
                }
            }
        }, "onestep-xiaobu-receiver").start();
    }
}
