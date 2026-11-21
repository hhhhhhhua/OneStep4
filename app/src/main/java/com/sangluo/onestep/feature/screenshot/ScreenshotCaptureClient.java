package com.sangluo.onestep.feature.screenshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import com.sangluo.onestep.system.root.ShellCommandResult;

import java.io.File;

/**
 * Captures a display through the privileged shell. The PNG is written into the app's
 * external files directory, which both the shell user and the app can access, then decoded
 * and deleted immediately.
 */
public final class ScreenshotCaptureClient {
    private static final String TAG = "OneStepScreenshot";
    private static final String DISPLAY_LIST_COMMAND = "dumpsys SurfaceFlinger --display-id";
    private static final int DISPLAY_LIST_TIMEOUT_SECONDS = 5;
    private static final int CAPTURE_TIMEOUT_SECONDS = 12;
    private static final long TOKEN_RETRY_DELAY_MS = 120L;

    public interface CommandRunner {
        ShellCommandResult run(String command, int timeoutSeconds);
    }

    private ScreenshotCaptureClient() {
    }

    /** Returns the decoded screenshot, or null after logging the failure. */
    public static Bitmap capture(Context context, int displayId, CommandRunner runner) {
        if (context == null || runner == null) {
            return null;
        }
        File directory = context.getExternalFilesDir("screenshots");
        if (directory == null) {
            Log.w(TAG, "External screenshot directory unavailable");
            return null;
        }
        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();
        File file = new File(directory, "shot-" + SystemClock.elapsedRealtime() + ".png");
        String command = buildCommand(context, displayId, runner, file);
        if (command == null) {
            Log.w(TAG, "No SurfaceFlinger token for display " + displayId);
            return null;
        }
        long startedAt = SystemClock.elapsedRealtime();
        int exitCode = -1;
        Bitmap bitmap = null;
        try {
            ShellCommandResult result = runner.run(command, CAPTURE_TIMEOUT_SECONDS);
            exitCode = result == null ? -1 : result.exitCode;
            if (file.isFile() && file.length() > 0L) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Capture command failed", error);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        Log.i(TAG, "Capture display=" + displayId + " exit=" + exitCode
                + " bitmap=" + (bitmap == null ? "null"
                : bitmap.getWidth() + "x" + bitmap.getHeight())
                + " elapsed=" + (SystemClock.elapsedRealtime() - startedAt) + "ms");
        return bitmap;
    }

    private static String buildCommand(Context context, int displayId, CommandRunner runner,
                                       File file) {
        String quoted = quote(file.getAbsolutePath());
        String action;
        if (displayId == Display.DEFAULT_DISPLAY) {
            action = "screencap -p " + quoted;
        } else {
            String token = resolveToken(context, displayId, runner);
            if (token == null) {
                return null;
            }
            action = "screencap -p -d " + token + " " + quoted;
        }
        return "rm -f " + quoted + "; " + action + "; chmod 0666 " + quoted;
    }

    private static String resolveToken(Context context, int displayId, CommandRunner runner) {
        String name = displayName(context, displayId);
        if (name == null || name.isEmpty()) {
            return null;
        }
        String token = tokenFor(runner, name);
        if (token != null) {
            return token;
        }
        // A display that was just registered may not be listed yet.
        try {
            Thread.sleep(TOKEN_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return tokenFor(runner, name);
    }

    private static String tokenFor(CommandRunner runner, String displayName) {
        ShellCommandResult listing = runner.run(
                DISPLAY_LIST_COMMAND, DISPLAY_LIST_TIMEOUT_SECONDS);
        if (listing == null || listing.output == null) {
            return null;
        }
        return SurfaceFlingerDisplayParser.tokenFor(listing.output, displayName);
    }

    private static String displayName(Context context, int displayId) {
        DisplayManager manager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (manager == null) {
            return "";
        }
        Display display = manager.getDisplay(displayId);
        return display == null ? "" : display.getName();
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
