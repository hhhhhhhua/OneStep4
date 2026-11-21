package com.sangluo.onestep.feature.screenshot;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One user annotation. All coordinates and widths are stored in editor-view pixels; the
 * exporter scales the canvas instead of mutating the stored values.
 */
public abstract class ScreenshotAnnotation {
    public abstract void draw(Canvas canvas, Bitmap source, float scale);

    /** A run of text anchored by its first baseline. */
    public static final class TextAnnotation extends ScreenshotAnnotation {
        private static final float LINE_SPACING = 1.25f;

        private final float x;
        private final float y;
        private final float textSizePx;
        private final int color;
        private final String text;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TextAnnotation(float x, float y, float textSizePx, int color, String text) {
            this.x = x;
            this.y = y;
            this.textSizePx = Math.max(1f, textSizePx);
            this.color = color;
            this.text = text == null ? "" : text;
            paint.setColor(color);
            paint.setTextSize(this.textSizePx);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setShadowLayer(Math.max(1f, textSizePx * 0.08f), 0f,
                    Math.max(1f, textSizePx * 0.05f), 0xaa000000);
        }

        @Override
        public void draw(Canvas canvas, Bitmap source, float scale) {
            if (text.isEmpty()) {
                return;
            }
            float lineHeight = textSizePx * LINE_SPACING;
            String[] lines = text.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                canvas.drawText(lines[index], x, y + lineHeight * index, paint);
            }
        }
    }

    /** Freehand pen stroke. */
    public static final class DoodleAnnotation extends ScreenshotAnnotation {
        private final List<PointF> points;
        private final float widthPx;
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public DoodleAnnotation(float widthPx, int color) {
            this.points = new ArrayList<>();
            this.widthPx = Math.max(1f, widthPx);
            this.color = color;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(this.widthPx);
            paint.setColor(color);
        }

        public void addPoint(float x, float y) {
            if (points.size() == 1) {
                PointF first = points.get(0);
                path.moveTo(first.x, first.y);
            }
            points.add(new PointF(x, y));
            if (points.size() > 1) {
                path.lineTo(x, y);
            }
        }

        public boolean isEmpty() {
            return points.isEmpty();
        }

        @Override
        public void draw(Canvas canvas, Bitmap source, float scale) {
            if (points.isEmpty()) {
                return;
            }
            Paint.Style previous = paint.getStyle();
            if (points.size() == 1) {
                paint.setStyle(Paint.Style.FILL);
                PointF point = points.get(0);
                canvas.drawCircle(point.x, point.y, widthPx / 2f, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(path, paint);
            }
            paint.setStyle(previous);
        }
    }

    /** Pixel-block mosaic sampled from the frozen screenshot. */
    public static final class MosaicAnnotation extends ScreenshotAnnotation {
        private static final int MAX_SAMPLES_PER_AXIS = 4;

        private final List<PointF> points;
        private final float widthPx;
        private final float blockPx;
        private final Map<Long, Integer> colorCache = new HashMap<>();
        private final Paint paint = new Paint();

        public MosaicAnnotation(float widthPx, float blockPx) {
            this.points = new ArrayList<>();
            this.widthPx = Math.max(1f, widthPx);
            this.blockPx = Math.max(2f, blockPx);
            paint.setStyle(Paint.Style.FILL);
        }

        public void addPoint(float x, float y) {
            points.add(new PointF(x, y));
        }

        public boolean isEmpty() {
            return points.isEmpty();
        }

        @Override
        public void draw(Canvas canvas, Bitmap source, float scale) {
            if (source == null || points.isEmpty()) {
                return;
            }
            float radius = widthPx / 2f;
            for (PointF point : points) {
                int minX = MosaicBlockPolicy.minBlockIndex(point.x, radius, blockPx);
                int maxX = MosaicBlockPolicy.maxBlockIndex(point.x, radius, blockPx);
                int minY = MosaicBlockPolicy.minBlockIndex(point.y, radius, blockPx);
                int maxY = MosaicBlockPolicy.maxBlockIndex(point.y, radius, blockPx);
                for (int by = minY; by <= maxY; by++) {
                    for (int bx = minX; bx <= maxX; bx++) {
                        if (!MosaicBlockPolicy.blockTouchesCircle(
                                bx, by, point.x, point.y, radius, blockPx)) {
                            continue;
                        }
                        long key = MosaicBlockPolicy.cacheKey(bx, by);
                        Integer cached = colorCache.get(key);
                        int color;
                        if (cached != null) {
                            color = cached;
                        } else {
                            color = sampleBlockColor(source, bx, by, scale);
                            colorCache.put(key, color);
                        }
                        paint.setColor(color);
                        canvas.drawRect(bx * blockPx, by * blockPx,
                                (bx + 1) * blockPx, (by + 1) * blockPx, paint);
                    }
                }
            }
        }

        private int sampleBlockColor(Bitmap source, int blockX, int blockY, float scale) {
            int sourceLeft = Math.round(blockX * blockPx * scale);
            int sourceTop = Math.round(blockY * blockPx * scale);
            int sourceRight = Math.round((blockX + 1) * blockPx * scale);
            int sourceBottom = Math.round((blockY + 1) * blockPx * scale);
            sourceLeft = Math.max(0, Math.min(source.getWidth() - 1, sourceLeft));
            sourceTop = Math.max(0, Math.min(source.getHeight() - 1, sourceTop));
            sourceRight = Math.max(sourceLeft + 1, Math.min(source.getWidth(), sourceRight));
            sourceBottom = Math.max(sourceTop + 1, Math.min(source.getHeight(), sourceBottom));

            long red = 0;
            long green = 0;
            long blue = 0;
            long alpha = 0;
            int samples = 0;
            int stepX = Math.max(1, (sourceRight - sourceLeft) / MAX_SAMPLES_PER_AXIS);
            int stepY = Math.max(1, (sourceBottom - sourceTop) / MAX_SAMPLES_PER_AXIS);
            for (int y = sourceTop; y < sourceBottom; y += stepY) {
                for (int x = sourceLeft; x < sourceRight; x += stepX) {
                    int pixel = source.getPixel(x, y);
                    alpha += Color.alpha(pixel);
                    red += Color.red(pixel);
                    green += Color.green(pixel);
                    blue += Color.blue(pixel);
                    samples++;
                }
            }
            if (samples == 0) {
                return Color.TRANSPARENT;
            }
            return Color.argb((int) (alpha / samples), (int) (red / samples),
                    (int) (green / samples), (int) (blue / samples));
        }
    }
}
