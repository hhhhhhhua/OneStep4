package com.sangluo.onestep.feature.screenshot;

/**
 * Pure layout for the screenshot editor toolbar: below the crop rectangle when there is
 * room, otherwise above it, otherwise pinned to the bottom edge. Bounds use
 * {@code {left, top, right, bottom}} in view pixels.
 */
public final class ScreenshotToolbarPolicy {
    private ScreenshotToolbarPolicy() {
    }

    public static float[] toolbarBounds(float[] selection, float toolbarWidth,
                                        float toolbarHeight, float screenWidth,
                                        float screenHeight, float gap, float edgeMargin) {
        float edge = Math.max(0f, edgeMargin);
        float width = Math.min(Math.max(0f, toolbarWidth),
                Math.max(0f, screenWidth - edge * 2f));
        float height = Math.max(0f, toolbarHeight);
        float centerX = selection == null || selection.length < 4
                ? screenWidth / 2f : (selection[0] + selection[2]) / 2f;
        float left = clamp(centerX - width / 2f, edge,
                Math.max(edge, screenWidth - edge - width));

        float top;
        if (selection != null && selection.length >= 4) {
            float below = selection[3] + gap;
            float above = selection[1] - gap - height;
            if (below + height + edge <= screenHeight) {
                top = below;
            } else if (above >= edge) {
                top = above;
            } else {
                top = Math.max(edge, screenHeight - edge - height);
            }
        } else {
            top = Math.max(edge, screenHeight - edge - height);
        }
        top = clamp(top, edge, Math.max(edge, screenHeight - edge - height));
        return new float[]{left, top, left + width, top + height};
    }

    /** Bounds of item {@code index} inside {@code toolbar}, split evenly. */
    public static float[] itemBounds(float[] toolbar, int count, int index) {
        if (toolbar == null || toolbar.length < 4 || count <= 0
                || index < 0 || index >= count) {
            return new float[]{0f, 0f, 0f, 0f};
        }
        float width = (toolbar[2] - toolbar[0]) / count;
        float left = toolbar[0] + width * index;
        return new float[]{left, toolbar[1], left + width, toolbar[3]};
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return (min + max) / 2f;
        }
        return Math.max(min, Math.min(max, value));
    }
}
