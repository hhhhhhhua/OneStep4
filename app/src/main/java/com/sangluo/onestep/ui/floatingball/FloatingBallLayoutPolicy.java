package com.sangluo.onestep.ui.floatingball;

/**
 * Pure geometry for the floating ball's single-ring menu.
 *
 * <p>Index {@link #SWITCH_INDEX} (0) is always the layer switch control at the
 * 12 o'clock position; application items follow clockwise. Every measurement is
 * expressed in dp (or px when the caller already converted) so the policy stays
 * unit-testable without an Android device.</p>
 */
public final class FloatingBallLayoutPolicy {
    public static final float DEFAULT_RADIUS_DP = 120f;
    public static final float MIN_RADIUS_DP = 100f;
    public static final float MAX_RADIUS_DP = 140f;
    public static final float ITEM_RADIUS_DP = 20f;
    public static final float HIT_SLOP_DP = 24f;
    /** Reserved ring index of the layer switch control. */
    public static final int SWITCH_INDEX = 0;
    /** Distance of the 识屏 control from the switch centre, in item radii. */
    public static final float OCR_OFFSET_ITEM_RADII = 2.15f;
    /** Distance of the 杀死应用 control from the switch centre, in item radii. */
    public static final float KILL_OFFSET_ITEM_RADII = 2.15f;
    /** Distance of the 截图 control from the switch centre, in item radii. */
    public static final float SCREENSHOT_OFFSET_ITEM_RADII = 2.15f;
    /** Distance of the 通知 control from the switch centre, in item radii. */
    public static final float NOTIFICATION_OFFSET_ITEM_RADII = 2.15f;
    /** Distance of the 放入后台 control from the switch centre, in item radii. */
    public static final float BACKGROUND_OFFSET_ITEM_RADII = 2.15f;
    /** Minimum distance a satellite keeps from the screen edge, in item radii. */
    public static final float SATELLITE_EDGE_ITEM_RADII = 1.15f;

    private FloatingBallLayoutPolicy() {
    }

    /** Ring slots for {@code appCount} apps plus the always-present switch control. */
    public static int slotCount(int appCount) {
        return Math.max(0, appCount) + 1;
    }

    /**
     * Picks the ring radius for the space available around the ball. The item radius
     * is subtracted first so a full-size icon always stays inside the ring budget.
     */
    public static float resolveRadiusDp(float availableDp) {
        float usable = Math.max(0f, availableDp) - ITEM_RADIUS_DP;
        return Math.max(MIN_RADIUS_DP, Math.min(MAX_RADIUS_DP, usable));
    }

    /** Distance from {@code (centerX, centerY)} to the closest screen edge. */
    public static float availableRadius(float centerX, float centerY,
                                        float width, float height) {
        float horizontal = Math.min(centerX, width - centerX);
        float vertical = Math.min(centerY, height - centerY);
        return Math.max(0f, Math.min(horizontal, vertical));
    }

    /** Keeps the whole ring (radius plus item radius) inside the given width. */
    public static float clampCenterX(float centerX, float width, float radiusPx) {
        float margin = Math.max(0f, radiusPx) + ITEM_RADIUS_DP;
        if (width <= margin * 2f) {
            return width / 2f;
        }
        return Math.max(margin, Math.min(width - margin, centerX));
    }

    public static float clampCenterY(float centerY, float height, float radiusPx) {
        float margin = Math.max(0f, radiusPx) + ITEM_RADIUS_DP;
        if (height <= margin * 2f) {
            return height / 2f;
        }
        return Math.max(margin, Math.min(height - margin, centerY));
    }

    /** Angle in degrees of a ring index, starting at 12 o'clock and going clockwise. */
    public static float angleFor(int index, int slotCount) {
        if (slotCount <= 0) {
            return -90f;
        }
        int wrapped = ((index % slotCount) + slotCount) % slotCount;
        return -90f + (360f / slotCount) * wrapped;
    }

    /**
     * Horizontal centre of the 识屏 control: on the switch's horizontal line, to its
     * right, outside the ring slots so it never costs an app position.
     */
    public static float ocrCenterX(float switchCenterX, float itemRadiusPx) {
        return switchCenterX + itemRadiusPx * OCR_OFFSET_ITEM_RADII;
    }

    /**
     * Horizontal centre of the 杀死应用 control: on the switch's horizontal line, to its
     * left, mirroring the 识屏 control so neither costs an app position.
     */
    public static float killCenterX(float switchCenterX, float itemRadiusPx) {
        return switchCenterX - itemRadiusPx * KILL_OFFSET_ITEM_RADII;
    }

