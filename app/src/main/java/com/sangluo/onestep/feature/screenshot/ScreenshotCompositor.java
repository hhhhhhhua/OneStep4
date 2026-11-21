package com.sangluo.onestep.feature.screenshot;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

import java.util.List;

/** Composites the frozen screenshot and its annotations into the crop rectangle. */
public final class ScreenshotCompositor {
    private ScreenshotCompositor() {
    }

    /**
     * @param selection crop bounds in editor-view pixels, {@code {left, top, right, bottom}}
     * @param scale     source bitmap pixels per editor-view pixel
     */
    public static Bitmap render(Bitmap source, float[] selection,
                                List<ScreenshotAnnotation> annotations, float scale) {
        if (source == null || selection == null || selection.length < 4) {
            return null;
        }
        int sourceLeft = clamp(Math.round(selection[0] * scale), 0, source.getWidth() - 1);
        int sourceTop = clamp(Math.round(selection[1] * scale), 0, source.getHeight() - 1);
        int sourceRight = clamp(Math.round(selection[2] * scale),
                sourceLeft + 1, source.getWidth());
        int sourceBottom = clamp(Math.round(selection[3] * scale),
                sourceTop + 1, source.getHeight());
        int width = Math.max(1, sourceRight - sourceLeft);
        int height = Math.max(1, sourceBottom - sourceTop);

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawBitmap(source, new Rect(sourceLeft, sourceTop, sourceRight, sourceBottom),
                new Rect(0, 0, width, height), null);

        if (annotations != null && !annotations.isEmpty()) {
            canvas.save();
            canvas.scale(scale, scale);
            canvas.translate(-selection[0], -selection[1]);
            canvas.clipRect(selection[0], selection[1], selection[2], selection[3]);
            for (ScreenshotAnnotation annotation : annotations) {
                if (annotation != null) {
                    annotation.draw(canvas, source, scale);
                }
            }
            canvas.restore();
        }
        return output;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
