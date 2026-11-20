package com.sangluo.onestep.xiaobu;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;

/** Root screencap for the virtual display, shared by the service and the receiver. */
final class XiaobuCaptureExecutor {
    static final String PREFIX = "/sdcard/onestep_xiaobu_capture_";
    private static final String TAG = "OneStepXiaobu";

    private XiaobuCaptureExecutor() {}

    static boolean allowed(Context context, int display) {
        if (display <= 0) return false;
        boolean enabled = XiaobuDisplay.enabled(context);
        int resolved = XiaobuDisplay.displayId(context);
        Log.i(TAG, "capture allowed display=" + display + " enabled=" + enabled
                + " resolved=" + resolved);
        // The binder caller is restricted to 小布识屏, and the target process resolves the
        // visible OneStep display; the service may see a different slot task, so accept the
        // requested non-main display here.
        return enabled;
    }

    static void capture(Context context, int display, String providedName, String path) {
        String name = providedName;
        if (name == null || name.length() == 0) {
            try {
                DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                Display target = manager == null ? null : manager.getDisplay(display);
                name = target == null ? null : target.getName();
            } catch (Throwable ignored) { }
        }
        if (name == null || name.length() == 0) {
            Log.w(TAG, "capture display name unavailable for " + display);
            return;
        }
        XiaobuShell.Result listing = XiaobuShell.run("dumpsys SurfaceFlinger --display-id");
        if (!listing.ok()) {
            Log.w(TAG, "display list failed: " + listing.output);
            return;
        }
        String token = tokenFor(listing.output, name);
        if (token == null) {
            Log.w(TAG, "no SurfaceFlinger token for " + name);
            return;
        }
        String quoted = quote(path);
        String temp = quote(path + ".tmp");
        XiaobuShell.Result result = XiaobuShell.run(
                "rm -f " + PREFIX + "*.png " + PREFIX + "*.tmp; "
                        + "screencap -p -d " + token + " " + temp + "; "
                        + "chmod 0666 " + temp + " 2>/dev/null; "
                        + "mv -f " + temp + " " + quoted + "; "
                        + "chmod 0666 " + quoted + " 2>/dev/null");
        Log.i(TAG, "screencap display " + display + " token " + token + " code " + result.code
                + (result.output.length() == 0 ? "" : (" " + result.output)));
    }

    private static String tokenFor(String listing, String name) {
        String needle = "displayName=\"" + name + "\"";
        for (String line : listing.split("\n")) {
            if (line.indexOf(needle) < 0) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2 && "Display".equals(parts[0])) return parts[1];
        }
        return null;
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
