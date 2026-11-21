package com.sangluo.onestep.feature.screenshot;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ScreenshotModeBarPolicyTest {
    private static final float DELTA = 0.01f;

    @Test
    public void barSitsAboveTheSelectionAndFollowsItsCentre() {
        float[] bar = ScreenshotModeBarPolicy.barBounds(
                new float[]{100f, 300f, 400f, 500f}, 3,
                60f, 32f, 6f, 600f, 800f, 8f, 10f);

        assertArrayEquals(new float[]{154f, 258f, 346f, 290f}, bar, DELTA);
    }

    @Test
    public void barPinsToTheTopWhenTheSelectionFillsTheScreen() {
        float[] bar = ScreenshotModeBarPolicy.barBounds(
                new float[]{0f, 0f, 600f, 800f}, 3,
                60f, 32f, 6f, 600f, 800f, 8f, 10f);

        assertArrayEquals(new float[]{204f, 8f, 396f, 40f}, bar, DELTA);
    }

    @Test
    public void barWidthIsClampedToTheScreen() {
        float[] bar = ScreenshotModeBarPolicy.barBounds(
                null, 3, 60f, 32f, 6f, 150f, 800f, 8f, 10f);

        assertEquals(8f, bar[0], DELTA);
        assertEquals(142f, bar[2], DELTA);
    }

    @Test
    public void chipsSplitTheBarWithGaps() {
        float[] bar = {100f, 200f, 340f, 252f};

        assertArrayEquals(new float[]{100f, 200f, 176f, 252f},
                ScreenshotModeBarPolicy.chipBounds(bar, 3, 0, 6f), DELTA);
        assertArrayEquals(new float[]{182f, 200f, 258f, 252f},
                ScreenshotModeBarPolicy.chipBounds(bar, 3, 1, 6f), DELTA);
        assertArrayEquals(new float[]{264f, 200f, 340f, 252f},
                ScreenshotModeBarPolicy.chipBounds(bar, 3, 2, 6f), DELTA);
    }

    @Test
    public void invalidChipIndexReturnsEmptyBounds() {
        float[] bounds = ScreenshotModeBarPolicy.chipBounds(
                new float[]{0f, 0f, 100f, 50f}, 3, 5, 6f);

        assertArrayEquals(new float[]{0f, 0f, 0f, 0f}, bounds, DELTA);
    }
}
