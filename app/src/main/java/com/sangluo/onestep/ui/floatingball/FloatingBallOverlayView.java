package com.sangluo.onestep.ui.floatingball;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Collections;
import java.util.List;

/**
 * Full-screen, non-interactive overlay that paints the expanded ring. It only takes
 * touch events while the ring is in the locked (click-to-select) state.
 */
public final class FloatingBallOverlayView extends View {
    public interface TouchListener {
        boolean onOverlayTouch(FloatingBallOverlayView view, MotionEvent event);
    }

    private static final int SCRIM_COLOR = 0x66000000;
    private static final int ITEM_BACKGROUND = 0xe61f2328;
    private static final int ADD_BACKGROUND = 0xe62f7df6;
    private static final int SCREEN_OCR_BACKGROUND_LIGHT = 0xff4cc9b8;
    private static final int SCREEN_OCR_BACKGROUND_DARK = 0xff1c7a70;
    private static final int KILL_BACKGROUND_LIGHT = 0xffff5f57;
    private static final int KILL_BACKGROUND_DARK = 0xffb3261e;
    private static final int SCREENSHOT_BACKGROUND_LIGHT = 0xff5b9bff;
    private static final int SCREENSHOT_BACKGROUND_DARK = 0xff2557d6;
    private static final int NOTIFICATION_BACKGROUND_LIGHT = 0xffe8a33d;
    private static final int NOTIFICATION_BACKGROUND_DARK = 0xffb57515;
    private static final int BACKGROUND_ACTION_LIGHT = 0xff8b7cf6;
    private static final int BACKGROUND_ACTION_DARK = 0xff5b49c9;
    private static final int ITEM_STROKE = 0x55ffffff;
    private static final int HIGHLIGHT_STROKE = 0xffffffff;
    private static final int ACTIVE_MAIN_STROKE = 0xff34c759;
    private static final int OPENED_DOT_COLOR = 0xff0a84ff;
    private static final long EXPAND_DURATION_MS = 160L;
    private static final long COLLAPSE_DURATION_MS = 120L;

    private final Paint scrimPaint = new Paint();
    private final Paint itemBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint itemStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ocrBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ocrGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint killBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint killGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint screenshotBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint screenshotGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notificationBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notificationGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundActionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundActionGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF labelRect = new RectF();
    private final RectF screenshotBodyRect = new RectF();
    private final Path bellPath = new Path();

    private List<FloatingBallItem> items = Collections.emptyList();
    private String layerLabel = "";
    private float centerX;
    private float centerY;
    private float radiusPx;
    private float itemRadiusPx;
    private int highlighted = -1;
    private boolean ocrHighlighted;
    private boolean killHighlighted;
    private boolean screenshotHighlighted;
    private boolean notificationHighlighted;
    private boolean backgroundActionHighlighted;
    private float progress;
    private boolean interactive;
    private ValueAnimator animator;
    private TouchListener touchListener;

    public FloatingBallOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setBackgroundColor(Color.TRANSPARENT);

