package com.sangluo.onestep.ui.gesture;

/** Direction and distance policy for entering or exiting OneStep from a top corner. */
public final class CornerTriggerGesturePolicy {
    private static final float MIN_DOWNWARD_DISTANCE_RATIO = 0.35f;
    private static final float MIN_INWARD_TO_DOWNWARD_RATIO = 0.25f;

    private CornerTriggerGesturePolicy() {
    }

    public static boolean matches(boolean left, float dx, float dy, int triggerDistance) {
        float distance = Math.max(1, triggerDistance);
        float inwardDistance = left ? dx : -dx;
        return inwardDistance > distance
                && dy > distance * MIN_DOWNWARD_DISTANCE_RATIO
                && inwardDistance > Math.abs(dy) * MIN_INWARD_TO_DOWNWARD_RATIO;
    }

    /**
     * Mirror of {@link #matches}: a swipe that returns toward the top corner
     * (outward horizontally plus upward) so OneStep can be dismissed.
     */
    public static boolean matchesExit(boolean left, float dx, float dy, int triggerDistance) {
        float distance = Math.max(1, triggerDistance);
        float outwardDistance = left ? -dx : dx;
        float upwardDistance = -dy;
        return outwardDistance > distance
                && upwardDistance > distance * MIN_DOWNWARD_DISTANCE_RATIO
                && outwardDistance > Math.abs(dy) * MIN_INWARD_TO_DOWNWARD_RATIO;
    }
}
