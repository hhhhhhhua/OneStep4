package com.sangluo.onestep.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Visual feedback for the edge-swipe-back gesture: a rounded pill that grows along
 * the touched main-pane edge together with an inward chevron, tracking the finger
 * height and the horizontal drag progress.
 *
 * It renders on its own Z-order-on-top surface because regular window views sit
 * below the main pane SurfaceView (same technique as the image drag preview); the
 * view never consumes touches so the underlying stream is untouched.
 */
@SuppressLint("ViewConstructor")
public final class EdgeBackIndicatorView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "OneStep40";
    private static final float CORNER_RADIUS_DP = 3f;
    private static final float PILL_WIDTH_DP = 7f;
    private static final float EDGE_INSET_DP = 7f;
    private static final float ARROW_SIZE_DP = 15f;
    private static final float MAX_LENGTH_RATIO = 0.5f;
    // Beyond this fraction of the full-scale bar the tips render red; releasing
    // there kills the hosted app instead of sending back.
    private static final float RED_PORTION = 0.75f;

    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    private final RectF pillRect = new RectF();
    private final float density;

    private boolean drawLogged;
    private boolean released;

    private float progress;
    private boolean fromLeft = true;
    private float anchorY = -1f;
    private float edgeX;
    private int overlayWidth;
    private int overlayHeight;

    public EdgeBackIndicatorView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setZOrderOnTop(true);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setLongClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        getHolder().addCallback(this);
        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(0xf0ffffff);
        redPaint.setStyle(Paint.Style.FILL);
        redPaint.setColor(0xf0ff4d4f);
        pillStrokePaint.setStyle(Paint.Style.STROKE);
        pillStrokePaint.setStrokeWidth(1.5f * density);
        pillStrokePaint.setColor(0x73000000);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(0x40000000);
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setColor(0xff3b3b3b);
        arrowOutlinePaint.setStyle(Paint.Style.STROKE);
        arrowOutlinePaint.setStrokeWidth(3f * density);
        arrowOutlinePaint.setStrokeJoin(Paint.Join.ROUND);
        arrowOutlinePaint.setColor(0xb3ffffff);
        setVisibility(GONE);
    }

    /**
     * Starts a gesture. The overlay size and the aligned pane edge are passed
     * explicitly because the surface may be created before the view is laid out.
     */
    public void begin(boolean fromLeft, int overlayWidth, int overlayHeight, float edgeX) {
        this.fromLeft = fromLeft;
        this.overlayWidth = overlayWidth;
        this.overlayHeight = overlayHeight;
        this.edgeX = edgeX;
        this.progress = 0f;
        this.anchorY = -1f;
        this.drawLogged = false;
        setVisibility(VISIBLE);
        drawFrame();
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        drawFrame();
    }

    public void setAnchorY(float anchorY) {
        this.anchorY = anchorY;
        drawFrame();
    }

    public void release() {
        released = true;
        getHolder().removeCallback(this);
    }

    /**
     * Posts a fully transparent frame right away so nothing lingers on screen even
     * if destroying the Z-top surface takes an extra compositor frame.
     */
    public void clearNow() {
        progress = 0f;
        drawFrame();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        drawFrame();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        drawFrame();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private synchronized void drawFrame() {
        SurfaceHolder holder = getHolder();
        if (released || getVisibility() != VISIBLE
                || !holder.getSurface().isValid()) {
            return;
        }
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                drawContent(canvas);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "edgeBack: draw frame failed", e);
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void drawContent(Canvas canvas) {
        float width = overlayWidth > 0 ? overlayWidth : getWidth();
        float height = overlayHeight > 0 ? overlayHeight : getHeight();
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        if (progress <= 0f || width <= 0 || height <= 0) {
            return;
        }
        if (!drawLogged) {
            drawLogged = true;
            Log.d(TAG, "edgeBack: indicator frame size=" + width + "x" + height
                    + " edgeX=" + edgeX + " progress=" + progress
                    + " fromLeft=" + fromLeft);
        }
        float pillWidth = PILL_WIDTH_DP * density;
        float radius = CORNER_RADIUS_DP * density;
        float inset = EDGE_INSET_DP * density;
        float centerY = anchorY > 0f ? anchorY : height * 0.5f;
        float halfLength = height * MAX_LENGTH_RATIO * progress * 0.5f;
        float top = Math.max(inset, centerY - halfLength);
        float bottom = Math.min(height - inset, centerY + halfLength);
        float pillLeft = fromLeft ? edgeX + inset : edgeX - inset - pillWidth;
        pillRect.set(pillLeft, top, pillLeft + pillWidth, bottom);
        // Soft glow behind the pill for depth.
        float glowPad = pillWidth * 1.4f;
        canvas.drawRoundRect(
                pillRect.left - glowPad, pillRect.top - glowPad,
                pillRect.right + glowPad, pillRect.bottom + glowPad,
                radius + glowPad, radius + glowPad, glowPaint);
        canvas.drawRoundRect(pillRect, radius, radius, pillPaint);
        canvas.drawRoundRect(pillRect, radius, radius, pillStrokePaint);

        // Paint the tips red once the bar reaches the kill zone (last quarter of
        // the full-scale length); clipping keeps the rounded ends intact.
        float maxHalf = height * MAX_LENGTH_RATIO * 0.5f;
        float redBoundary = maxHalf * RED_PORTION;
        if (centerY - top > redBoundary || bottom - centerY > redBoundary) {
            int save = canvas.save();
            canvas.clipRect(pillRect.left - glowPad, top - glowPad,
                    pillRect.right + glowPad, centerY - redBoundary);
            canvas.drawRoundRect(pillRect, radius, radius, redPaint);
            canvas.drawRoundRect(pillRect, radius, radius, pillStrokePaint);
            canvas.restoreToCount(save);
            save = canvas.save();
            canvas.clipRect(pillRect.left - glowPad, centerY + redBoundary,
                    pillRect.right + glowPad, bottom + glowPad);
            canvas.drawRoundRect(pillRect, radius, radius, redPaint);
            canvas.drawRoundRect(pillRect, radius, radius, pillStrokePaint);
            canvas.restoreToCount(save);
        }

        float arrowSize = ARROW_SIZE_DP * density;
        float gap = inset + pillWidth + 10f * density;
        float arrowCx = fromLeft ? edgeX + gap : edgeX - gap;
        float arrowCy = centerY;
        arrowPath.reset();
        if (fromLeft) {
            arrowPath.moveTo(arrowCx - arrowSize * 0.5f, arrowCy - arrowSize * 0.5f);
            arrowPath.lineTo(arrowCx + arrowSize * 0.5f, arrowCy);
            arrowPath.lineTo(arrowCx - arrowSize * 0.5f, arrowCy + arrowSize * 0.5f);
        } else {
            arrowPath.moveTo(arrowCx + arrowSize * 0.5f, arrowCy - arrowSize * 0.5f);
            arrowPath.lineTo(arrowCx - arrowSize * 0.5f, arrowCy);
            arrowPath.lineTo(arrowCx + arrowSize * 0.5f, arrowCy + arrowSize * 0.5f);
        }
        arrowPath.close();
        canvas.drawPath(arrowPath, arrowOutlinePaint);
        canvas.drawPath(arrowPath, arrowPaint);
    }
}
