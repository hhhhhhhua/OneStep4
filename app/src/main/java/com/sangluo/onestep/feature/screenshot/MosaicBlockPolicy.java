package com.sangluo.onestep.feature.screenshot;

/** Pure block arithmetic for the mosaic brush. */
public final class MosaicBlockPolicy {
    private MosaicBlockPolicy() {
    }

    public static int minBlockIndex(float point, float radius, float blockPx) {
        return (int) Math.floor((point - radius) / blockPx);
    }

    public static int maxBlockIndex(float point, float radius, float blockPx) {
        return (int) Math.floor((point + radius) / blockPx);
    }

    /** True when the block's centre falls inside the brush circle. */
    public static boolean blockTouchesCircle(int blockX, int blockY,
                                             float pointX, float pointY,
                                             float radius, float blockPx) {
        float centerX = (blockX + 0.5f) * blockPx;
        float centerY = (blockY + 0.5f) * blockPx;
        float dx = centerX - pointX;
        float dy = centerY - pointY;
        return dx * dx + dy * dy <= radius * radius;
    }

    /** Stable cache key for a block coordinate, including negative indices. */
    public static long cacheKey(int blockX, int blockY) {
        return (((long) blockX) << 32) ^ (blockY & 0xffffffffL);
    }
}
