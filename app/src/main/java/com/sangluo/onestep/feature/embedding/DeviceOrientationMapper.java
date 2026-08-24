package com.sangluo.onestep.feature.embedding;

import android.view.OrientationEventListener;
import android.view.Surface;

import com.sangluo.onestep.system.root.PersistentRootShell;
import com.sangluo.onestep.system.root.ShellCommandResult;

/** Converts physical sensor angles into the landscape rotations used by hosted displays. */
public final class DeviceOrientationMapper {
    private static final int LANDSCAPE_MIN_DEGREES = 60;
    private static final int LANDSCAPE_MAX_DEGREES = 120;
    private static final int REVERSE_LANDSCAPE_MIN_DEGREES = 240;
    private static final int REVERSE_LANDSCAPE_MAX_DEGREES = 300;
    private static final int PORTRAIT_EDGE_DEGREES = 30;
    private static final int UPSIDE_DOWN_MIN_DEGREES = 150;
    private static final int UPSIDE_DOWN_MAX_DEGREES = 210;
    private static final PersistentRootShell persistentRootShell = new PersistentRootShell();
    private DeviceOrientationMapper() {
    }

    public static int mapLandscapeRotation(int orientation) {
        String rotation_flag = "0";
        String shellCmd = "settings get system accelerometer_rotation";
        ShellCommandResult res = persistentRootShell.run(shellCmd, 10);
        rotation_flag = res.output;
        // console.log("shellCmd: " + shellCmd + " res: " + res.output.value)
        if ("1".equals(rotation_flag)){
            if (orientation <= 30 || orientation >= 330) {
                return 0;
            }
            if (orientation >= 60 && orientation <= 120) {
                return 3;
            }
            if (orientation >= 150 && orientation <= 210) {
                return 2;
            }
            if (orientation >= 240 && orientation <= 300) {
                return 1;
            }
        }
        return -1;
    }

    public static boolean isStablePortrait(int orientation) {
        return orientation != OrientationEventListener.ORIENTATION_UNKNOWN
                && (orientation <= PORTRAIT_EDGE_DEGREES
                || orientation >= 360 - PORTRAIT_EDGE_DEGREES
                || (orientation >= UPSIDE_DOWN_MIN_DEGREES
                && orientation <= UPSIDE_DOWN_MAX_DEGREES));
    }

    public static boolean isLandscapeRotation(int rotation) {
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
    }
}
