package com.sangluo.onestep.ui.floatingball;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FloatingBallLayoutPolicyTest {
    private static final float DELTA = 0.01f;

    @Test
    public void reservesOneSlotForTheLayerSwitchControl() {
        assertEquals(1, FloatingBallLayoutPolicy.slotCount(0));
        assertEquals(9, FloatingBallLayoutPolicy.slotCount(8));
        assertEquals(1, FloatingBallLayoutPolicy.slotCount(-3));
    }

    @Test
    public void switchControlSitsAtTwelveOClock() {
        assertEquals(-90f, FloatingBallLayoutPolicy.angleFor(0, 9), DELTA);
        assertEquals(-50f, FloatingBallLayoutPolicy.angleFor(1, 9), DELTA);
        assertEquals(-90f, FloatingBallLayoutPolicy.angleFor(9, 9), DELTA);
        assertEquals(180f, FloatingBallLayoutPolicy.angleFor(-1, 4), DELTA);
    }

    @Test
    public void centersFollowClockwiseRingOrder() {
        float[] centers = FloatingBallLayoutPolicy.computeCenters(100f, 100f, 50f, 4);

        // 12 o'clock, 3 o'clock, 6 o'clock, 9 o'clock
        assertEquals(100f, centers[0], DELTA);
        assertEquals(50f, centers[1], DELTA);
        assertEquals(150f, centers[2], DELTA);
        assertEquals(100f, centers[3], DELTA);
        assertEquals(100f, centers[4], DELTA);
        assertEquals(150f, centers[5], DELTA);
        assertEquals(50f, centers[6], DELTA);
        assertEquals(100f, centers[7], DELTA);
    }

    @Test
    public void killControlMirrorsScreenOcrAcrossTheSwitch() {
        float switchCenterX = 200f;
        float itemRadius = 20f;

        assertEquals(switchCenterX + itemRadius
                        * FloatingBallLayoutPolicy.OCR_OFFSET_ITEM_RADII,
                FloatingBallLayoutPolicy.ocrCenterX(switchCenterX, itemRadius), DELTA);
        assertEquals(switchCenterX - itemRadius
                        * FloatingBallLayoutPolicy.KILL_OFFSET_ITEM_RADII,
                FloatingBallLayoutPolicy.killCenterX(switchCenterX, itemRadius), DELTA);
    }

    @Test
    public void screenshotControlSitsAboveTheScreenRecognitionControl() {
        float switchCenterX = 200f;
        float switchCenterY = 300f;
        float itemRadius = 20f;

        assertEquals(switchCenterX + itemRadius
                        * FloatingBallLayoutPolicy.SCREENSHOT_OFFSET_ITEM_RADII,
                FloatingBallLayoutPolicy.screenshotCenterX(
                        switchCenterX, itemRadius), DELTA);
        assertEquals(switchCenterY - itemRadius
                        * FloatingBallLayoutPolicy.SCREENSHOT_OFFSET_ITEM_RADII,
                FloatingBallLayoutPolicy.screenshotCenterY(
                        switchCenterY, itemRadius, 800f), DELTA);
    }

    @Test
    public void screenshotControlStaysInsideTheScreen() {
        float itemRadius = 20f;
        float edge = itemRadius * FloatingBallLayoutPolicy.SATELLITE_EDGE_ITEM_RADII;

        assertEquals(edge, FloatingBallLayoutPolicy.screenshotCenterY(
                0f, itemRadius, 800f), DELTA);
        assertEquals(800f - edge, FloatingBallLayoutPolicy.screenshotCenterY(
                900f, itemRadius, 800f), DELTA);
    }

    @Test
    public void backgroundControlSitsAboveTheKillControl() {
        float switchCenterX = 200f;
        float switchCenterY = 300f;
        float itemRadius = 20f;

        assertEquals(switchCenterX - itemRadius
                        * FloatingBallLayoutPolicy.BACKGROUND_OFFSET_ITEM_RADII,
                FloatingBallLayoutPolicy.backgroundCenterX(
                        switchCenterX, itemRadius), DELTA);
        assertEquals(switchCenterY - itemRadius
                        * FloatingBallLayoutPolicy.BACKGROUND_OFFSET_ITEM_RADII,
                FloatingBallLayoutPolicy.backgroundCenterY(
                        switchCenterY, itemRadius, 800f), DELTA);
        assertEquals(FloatingBallLayoutPolicy.killCenterX(switchCenterX, itemRadius),
                FloatingBallLayoutPolicy.backgroundCenterX(
                        switchCenterX, itemRadius), DELTA);
    }

    @Test
    public void backgroundControlStaysInsideTheScreen() {
        float itemRadius = 20f;
        float edge = itemRadius * FloatingBallLayoutPolicy.SATELLITE_EDGE_ITEM_RADII;

        assertEquals(edge, FloatingBallLayoutPolicy.backgroundCenterY(
                0f, itemRadius, 800f), DELTA);
    }

    @Test
    public void notificationControlSitsBelowTheScreenRecognitionControl() {
        float switchCenterX = 200f;
        float switchCenterY = 300f;
        float itemRadius = 20f;

        assertEquals(switchCenterX + itemRadius
                        * FloatingBallLayoutPolicy.NOTIFICATION_OFFSET_ITEM_RADII,
                FloatingBallLayoutPolicy.notificationCenterX(
                        switchCenterX, itemRadius), DELTA);
        assertEquals(switchCenterY + itemRadius
                        * FloatingBallLayoutPolicy.NOTIFICATION_OFFSET_ITEM_RADII,
                FloatingBallLayoutPolicy.notificationCenterY(
                        switchCenterY, itemRadius, 800f), DELTA);
    }

    @Test
    public void notificationControlStaysInsideTheScreen() {
        float itemRadius = 20f;
        float edge = itemRadius * FloatingBallLayoutPolicy.SATELLITE_EDGE_ITEM_RADII;

        assertEquals(itemRadius * FloatingBallLayoutPolicy.NOTIFICATION_OFFSET_ITEM_RADII,
                FloatingBallLayoutPolicy.notificationCenterY(
                        0f, itemRadius, 800f), DELTA);
        assertEquals(800f - edge, FloatingBallLayoutPolicy.notificationCenterY(
                900f, itemRadius, 800f), DELTA);
    }

    @Test
    public void hitTestReturnsNearestSlotInsideTheHitArea() {
        float[] centers = FloatingBallLayoutPolicy.computeCenters(100f, 100f, 100f, 4);

        assertEquals(0, FloatingBallLayoutPolicy.hitTest(
                centers, 100f, 10f, 20f, 24f));
        assertEquals(2, FloatingBallLayoutPolicy.hitTest(
                centers, 108f, 190f, 20f, 24f));
        assertEquals(-1, FloatingBallLayoutPolicy.hitTest(
                centers, 100f, 100f, 20f, 24f));
        assertEquals(-1, FloatingBallLayoutPolicy.hitTest(
                centers, 100f, 260f, 20f, 24f));
    }

    @Test
    public void hitTestPrefersTheClosestOfTwoNearbyItems() {
        float[] centers = FloatingBallLayoutPolicy.computeCenters(0f, 0f, 100f, 8);

        int top = FloatingBallLayoutPolicy.hitTest(centers, 0f, -100f, 20f, 60f);
        int topRight = FloatingBallLayoutPolicy.hitTest(centers, 70f, -70f, 20f, 60f);

        assertEquals(0, top);
        assertEquals(1, topRight);
    }

    @Test
    public void radiusShrinksOnlyWhenSpaceIsTight() {
        assertEquals(FloatingBallLayoutPolicy.MAX_RADIUS_DP,
                FloatingBallLayoutPolicy.resolveRadiusDp(400f), DELTA);
        assertEquals(120f, FloatingBallLayoutPolicy.resolveRadiusDp(140f), DELTA);
        assertEquals(FloatingBallLayoutPolicy.MIN_RADIUS_DP,
                FloatingBallLayoutPolicy.resolveRadiusDp(40f), DELTA);
    }

    @Test
    public void availableRadiusIsMeasuredToTheClosestEdge() {
        assertEquals(100f, FloatingBallLayoutPolicy.availableRadius(
                100f, 300f, 1000f, 800f), DELTA);
        assertEquals(60f, FloatingBallLayoutPolicy.availableRadius(
                940f, 400f, 1000f, 800f), DELTA);
    }

    @Test
    public void ringCenterIsPushedAwayFromTheScreenEdge() {
        assertEquals(170f, FloatingBallLayoutPolicy.clampCenterX(
                60f, 1000f, 150f), DELTA);
        assertEquals(830f, FloatingBallLayoutPolicy.clampCenterX(
                980f, 1000f, 150f), DELTA);
        assertEquals(500f, FloatingBallLayoutPolicy.clampCenterX(
                500f, 1000f, 150f), DELTA);
        assertEquals(150f, FloatingBallLayoutPolicy.clampCenterX(
                10f, 300f, 150f), DELTA);
        assertEquals(170f, FloatingBallLayoutPolicy.clampCenterY(
                10f, 800f, 150f), DELTA);
    }

    @Test
    public void screenOcrControlSitsRightOfTheSwitchOnTheSameLine() {
        assertEquals(143f, FloatingBallLayoutPolicy.ocrCenterX(100f, 20f), DELTA);
        assertEquals(100f, FloatingBallLayoutPolicy.ocrCenterX(100f, 0f), DELTA);
    }

    @Test
    public void detectsWhenTheFingerIsBackOnTheBall() {
        assertTrue(FloatingBallLayoutPolicy.isOverBall(0f, 0f, 28f, 10f, 10f));
        assertFalse(FloatingBallLayoutPolicy.isOverBall(0f, 0f, 28f, 40f, 40f));
    }
}
