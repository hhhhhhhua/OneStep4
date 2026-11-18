package com.sangluo.onestep.ui.floatingball;

/**
 * Timing and slop rules for the floating ball gesture: long press to expand,
 * dwell on the switch control to change layer, and a short re-arm window after
 * a layer change so the finger cannot accidentally activate the new layer.
 */
public final class FloatingBallGesturePolicy {
    /** Finger must stay this long on the ball before the ring expands. */
    public static final long LONG_PRESS_TIMEOUT_MS = 500L;
    /** Finger must rest this long on the switch control before the layer flips. */
    public static final long SWITCH_DWELL_MS = 300L;
    /** Locked (click-to-open) expansion collapses after this idle time. */
    public static final long LOCKED_AUTO_COLLAPSE_MS = 1500L;
    /** Movement above this cancels the pending long press (and starts a ball drag). */
    public static final float TOUCH_SLOP_DP = 8f;
    /** After a layer flip the finger must move this far before items re-highlight. */
    public static final float RELIGHT_SLOP_DP = 12f;

    private FloatingBallGesturePolicy() {
    }

    public static boolean isWithinSlop(float dx, float dy, float slopPx) {
        float slop = Math.max(0f, slopPx);
        return dx * dx + dy * dy <= slop * slop;
    }

    /** Long press still pending: the finger has not drifted past the touch slop. */
    public static boolean shouldStartLongPress(float dx, float dy, float slopPx) {
        return isWithinSlop(dx, dy, slopPx);
    }

    /** True once the finger has rested on the switch control long enough to flip layers. */
    public static boolean isSwitchDwellElapsed(long highlightedSinceUptime, long nowUptime) {
        return highlightedSinceUptime > 0L
                && nowUptime - highlightedSinceUptime >= SWITCH_DWELL_MS;
    }

    /** True once the finger moved far enough after a layer flip to highlight again. */
    public static boolean canRelight(float dx, float dy, float relightPx) {
        return !isWithinSlop(dx, dy, relightPx);
    }

    /** True when a locked expansion has been idle long enough to collapse itself. */
    public static boolean isLockExpired(long lockedSinceUptime, long nowUptime) {
        return lockedSinceUptime > 0L
                && nowUptime - lockedSinceUptime >= LOCKED_AUTO_COLLAPSE_MS;
    }
}
