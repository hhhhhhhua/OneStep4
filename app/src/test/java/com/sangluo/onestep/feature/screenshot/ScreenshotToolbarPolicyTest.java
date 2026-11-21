package com.sangluo.onestep.feature.screenshot;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ScreenshotToolbarPolicyTest {
    private static final float DELTA = 0.01f;

    @Test
    public void toolbarSitsBelowTheSelectionWhenThereIsRoom() {
        float[] toolbar = ScreenshotToolbarPolicy.toolbarBounds(
                new float[]{100f, 100f, 400f, 300f},
                240f, 52f, 600f, 800f, 12f, 8f);

        assertArrayEquals(new float[]{130f, 312f, 370f, 364f}, toolbar, DELTA);
    }

    @Test
    public void toolbarFlipsAboveWhenTheBottomIsCrowded() {
        float[] toolbar = ScreenshotToolbarPolicy.toolbarBounds(
                new float[]{100f, 700f, 400f, 760f},
                240f, 52f, 600f, 800f, 12f, 8f);

        assertEquals(636f, toolbar[1], DELTA);
        assertEquals(688f, toolbar[3], DELTA);
    }

    @Test
    public void toolbarPinsToTheBottomWhenNeitherSideFits() {
        float[] toolbar = ScreenshotToolbarPolicy.toolbarBounds(
                new float[]{0f, 0f, 600f, 800f},
                240f, 52f, 600f, 800f, 12f, 8f);

        assertEquals(740f, toolbar[1], DELTA);
        assertEquals(792f, toolbar[3], DELTA);
    }

    @Test
    public void toolbarWidthIsClampedToTheScreen() {
        float[] toolbar = ScreenshotToolbarPolicy.toolbarBounds(
                new float[]{0f, 100f, 200f, 300f},
                1000f, 52f, 600f, 800f, 12f, 8f);

        assertEquals(8f, toolbar[0], DELTA);
        assertEquals(592f, toolbar[2], DELTA);
    }

    @Test
    public void itemsSplitTheToolbarEvenly() {
        float[] toolbar = {100f, 200f, 340f, 252f};

        assertArrayEquals(new float[]{100f, 200f, 180f, 252f},
                ScreenshotToolbarPolicy.itemBounds(toolbar, 3, 0), DELTA);
        assertArrayEquals(new float[]{180f, 200f, 260f, 252f},
                ScreenshotToolbarPolicy.itemBounds(toolbar, 3, 1), DELTA);
        assertArrayEquals(new float[]{260f, 200f, 340f, 252f},
                ScreenshotToolbarPolicy.itemBounds(toolbar, 3, 2), DELTA);
    }

    @Test
    public void invalidItemIndexReturnsEmptyBounds() {
        float[] bounds = ScreenshotToolbarPolicy.itemBounds(
                new float[]{0f, 0f, 100f, 50f}, 2, 5);

        assertArrayEquals(new float[]{0f, 0f, 0f, 0f}, bounds, DELTA);
    }
}
