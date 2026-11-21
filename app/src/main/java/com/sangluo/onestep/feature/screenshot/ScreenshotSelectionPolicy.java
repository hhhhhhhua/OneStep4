package com.sangluo.onestep.feature.screenshot;

/**
 * Pure geometry for the screenshot crop rectangle. Bounds are stored as
 * {@code {left, top, right, bottom}} in view pixels so the policy stays unit-testable
 * without an Android device.
 */
public final class ScreenshotSelectionPolicy {
    public static final float MIN_SIZE_DP = 96f;
    public static final float INITIAL_SIZE_RATIO = 0.42f;

    public static final int CORNER_NONE = -1;
    public static final int CORNER_TOP_LEFT = 0;
    public static final int CORNER_TOP_RIGHT = 1;
    public static final int CORNER_BOTTOM_RIGHT = 2;
    public static final int CORNER_BOTTOM_LEFT = 3;

    private ScreenshotSelectionPolicy() {
    }

    /** Centered square covering {@link #INITIAL_SIZE_RATIO} of the shorter screen edge. */
    public static float[] initial(float screenWidth, float screenHeight, float minSizePx) {
        float width = Math.max(0f, screenWidth);
        float height = Math.max(0f, screenHeight);
        float side = Math.min(width, height) * INITIAL_SIZE_RATIO;
        side = Math.min(side, Math.min(width, height));
        side = Math.max(side, Math.min(minSizePx, Math.min(width, height)));
        float left = Math.max(0f, (width - side) / 2f);
        float top = Math.max(0f, (height - side) / 2f);
        return new float[]{left, top, left + side, top + side};
    }

    /** Whole-display selection; used by the default 全屏 capture mode. */
    public static float[] fullScreen(float screenWidth, float screenHeight) {
        return new float[]{0f, 0f, Math.max(0f, screenWidth), Math.max(0f, screenHeight)};
    }

    /**
     * Clamps {@code rect} to the screen. Returns {@code null} when the clamped rectangle
     * has less than {@code minSizePx} on either axis, so the caller can fall back to
     * another preset.
     */
    public static float[] clampToScreen(float[] rect, float screenWidth,
                                        float screenHeight, float minSizePx) {
        if (rect == null || rect.length < 4) {
            return null;
        }
        float left = Math.max(0f, Math.min(rect[0], rect[2]));
        float right = Math.min(Math.max(0f, screenWidth), Math.max(rect[0], rect[2]));
        float top = Math.max(0f, Math.min(rect[1], rect[3]));
        float bottom = Math.min(Math.max(0f, screenHeight), Math.max(rect[1], rect[3]));
        float min = Math.max(0f, minSizePx);
        if (right - left < min || bottom - top < min) {
            return null;
        }
        return new float[]{left, top, right, bottom};
    }

    /** Moves the rectangle by a delta while keeping it fully on screen. */
    public static float[] move(float[] bounds, float dx, float dy,
                               float screenWidth, float screenHeight) {
        if (bounds == null || bounds.length < 4) {
            return new float[]{0f, 0f, 0f, 0f};
        }
        float width = bounds[2] - bounds[0];
        float height = bounds[3] - bounds[1];
        float left = clamp(bounds[0] + dx, 0f, Math.max(0f, screenWidth - width));
        float top = clamp(bounds[1] + dy, 0f, Math.max(0f, screenHeight - height));
        return new float[]{left, top, left + width, top + height};
    }

    /**
     * Resizes the rectangle by dragging {@code corner} to {@code (x, y)}. Width and height
     * are independent; the result is kept on screen and never smaller than
     * {@code minSizePx} on either axis.
     */
    public static float[] resize(float[] bounds, int corner, float x, float y,
                                 float minSizePx, float screenWidth, float screenHeight) {
        if (bounds == null || bounds.length < 4) {
            return new float[]{0f, 0f, 0f, 0f};
        }
        float left = bounds[0];
        float top = bounds[1];
        float right = bounds[2];
        float bottom = bounds[3];
        float min = Math.max(0f, minSizePx);
        switch (corner) {
            case CORNER_TOP_LEFT:
                left = clamp(x, 0f, right - min);
                top = clamp(y, 0f, bottom - min);
                break;
            case CORNER_TOP_RIGHT:
                right = clamp(x, left + min, screenWidth);
                top = clamp(y, 0f, bottom - min);
                break;
            case CORNER_BOTTOM_RIGHT:
                right = clamp(x, left + min, screenWidth);
                bottom = clamp(y, top + min, screenHeight);
                break;
            case CORNER_BOTTOM_LEFT:
                left = clamp(x, 0f, right - min);
                bottom = clamp(y, top + min, screenHeight);
                break;
            default:
                break;
        }
        return new float[]{left, top, right, bottom};
    }

    /** Corner whose handle is under the touch, or {@link #CORNER_NONE}. */
    public static int hitHandle(float[] bounds, float x, float y, float hitRadiusPx) {
        if (bounds == null || bounds.length < 4) {
            return CORNER_NONE;
        }
        if (hit(bounds[0], bounds[1], x, y, hitRadiusPx)) {
            return CORNER_TOP_LEFT;
        }
        if (hit(bounds[2], bounds[1], x, y, hitRadiusPx)) {
            return CORNER_TOP_RIGHT;
        }
        if (hit(bounds[2], bounds[3], x, y, hitRadiusPx)) {
            return CORNER_BOTTOM_RIGHT;
        }
        if (hit(bounds[0], bounds[3], x, y, hitRadiusPx)) {
            return CORNER_BOTTOM_LEFT;
        }
        return CORNER_NONE;
    }

    public static boolean contains(float[] bounds, float x, float y) {
        return bounds != null && bounds.length >= 4
                && x >= bounds[0] && x <= bounds[2]
                && y >= bounds[1] && y <= bounds[3];
    }

    private static boolean hit(float cx, float cy, float x, float y, float radiusPx) {
        float dx = x - cx;
        float dy = y - cy;
        float radius = Math.max(0f, radiusPx);
        return dx * dx + dy * dy <= radius * radius;
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return (min + max) / 2f;
        }
        return Math.max(min, Math.min(max, value));
    }
}