        scrimPaint.setColor(SCRIM_COLOR);
        itemBackgroundPaint.setStyle(Paint.Style.FILL);
        itemStrokePaint.setStyle(Paint.Style.STROKE);
        itemStrokePaint.setStrokeWidth(dp(1));
        itemStrokePaint.setColor(ITEM_STROKE);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(dp(2));
        highlightPaint.setColor(HIGHLIGHT_STROKE);
        accentPaint.setStyle(Paint.Style.STROKE);
        accentPaint.setStrokeWidth(dp(2));
        accentPaint.setColor(ACTIVE_MAIN_STROKE);
        glyphPaint.setColor(Color.WHITE);
        glyphPaint.setTextAlign(Paint.Align.CENTER);
        glyphPaint.setTextSize(dp(18));
        glyphPaint.setTypeface(Typeface.DEFAULT_BOLD);
        labelPaint.setColor(0xffffffff);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(13));
        labelPaint.setShadowLayer(dp(2), 0, dp(1), 0xaa000000);
        labelBackgroundPaint.setColor(0xcc000000);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(OPENED_DOT_COLOR);
        ocrGlyphPaint.setStyle(Paint.Style.STROKE);
        ocrGlyphPaint.setColor(Color.WHITE);
        ocrGlyphPaint.setStrokeWidth(dp(2.2f));
        ocrGlyphPaint.setStrokeCap(Paint.Cap.ROUND);
        ocrGlyphPaint.setStrokeJoin(Paint.Join.ROUND);
        killGlyphPaint.setStyle(Paint.Style.STROKE);
        killGlyphPaint.setColor(Color.WHITE);
        killGlyphPaint.setStrokeWidth(dp(2.2f));
        killGlyphPaint.setStrokeCap(Paint.Cap.ROUND);
        killGlyphPaint.setStrokeJoin(Paint.Join.ROUND);
        screenshotGlyphPaint.setStyle(Paint.Style.STROKE);
        screenshotGlyphPaint.setColor(Color.WHITE);
        screenshotGlyphPaint.setStrokeWidth(dp(1.8f));
        screenshotGlyphPaint.setStrokeCap(Paint.Cap.ROUND);
        screenshotGlyphPaint.setStrokeJoin(Paint.Join.ROUND);
        notificationGlyphPaint.setStyle(Paint.Style.STROKE);
        notificationGlyphPaint.setColor(Color.WHITE);
        notificationGlyphPaint.setStrokeWidth(dp(1.9f));
        notificationGlyphPaint.setStrokeCap(Paint.Cap.ROUND);
        notificationGlyphPaint.setStrokeJoin(Paint.Join.ROUND);
        backgroundActionGlyphPaint.setStyle(Paint.Style.STROKE);
        backgroundActionGlyphPaint.setColor(Color.WHITE);
        backgroundActionGlyphPaint.setStrokeWidth(dp(1.9f));
        backgroundActionGlyphPaint.setStrokeCap(Paint.Cap.ROUND);
        backgroundActionGlyphPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setTouchListener(TouchListener listener) {
        touchListener = listener;
    }

    public void setRing(List<FloatingBallItem> ringItems, String label,
                        float centerX, float centerY, float radiusPx, float itemRadiusPx) {
        this.items = ringItems == null ? Collections.<FloatingBallItem>emptyList() : ringItems;
        this.layerLabel = label == null ? "" : label;
        this.centerX = centerX;
        this.centerY = centerY;
        this.radiusPx = radiusPx;
        this.itemRadiusPx = itemRadiusPx;
        this.highlighted = -1;
        this.ocrHighlighted = false;
        this.killHighlighted = false;
        this.screenshotHighlighted = false;
        this.notificationHighlighted = false;
        this.backgroundActionHighlighted = false;
        invalidate();
    }

    public void setHighlight(int index) {
        if (highlighted == index) {
            return;
        }
        highlighted = index;
        invalidate();
    }

    public void setOcrHighlighted(boolean highlighted) {
        if (ocrHighlighted == highlighted) {
            return;
        }
        ocrHighlighted = highlighted;
        invalidate();
    }

    public void setKillHighlighted(boolean highlighted) {
        if (killHighlighted == highlighted) {
            return;
        }
        killHighlighted = highlighted;
        invalidate();
    }

    public void setScreenshotHighlighted(boolean highlighted) {
        if (screenshotHighlighted == highlighted) {
            return;
        }
        screenshotHighlighted = highlighted;
        invalidate();
    }

    public void setNotificationHighlighted(boolean highlighted) {
        if (notificationHighlighted == highlighted) {
            return;
        }
        notificationHighlighted = highlighted;
        invalidate();
    }

    public void setBackgroundActionHighlighted(boolean highlighted) {
        if (backgroundActionHighlighted == highlighted) {
            return;
        }
        backgroundActionHighlighted = highlighted;
        invalidate();
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setClickable(interactive);
        setFocusable(interactive);
    }

    public void playExpand() {
        cancelAnimation();
        setVisibility(VISIBLE);
        animateProgress(1f, EXPAND_DURATION_MS, null);
    }

    public void playCollapse() {
        cancelAnimation();
        animateProgress(0f, COLLAPSE_DURATION_MS, () -> {
            if (progress <= 0f) {
                setVisibility(GONE);
                items = Collections.emptyList();
                highlighted = -1;
                ocrHighlighted = false;
                killHighlighted = false;
                screenshotHighlighted = false;
                notificationHighlighted = false;
                backgroundActionHighlighted = false;
                invalidate();
            }
        });
    }

    private void animateProgress(float target, long duration, Runnable onEnd) {
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        if (onEnd != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!cancelled) {
                        onEnd.run();
                    }
                }
            });
        }
        animator.start();
    }

    private void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (items.isEmpty() || progress <= 0.01f) {
            return;
        }
        float eased = progress;
        scrimPaint.setAlpha((int) (0x66 * eased));
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

        float radius = radiusPx * (0.6f + 0.4f * eased);
        float[] centers = FloatingBallLayoutPolicy.computeCenters(
                centerX, centerY, radius, items.size());

        int layer = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(),
                (int) (255 * eased));
        for (int index = 0; index < items.size(); index++) {
            drawItem(canvas, items.get(index), index,
                    centers[index * 2], centers[index * 2 + 1]);
        }
        if (centers.length >= 2) {
            drawScreenOcr(canvas, centers[0], centers[1]);
            drawKill(canvas, centers[0], centers[1]);
            drawScreenshot(canvas, centers[0], centers[1]);
            drawNotification(canvas, centers[0], centers[1]);
            drawBackgroundAction(canvas, centers[0], centers[1]);
        }
        canvas.restoreToCount(layer);

        drawCaption(canvas, eased);
    }

    private void drawItem(Canvas canvas, FloatingBallItem item, int index,
                          float cx, float cy) {
        boolean highlightedItem = index == highlighted;
        float baseRadius = item.kind == FloatingBallItem.Kind.SWITCH
                ? itemRadiusPx * 0.82f : itemRadiusPx;
        float drawRadius = highlightedItem ? baseRadius * 1.12f : baseRadius;

        itemBackgroundPaint.setColor(item.kind == FloatingBallItem.Kind.ADD
                ? ADD_BACKGROUND : ITEM_BACKGROUND);
        canvas.drawCircle(cx, cy, drawRadius, itemBackgroundPaint);
        canvas.drawCircle(cx, cy, drawRadius, itemStrokePaint);
        if (highlightedItem) {
            canvas.drawCircle(cx, cy, drawRadius + dp(3), highlightPaint);
        } else if (item.kind == FloatingBallItem.Kind.OPENED_APP && item.activeMain) {
            canvas.drawCircle(cx, cy, drawRadius, accentPaint);
        }

        switch (item.kind) {
            case SWITCH:
                canvas.drawText("⇄", cx, textBaseline(cy), glyphPaint);
                break;
            case ADD:
                canvas.drawText("+", cx, textBaseline(cy), glyphPaint);
                break;
            default:
                drawIcon(canvas, item.app == null ? null : item.app.icon, cx, cy, drawRadius);
                break;
        }
        if (item.kind == FloatingBallItem.Kind.PINNED_APP && item.alreadyOpen) {
            canvas.drawCircle(cx + drawRadius * 0.62f, cy - drawRadius * 0.62f,
                    dp(3.5f), dotPaint);
        }
    }

    private void drawIcon(Canvas canvas, Drawable icon, float cx, float cy, float radius) {
        if (icon == null) {
            return;
        }
        int size = Math.round(radius * 1.16f);
        icon.setBounds(Math.round(cx - size / 2f), Math.round(cy - size / 2f),
                Math.round(cx + size / 2f), Math.round(cy + size / 2f));
        icon.draw(canvas);
    }

    /**
     * 识屏 control: always drawn on the switch's horizontal line, to its right, in
     * both layers, so it never takes a ring app slot.
     */
    private void drawScreenOcr(Canvas canvas, float switchCx, float switchCy) {
        float cx = FloatingBallLayoutPolicy.ocrCenterX(switchCx, itemRadiusPx);
        float cy = switchCy;
        float baseRadius = itemRadiusPx * 0.86f;
        float drawRadius = ocrHighlighted ? baseRadius * 1.12f : baseRadius;

        ocrBackgroundPaint.setShader(new LinearGradient(
                cx - drawRadius, cy - drawRadius, cx + drawRadius, cy + drawRadius,
                SCREEN_OCR_BACKGROUND_LIGHT, SCREEN_OCR_BACKGROUND_DARK,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, drawRadius, ocrBackgroundPaint);
        ocrBackgroundPaint.setShader(null);
        canvas.drawCircle(cx, cy, drawRadius, itemStrokePaint);
        if (ocrHighlighted) {
            canvas.drawCircle(cx, cy, drawRadius + dp(3), highlightPaint);
        }
        drawOcrGlyph(canvas, cx, cy, drawRadius);
    }

    /**
     * 杀死应用 control: the 识屏 control's mirror on the switch's left, in both layers,
     * so it never takes a ring app slot.
     */
    private void drawKill(Canvas canvas, float switchCx, float switchCy) {
        float cx = FloatingBallLayoutPolicy.killCenterX(switchCx, itemRadiusPx);
        float cy = switchCy;
        float baseRadius = itemRadiusPx * 0.86f;
        float drawRadius = killHighlighted ? baseRadius * 1.12f : baseRadius;

        killBackgroundPaint.setShader(new LinearGradient(
                cx - drawRadius, cy - drawRadius, cx + drawRadius, cy + drawRadius,
                KILL_BACKGROUND_LIGHT, KILL_BACKGROUND_DARK,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, drawRadius, killBackgroundPaint);
        killBackgroundPaint.setShader(null);
        canvas.drawCircle(cx, cy, drawRadius, itemStrokePaint);
        if (killHighlighted) {
            canvas.drawCircle(cx, cy, drawRadius + dp(3), highlightPaint);
        }
        drawKillGlyph(canvas, cx, cy, drawRadius);
    }

    /**
     * 截图 control: diagonally above the 识屏 control, in both layers, so it never takes a
     * ring app slot.
     */
    private void drawScreenshot(Canvas canvas, float switchCx, float switchCy) {
        float cx = FloatingBallLayoutPolicy.screenshotCenterX(switchCx, itemRadiusPx);
        float cy = FloatingBallLayoutPolicy.screenshotCenterY(
                switchCy, itemRadiusPx, getHeight());
        float baseRadius = itemRadiusPx * 0.86f;
        float drawRadius = screenshotHighlighted ? baseRadius * 1.12f : baseRadius;

        screenshotBackgroundPaint.setShader(new LinearGradient(
                cx - drawRadius, cy - drawRadius, cx + drawRadius, cy + drawRadius,
                SCREENSHOT_BACKGROUND_LIGHT, SCREENSHOT_BACKGROUND_DARK,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, drawRadius, screenshotBackgroundPaint);
        screenshotBackgroundPaint.setShader(null);
        canvas.drawCircle(cx, cy, drawRadius, itemStrokePaint);
        if (screenshotHighlighted) {
            canvas.drawCircle(cx, cy, drawRadius + dp(3), highlightPaint);
        }
        drawScreenshotGlyph(canvas, cx, cy, drawRadius);
    }

    /**
     * 放入后台 control: above the 杀死应用 control, on the left, in both layers, so it
     * never takes a ring app slot.
     */
    private void drawBackgroundAction(Canvas canvas, float switchCx, float switchCy) {
        float cx = FloatingBallLayoutPolicy.backgroundCenterX(switchCx, itemRadiusPx);
        float cy = FloatingBallLayoutPolicy.backgroundCenterY(
                switchCy, itemRadiusPx, getHeight());
        float baseRadius = itemRadiusPx * 0.86f;
        float drawRadius = backgroundActionHighlighted ? baseRadius * 1.12f : baseRadius;

        backgroundActionBackgroundPaint.setShader(new LinearGradient(
                cx - drawRadius, cy - drawRadius, cx + drawRadius, cy + drawRadius,
                BACKGROUND_ACTION_LIGHT, BACKGROUND_ACTION_DARK, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, drawRadius, backgroundActionBackgroundPaint);
        backgroundActionBackgroundPaint.setShader(null);
        canvas.drawCircle(cx, cy, drawRadius, itemStrokePaint);
        if (backgroundActionHighlighted) {
            canvas.drawCircle(cx, cy, drawRadius + dp(3), highlightPaint);
        }
        drawBackgroundActionGlyph(canvas, cx, cy, drawRadius);
    }

    /** Tray with a downward arrow: minimize the app into the background. */
    private void drawBackgroundActionGlyph(Canvas canvas, float cx, float cy, float radius) {
        float halfWidth = radius * 0.42f;
        float trayY = cy + radius * 0.32f;
        canvas.drawLine(cx - halfWidth, trayY - radius * 0.20f,
                cx - halfWidth, trayY, backgroundActionGlyphPaint);
        canvas.drawLine(cx - halfWidth, trayY, cx + halfWidth, trayY,
                backgroundActionGlyphPaint);
        canvas.drawLine(cx + halfWidth, trayY, cx + halfWidth, trayY - radius * 0.20f,
                backgroundActionGlyphPaint);
        canvas.drawLine(cx, cy - radius * 0.46f, cx, cy + radius * 0.16f,
                backgroundActionGlyphPaint);
        canvas.drawLine(cx - radius * 0.20f, cy - radius * 0.06f,
                cx, cy + radius * 0.16f, backgroundActionGlyphPaint);
        canvas.drawLine(cx + radius * 0.20f, cy - radius * 0.06f,
                cx, cy + radius * 0.16f, backgroundActionGlyphPaint);
    }

    /**
     * 通知 control: below the 识屏 control, on the right, in both layers, so it never
     * takes a ring app slot.
     */
    private void drawNotification(Canvas canvas, float switchCx, float switchCy) {
        float cx = FloatingBallLayoutPolicy.notificationCenterX(switchCx, itemRadiusPx);
        float cy = FloatingBallLayoutPolicy.notificationCenterY(
                switchCy, itemRadiusPx, getHeight());
        float baseRadius = itemRadiusPx * 0.86f;
        float drawRadius = notificationHighlighted ? baseRadius * 1.12f : baseRadius;

        notificationBackgroundPaint.setShader(new LinearGradient(
                cx - drawRadius, cy - drawRadius, cx + drawRadius, cy + drawRadius,
                NOTIFICATION_BACKGROUND_LIGHT, NOTIFICATION_BACKGROUND_DARK,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, drawRadius, notificationBackgroundPaint);
        notificationBackgroundPaint.setShader(null);
        canvas.drawCircle(cx, cy, drawRadius, itemStrokePaint);
        if (notificationHighlighted) {
            canvas.drawCircle(cx, cy, drawRadius + dp(3), highlightPaint);
        }
        drawNotificationGlyph(canvas, cx, cy, drawRadius);
    }

    /** Bell glyph: domed body, flared rim and a clapper. */
    private void drawNotificationGlyph(Canvas canvas, float cx, float cy, float radius) {
        bellPath.reset();
        bellPath.moveTo(cx - radius * 0.44f, cy + radius * 0.26f);
        bellPath.lineTo(cx - radius * 0.29f, cy + radius * 0.10f);
        bellPath.lineTo(cx - radius * 0.29f, cy - radius * 0.16f);
        bellPath.quadTo(cx - radius * 0.26f, cy - radius * 0.44f,
                cx, cy - radius * 0.46f);
        bellPath.quadTo(cx + radius * 0.26f, cy - radius * 0.44f,
                cx + radius * 0.29f, cy - radius * 0.16f);
        bellPath.lineTo(cx + radius * 0.29f, cy + radius * 0.10f);
        bellPath.lineTo(cx + radius * 0.44f, cy + radius * 0.26f);
        bellPath.close();
        canvas.drawPath(bellPath, notificationGlyphPaint);
        canvas.drawCircle(cx, cy + radius * 0.37f, radius * 0.07f,
                notificationGlyphPaint);
    }

    /** Camera glyph: rounded body, top bump and a lens. */
    private void drawScreenshotGlyph(Canvas canvas, float cx, float cy, float radius) {
        float halfWidth = radius * 0.46f;
        float halfHeight = radius * 0.34f;
        screenshotBodyRect.set(cx - halfWidth, cy - halfHeight,
                cx + halfWidth, cy + halfHeight);
        canvas.drawRoundRect(screenshotBodyRect, radius * 0.16f, radius * 0.16f,
                screenshotGlyphPaint);
        canvas.drawRoundRect(cx - radius * 0.18f, cy - radius * 0.52f,
                cx + radius * 0.18f, cy - radius * 0.28f,
                radius * 0.08f, radius * 0.08f, screenshotGlyphPaint);
        canvas.drawCircle(cx, cy, radius * 0.21f, screenshotGlyphPaint);
    }

    /** Cross glyph: kill/close. */
    private void drawKillGlyph(Canvas canvas, float cx, float cy, float radius) {
        float half = radius * 0.40f;
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, killGlyphPaint);
        canvas.drawLine(cx - half, cy + half, cx + half, cy - half, killGlyphPaint);
    }

    /** View-finder frame with a lens in the middle: screen recognition. */
    private void drawOcrGlyph(Canvas canvas, float cx, float cy, float radius) {
        float half = radius * 0.52f;
        float arm = radius * 0.30f;
        float left = cx - half;
        float right = cx + half;
        float top = cy - half;
        float bottom = cy + half;

        canvas.drawLine(left, top + arm, left, top, ocrGlyphPaint);
        canvas.drawLine(left, top, left + arm, top, ocrGlyphPaint);
        canvas.drawLine(right - arm, top, right, top, ocrGlyphPaint);
        canvas.drawLine(right, top, right, top + arm, ocrGlyphPaint);
        canvas.drawLine(left, bottom - arm, left, bottom, ocrGlyphPaint);
        canvas.drawLine(left, bottom, left + arm, bottom, ocrGlyphPaint);
        canvas.drawLine(right - arm, bottom, right, bottom, ocrGlyphPaint);
        canvas.drawLine(right, bottom, right, bottom - arm, ocrGlyphPaint);
        canvas.drawCircle(cx, cy, radius * 0.25f, ocrGlyphPaint);
    }

    private void drawCaption(Canvas canvas, float alpha) {
        FloatingBallItem highlightedItem = highlighted >= 0 && highlighted < items.size()
                ? items.get(highlighted) : null;
        String text = screenshotHighlighted ? "截图"
                : killHighlighted ? "杀死应用"
                : ocrHighlighted ? "识屏"
                : notificationHighlighted ? "通知"
                : backgroundActionHighlighted ? "放入后台"
                : highlightedItem != null && !TextUtils.isEmpty(highlightedItem.label())
                        ? highlightedItem.label() : layerLabel;
        if (TextUtils.isEmpty(text)) {
            return;
        }
        float paddingH = dp(12);
        float paddingV = dp(6);
        float textWidth = labelPaint.measureText(text);
        Paint.FontMetrics metrics = labelPaint.getFontMetrics();
        float textHeight = metrics.descent - metrics.ascent;
        float cy = getHeight() - dp(48);
        labelRect.set(centerX - textWidth / 2f - paddingH, cy - textHeight / 2f - paddingV,
                centerX + textWidth / 2f + paddingH, cy + textHeight / 2f + paddingV);
        labelBackgroundPaint.setAlpha((int) (0xcc * alpha));
        canvas.drawRoundRect(labelRect, dp(10), dp(10), labelBackgroundPaint);
        labelPaint.setAlpha((int) (255 * alpha));
        canvas.drawText(text, centerX, cy - (metrics.ascent + metrics.descent) / 2f, labelPaint);
    }

    private float textBaseline(float cy) {
        Paint.FontMetrics metrics = glyphPaint.getFontMetrics();
        return cy - (metrics.ascent + metrics.descent) / 2f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (interactive && touchListener != null) {
            return touchListener.onOverlayTouch(this, event);
        }
        return false;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}
