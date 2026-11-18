package com.sangluo.onestep.ui.floatingball;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FloatingBallGesturePolicyTest {
    @Test
    public void longPressSurvivesTinyDriftOnly() {
        assertTrue(FloatingBallGesturePolicy.shouldStartLongPress(0f, 0f, 8f));
        assertTrue(FloatingBallGesturePolicy.shouldStartLongPress(5f, 6f, 8f));
        assertTrue(FloatingBallGesturePolicy.shouldStartLongPress(8f, 0f, 8f));
        assertFalse(FloatingBallGesturePolicy.shouldStartLongPress(9f, 0f, 8f));
        assertFalse(FloatingBallGesturePolicy.shouldStartLongPress(0f, -12f, 8f));
    }

    @Test
    public void switchDwellNeedsAStableThreeHundredMilliseconds() {
        long highlightedAt = 1_000L;

        assertFalse(FloatingBallGesturePolicy.isSwitchDwellElapsed(highlightedAt, 1_299L));
        assertTrue(FloatingBallGesturePolicy.isSwitchDwellElapsed(highlightedAt, 1_300L));
        assertTrue(FloatingBallGesturePolicy.isSwitchDwellElapsed(highlightedAt, 1_900L));
        assertFalse(FloatingBallGesturePolicy.isSwitchDwellElapsed(0L, 5_000L));
    }

    @Test
    public void relightRequiresMovingPastTheRelightSlop() {
        assertFalse(FloatingBallGesturePolicy.canRelight(6f, 6f, 12f));
        assertFalse(FloatingBallGesturePolicy.canRelight(12f, 0f, 12f));
        assertTrue(FloatingBallGesturePolicy.canRelight(13f, 0f, 12f));
        assertTrue(FloatingBallGesturePolicy.canRelight(0f, -20f, 12f));
    }

    @Test
    public void lockedExpansionCollapsesAfterIdleTimeout() {
        long lockedAt = 4_000L;

        assertFalse(FloatingBallGesturePolicy.isLockExpired(lockedAt, 5_499L));
        assertTrue(FloatingBallGesturePolicy.isLockExpired(lockedAt, 5_500L));
        assertFalse(FloatingBallGesturePolicy.isLockExpired(0L, 90_000L));
    }

    @Test
    public void timeoutsMatchTheAgreedInteractionConstants() {
        assertEquals(500L, FloatingBallGesturePolicy.LONG_PRESS_TIMEOUT_MS);
        assertEquals(300L, FloatingBallGesturePolicy.SWITCH_DWELL_MS);
        assertEquals(1500L, FloatingBallGesturePolicy.LOCKED_AUTO_COLLAPSE_MS);
        assertEquals(8f, FloatingBallGesturePolicy.TOUCH_SLOP_DP, 0.001f);
        assertEquals(12f, FloatingBallGesturePolicy.RELIGHT_SLOP_DP, 0.001f);
    }
}
