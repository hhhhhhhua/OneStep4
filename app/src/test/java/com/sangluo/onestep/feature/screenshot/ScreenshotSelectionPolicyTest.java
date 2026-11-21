package com.sangluo.onestep.feature.screenshot;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenshotSelectionPolicyTest {
    private static final float DELTA = 0.01f;

    @Test
    public void initialSelectionIsCenteredSquare() {
        float[] bounds = ScreenshotSelectionPolicy.initial(1000f, 800f, 100f);

        assertArrayEquals(new float[]{332f, 232f, 668f, 568f}, bounds, DELTA);
    }

    @Test
    public void initialSelectionHonoursMinimumSize() {
        float[] bounds = ScreenshotSelectionPolicy.initial(120f, 120f, 100f);

        assertEquals(100f, bounds[2] - bounds[0], DELTA);
        assertEquals(100f, bounds[3] - bounds[1], DELTA);
    }

    @Test
    public void moveKeepsSizeAndStaysOnScreen() {
        float[] moved = ScreenshotSelectionPolicy.move(
                new float[]{100f, 100f, 300f, 300f}, 50f, -50f, 1000f, 1000f);
        assertArrayEquals(new float[]{150f, 50f, 350f, 250f}, moved, DELTA);

        float[] clamped = ScreenshotSelectionPolicy.move(
                new float[]{0f, 0f, 200f, 200f}, 1000f, 1000f, 500f, 500f);
        assertArrayEquals(new float[]{300f, 300f, 500f, 500f}, clamped, DELTA);

        float[] clampedNegative = ScreenshotSelectionPolicy.move(
                new float[]{100f, 100f, 300f, 300f}, -500f, -500f, 500f, 500f);
        assertArrayEquals(new float[]{0f, 0f, 200f, 200f}, clampedNegative, DELTA);
    }

    @Test
    public void resizeTopLeftStopsAtMinimumSize() {
        float[] resized = ScreenshotSelectionPolicy.resize(
                new float[]{100f, 100f, 300f, 300f},
                ScreenshotSelectionPolicy.CORNER_TOP_LEFT,
                290f, 290f, 100f, 1000f, 1000f);

        assertArrayEquals(new float[]{200f, 200f, 300f, 300f}, resized, DELTA);
    }

    @Test
    public void resizeBottomRightStopsAtScreenEdge() {
        float[] resized = ScreenshotSelectionPolicy.resize(
                new float[]{100f, 100f, 300f, 300f},
                ScreenshotSelectionPolicy.CORNER_BOTTOM_RIGHT,
                1000f, 1000f, 100f, 500f, 600f);

        assertArrayEquals(new float[]{100f, 100f, 500f, 600f}, resized, DELTA);
    }

    @Test
    public void hitHandleFindsCornersOnly() {
        float[] bounds = {10f, 20f, 110f, 120f};

        assertEquals(ScreenshotSelectionPolicy.CORNER_TOP_LEFT,
                ScreenshotSelectionPolicy.hitHandle(bounds, 10f, 20f, 16f));
        assertEquals(ScreenshotSelectionPolicy.CORNER_TOP_RIGHT,
                ScreenshotSelectionPolicy.hitHandle(bounds, 110f, 20f, 16f));
        assertEquals(ScreenshotSelectionPolicy.CORNER_BOTTOM_RIGHT,
                ScreenshotSelectionPolicy.hitHandle(bounds, 110f, 120f, 16f));
        assertEquals(ScreenshotSelectionPolicy.CORNER_BOTTOM_LEFT,
                ScreenshotSelectionPolicy.hitHandle(bounds, 10f, 120f, 16f));
        assertEquals(ScreenshotSelectionPolicy.CORNER_NONE,
                ScreenshotSelectionPolicy.hitHandle(bounds, 60f, 70f, 16f));
        assertEquals(ScreenshotSelectionPolicy.CORNER_NONE,
                ScreenshotSelectionPolicy.hitHandle(bounds, 40f, 20f, 16f));
    }

    @Test
    public void containsUsesTheInclusiveRectangle() {
        float[] bounds = {10f, 20f, 110f, 120f};

        assertTrue(ScreenshotSelectionPolicy.contains(bounds, 60f, 70f));
        assertTrue(ScreenshotSelectionPolicy.contains(bounds, 10f, 20f));
        assertFalse(ScreenshotSelectionPolicy.contains(bounds, 9f, 70f));
        assertFalse(ScreenshotSelectionPolicy.contains(bounds, 60f, 121f));
    }
}
