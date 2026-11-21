package com.sangluo.onestep.feature.screenshot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MosaicBlockPolicyTest {
    @Test
    public void blockRangeCoversTheBrushCircle() {
        assertEquals(1, MosaicBlockPolicy.minBlockIndex(10f, 5f, 4f));
        assertEquals(3, MosaicBlockPolicy.maxBlockIndex(10f, 5f, 4f));
        assertEquals(-2, MosaicBlockPolicy.minBlockIndex(-3f, 5f, 4f));
    }

    @Test
    public void blockTouchesCircleWhenItsCentreIsInside() {
        assertTrue(MosaicBlockPolicy.blockTouchesCircle(
                2, 2, 10f, 10f, 5f, 4f));
        assertTrue(MosaicBlockPolicy.blockTouchesCircle(
                2, 1, 10f, 10f, 5f, 4f));
        assertFalse(MosaicBlockPolicy.blockTouchesCircle(
                0, 0, 10f, 10f, 5f, 4f));
        assertFalse(MosaicBlockPolicy.blockTouchesCircle(
                1, 1, 10f, 10f, 5f, 4f));
    }

    @Test
    public void cacheKeyIsStableAndDistinguishesNegativeBlocks() {
        assertEquals(MosaicBlockPolicy.cacheKey(3, 7),
                MosaicBlockPolicy.cacheKey(3, 7));
        assertNotEquals(MosaicBlockPolicy.cacheKey(3, 7),
                MosaicBlockPolicy.cacheKey(7, 3));
        assertNotEquals(MosaicBlockPolicy.cacheKey(-1, 2),
                MosaicBlockPolicy.cacheKey(1, 2));
    }
}
