package com.sangluo.onestep.feature.screenshot;

/** How the screenshot editor presets its crop rectangle. */
public enum ScreenshotCaptureMode {
    /** The whole captured display. This is the default. */
    FULL_SCREEN,
    /** A user-adjustable region, seeded with the centered square. */
    REGION,
    /** Only the active main slot pane of the OneStep display. */
    MAIN_SLOT
}