    public static float screenshotCenterX(float switchCenterX, float itemRadiusPx) {
        return switchCenterX + itemRadiusPx * SCREENSHOT_OFFSET_ITEM_RADII;
    }

    /**
     * Vertical centre of the 截图 control: above the 识屏 control, mirrored diagonally.
     * Clamped to the screen so the satellite never leaves the visible area when the ball
     * is dragged to the top edge.
     */
    public static float screenshotCenterY(float switchCenterY, float itemRadiusPx,
                                          float heightPx) {
        float edge = itemRadiusPx * SATELLITE_EDGE_ITEM_RADII;
        float maxY = Math.max(edge, heightPx - edge);
        float target = switchCenterY - itemRadiusPx * SCREENSHOT_OFFSET_ITEM_RADII;
        return Math.max(edge, Math.min(maxY, target));
    }

    /**
     * Horizontal centre of the 放入后台 control: above the 杀死应用 control, on the
     * left side, mirroring the 截图 control so it never costs an app position.
     */
    public static float backgroundCenterX(float switchCenterX, float itemRadiusPx) {
        return switchCenterX - itemRadiusPx * BACKGROUND_OFFSET_ITEM_RADII;
    }

    /** Vertical centre of the 放入后台 control, clamped to the screen top edge. */
    public static float backgroundCenterY(float switchCenterY, float itemRadiusPx,
                                          float heightPx) {
        float edge = itemRadiusPx * SATELLITE_EDGE_ITEM_RADII;
        float maxY = Math.max(edge, heightPx - edge);
        float target = switchCenterY - itemRadiusPx * BACKGROUND_OFFSET_ITEM_RADII;
        return Math.max(edge, Math.min(maxY, target));
    }

    /**
     * Horizontal centre of the 通知 control: below the 识屏 control, on the right
     * side, so it never costs an app position.
     */
    public static float notificationCenterX(float switchCenterX, float itemRadiusPx) {
        return switchCenterX + itemRadiusPx * NOTIFICATION_OFFSET_ITEM_RADII;
    }

    /**
     * Vertical centre of the 通知 control: below the 识屏 control, clamped to the
     * screen so the satellite never leaves the visible area near the bottom edge.
     */
    public static float notificationCenterY(float switchCenterY, float itemRadiusPx,
                                            float heightPx) {
        float edge = itemRadiusPx * SATELLITE_EDGE_ITEM_RADII;
        float maxY = Math.max(edge, heightPx - edge);
        float target = switchCenterY + itemRadiusPx * NOTIFICATION_OFFSET_ITEM_RADII;
        return Math.max(edge, Math.min(maxY, target));
    }

    /**
     * Centres of every ring slot as a flat {@code [x0, y0, x1, y1, ...]} array.
     * The caller passes the radius in pixels.
     */
    public static float[] computeCenters(float centerX, float centerY,
                                         float radiusPx, int slotCount) {
        if (slotCount <= 0) {
            return new float[0];
        }
        float[] centers = new float[slotCount * 2];
        for (int index = 0; index < slotCount; index++) {
            double radians = Math.toRadians(angleFor(index, slotCount));
            centers[index * 2] = (float) (centerX + Math.cos(radians) * radiusPx);
            centers[index * 2 + 1] = (float) (centerY + Math.sin(radians) * radiusPx);
        }
        return centers;
    }

    /**
     * Nearest ring slot under the finger, or -1 when the touch is outside every
     * item's hit area. {@code centers} comes from {@link #computeCenters}.
     */
    public static int hitTest(float[] centers, float touchX, float touchY,
                              float itemRadiusPx, float slopPx) {
        if (centers == null || centers.length < 2) {
            return -1;
        }
        float threshold = Math.max(0f, itemRadiusPx) + Math.max(0f, slopPx);
        float thresholdSquared = threshold * threshold;
        int bestIndex = -1;
        float bestDistanceSquared = Float.MAX_VALUE;
        for (int index = 0; index * 2 + 1 < centers.length; index++) {
            float dx = touchX - centers[index * 2];
            float dy = touchY - centers[index * 2 + 1];
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared <= thresholdSquared && distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    /** True when the finger returned to the ball itself, i.e. no ring slot is aimed at. */
    public static boolean isOverBall(float centerX, float centerY, float ballRadiusPx,
                                     float touchX, float touchY) {
        float dx = touchX - centerX;
        float dy = touchY - centerY;
        float radius = Math.max(0f, ballRadiusPx);
        return dx * dx + dy * dy <= radius * radius;
    }
}
