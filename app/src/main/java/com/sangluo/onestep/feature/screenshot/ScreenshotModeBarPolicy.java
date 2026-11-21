package com.sangluo.onestep.feature.screenshot;

/**
 * Pure layout for the capture-mode chips (全屏 / 区域 / 主slot) shown above the crop
 * rectangle. Bounds use {@code {left, top, right, bottom}} in view pixels so the policy
 * stays unit-testable without an Android device.
 */
public final class ScreenshotModeBarPolicy {
    private ScreenshotModeBarPolicy() {
    }

    /**
     * The chip bar is centred on the crop rectangle and lifted above its top edge. When
     * the selection reaches the top of the screen the bar is pinned to the top edge
     * instead, so it stays reachable in full-screen mode.
     */
    public static float[] barBounds(float[] selection, int count, float chipWidth,
                                    float chipHeight, float gap, float screenWidth,
                                    float screenHeight, float edgeMargin,
                                    float selectionGap) {
        int chips = Math.max(1, count);
        float gapPx = Math.max(0f, gap);
        float edge = Math.max(0f, edgeMargin);
        float total = Math.max(0f, chipWidth) * chips + gapPx * (chips - 1);
        float width = Math.min(total, Math.max(0f, screenWidth - edge * 2f));
        float centerX = selection != null && selection.length >= 4
                ? (selection[0] + selection[2]) / 2f : screenWidth / 2f;
        float left = clamp(centerX - width / 2f, edge,
                Math.max(edge, screenWidth - edge - width));
        float height = Math.max(0f, chipHeight);
        float top = selection != null && selection.length >= 4
                ? selection[1] - Math.max(0f, selectionGap) - height
                : edge;
        top = clamp(top, edge, Math.max(edge, screenHeight - edge - height));
        return new float[]{left, top, left + width, top + height};
    }

    /** Bounds of chip {@code index} inside {@code bar}, separated by {@code gap}. */
    public static float[] chipBounds(float[] bar, int count, int index, float gap) {
        if (bar == null || bar.length < 4 || count <= 0
                || index < 0 || index >= count) {
            return new float[]{0f, 0f, 0f, 0f};
        }
        float gapPx = Math.max(0f, gap);
        float available = Math.max(0f, (bar[2] - bar[0]) - gapPx * (count - 1));
        float width = available / count;
        float left = bar[0] + (width + gapPx) * index;
        return new float[]{left, bar[1], left + width, bar[3]};
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return (min + max) / 2f;
        }
        return Math.max(min, Math.min(max, value));
    }
}
