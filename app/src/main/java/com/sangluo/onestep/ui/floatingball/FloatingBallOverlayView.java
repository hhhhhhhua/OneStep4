package com.sangluo.onestep.ui.floatingball;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
    private final RectF labelRect = new RectF();

    private List<FloatingBallItem> items = Collections.emptyList();
    private String layerLabel = "";
    private float centerX;
    private float centerY;
    private float radiusPx;
    private float itemRadiusPx;
    private int highlighted = -1;
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
        invalidate();
    }

    public void setHighlight(int index) {
        if (highlighted == index) {
            return;
        }
        highlighted = index;
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

    private void drawCaption(Canvas canvas, float alpha) {
        FloatingBallItem highlightedItem = highlighted >= 0 && highlighted < items.size()
                ? items.get(highlighted) : null;
        String text = highlightedItem != null && !TextUtils.isEmpty(highlightedItem.label())
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
