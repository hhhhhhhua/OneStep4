package com.sangluo.onestep.feature.screenshot;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;

import com.sangluo.onestep.IRootInputBridgeService;
import com.sangluo.onestep.system.root.ShellCommandResult;
import com.sangluo.onestep.utils.DeviceUtil;
import com.sangluo.onestep.utils.ShizukuServiceUtil;

/**
 * Full-screen host for the screenshot editor. A dedicated activity (rather than an
 * overlay window) is required so the system IME can attach on secondary displays.
 */
public final class ScreenshotEditorActivity extends Activity
        implements ScreenshotController.Listener {
    private static final String TAG = "OneStepScreenshot";
    private static Bitmap pendingBitmap;
    private static int pendingDisplayId;
    private static float[] pendingMainSlotBounds;
    private static Runnable pendingOnClosed;
    private static volatile long lastCreatedUptime;

    private ScreenshotController controller;
    private Runnable onClosed;

    /**
     * Hands the captured bitmap to a fresh editor activity on {@code displayId}. Returns the
     * request uptime, or 0 when the launch could not be queued.
     *
     * <p>When another app covers OneStep, the activity is in the background and a plain
     * {@code startActivity} may be blocked by background-activity-launch rules. The
     * privileged shell is therefore the primary launch path; a direct launch is the
     * fallback when the shell command fails.</p>
     */
    public static long start(Activity host, Bitmap bitmap, int displayId,
                             float[] mainSlotBounds, Runnable onClosed,
                             ScreenshotCaptureClient.CommandRunner runner) {
        pendingBitmap = bitmap;
        pendingDisplayId = displayId;
        pendingMainSlotBounds = mainSlotBounds == null ? null : mainSlotBounds.clone();
        pendingOnClosed = onClosed;
        lastCreatedUptime = 0L;
        final long requestUptime = SystemClock.uptimeMillis();
        new Thread(() -> {
            boolean launched = false;
            if (runner != null) {
                try {
                    ShellCommandResult result = runner.run(buildLaunchCommand(host, displayId), 6);
                    String output = result == null || result.output == null
                            ? "" : result.output;
                    launched = result != null && result.isSuccess()
                            && !output.contains("Error")
                            && (output.contains("Starting") || output.contains("Warning"));
                } catch (RuntimeException error) {
                    Log.w(TAG, "Privileged editor launch failed", error);
                }
            }
            if (!launched) {
                host.runOnUiThread(() -> launchDirect(host, displayId));
            }
        }, "onestep-screenshot-launch").start();
        return requestUptime;
    }

    /** True once an editor instance was created for a request at {@code requestUptime}. */
    public static boolean wasCreatedSince(long requestUptime) {
        return lastCreatedUptime > 0L && lastCreatedUptime >= requestUptime;
    }

    /** Releases a pending session when no editor instance was created. */
    public static void cancelPending() {
        Bitmap bitmap = pendingBitmap;
        clearPending();
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private static String buildLaunchCommand(Activity host, int displayId) {
        String component = host.getPackageName() + "/" + ScreenshotEditorActivity.class.getName();
        return "am start --display " + displayId + " -n '" + component + "' -f 0x10000000";
    }

    private static void launchDirect(Activity host, int displayId) {
        try {
            Intent intent = new Intent(host, ScreenshotEditorActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(displayId);
            host.startActivity(intent, options.toBundle());
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to launch screenshot editor activity", error);
            cancelPending();
        }
    }

    private static void clearPending() {
        pendingBitmap = null;
        pendingDisplayId = 0;
        pendingMainSlotBounds = null;
        pendingOnClosed = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lastCreatedUptime = SystemClock.uptimeMillis();
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        Bitmap bitmap = pendingBitmap;
        int displayId = pendingDisplayId;
        float[] mainSlotBounds = pendingMainSlotBounds;
        onClosed = pendingOnClosed;
        clearPending();
        if (bitmap == null) {
            finish();
            return;
        }
        controller = new ScreenshotController(this, bitmap, displayId, mainSlotBounds, this);
        if (!controller.show()) {
            controller = null;
            bitmap.recycle();
            onClosed();
            return;
        }

    }


    @Override
    public void onClosed() {
        Runnable callback = onClosed;
        onClosed = null;
        if (callback != null) {
            callback.run();
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        if (controller != null && controller.handleBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            ScreenshotController current = controller;
            controller = null;
            current.close();
        }
        super.onDestroy();
    }
}
