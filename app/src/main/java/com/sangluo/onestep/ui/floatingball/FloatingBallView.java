package com.sangluo.onestep.ui.floatingball;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/** Collapsed floating ball: a circular shortcut that forwards every touch to the controller. */
public final class FloatingBallView extends View {
    public interface TouchListener {
        boolean onBallTouch(FloatingBallView view, MotionEvent event);
    }

    private static final int BACKGROUND_COLOR = 0xdd1b1f24;
    private static final int STROKE_COLOR = 0x66ffffff;
    private static final int BADGE_COLOR = 0xff0a84ff;
    private static final int UNREAD_BADGE_COLOR = 0xffff3b30;

    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unreadBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF unreadBadgeRect = new RectF();
    private Drawable icon;
    private String contentLabel = "";
    private int badgeCount;
    private int unreadBadgeCount;
    private TouchListener touchListener;

    public FloatingBallView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setElevation(dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(BACKGROUND_COLOR);
        background.setStroke((int) dp(1.5f), STROKE_COLOR);
        setBackground(background);

        badgePaint.setColor(BADGE_COLOR);
        badgeTextPaint.setColor(Color.WHITE);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setTextSize(sp(10));
        badgeTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        unreadBadgePaint.setColor(UNREAD_BADGE_COLOR);
        setContentDescription("OneStep 悬浮球");
    }

    public void setBallTouchListener(TouchListener listener) {
        touchListener = listener;
    }

    public void setIcon(Drawable drawable) {
        icon = drawable;
        invalidate();
    }

    public void setContentLabel(String label) {
        contentLabel = label == null ? "" : label;
        setContentDescription(TextUtils.isEmpty(contentLabel)
                ? "OneStep 悬浮球" : "OneStep 悬浮球，" + contentLabel);
        invalidate();
    }

    public void setBadgeCount(int count) {
        int sanitized = Math.max(0, count);
        if (sanitized == badgeCount) {
            return;
        }
        badgeCount = sanitized;
        invalidate();
    }

    /** Red unread-notification pill in the top-left corner, visible while collapsed. */
    public void setUnreadBadgeCount(int count) {
        int sanitized = Math.max(0, count);
        if (sanitized == unreadBadgeCount) {
            return;
        }
        unreadBadgeCount = sanitized;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        if (icon != null) {
            int iconSize = Math.round(size * 0.52f);
            icon.setBounds(Math.round(centerX - iconSize / 2f),
                    Math.round(centerY - iconSize / 2f),
                    Math.round(centerX + iconSize / 2f),
                    Math.round(centerY + iconSize / 2f));
            icon.draw(canvas);
        }
        if (badgeCount > 0) {
            drawBadge(canvas);
        }
        if (unreadBadgeCount > 0) {
            drawUnreadBadge(canvas);
        }
    }

    private void drawUnreadBadge(Canvas canvas) {
        String text = unreadBadgeCount > 99 ? "99+" : String.valueOf(unreadBadgeCount);
        float height = dp(18);
        float width = Math.max(height, badgeTextPaint.measureText(text) + dp(9));
        float left = dp(1);
        float top = dp(1);
        unreadBadgeRect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(unreadBadgeRect, height / 2f, height / 2f, unreadBadgePaint);
        Paint.FontMetrics metrics = badgeTextPaint.getFontMetrics();
        float baseline = top + height / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, left + width / 2f, baseline, badgeTextPaint);
    }

    private void drawBadge(Canvas canvas) {
        float radius = dp(9);
        float centerX = getWidth() - radius - dp(1);
        float centerY = radius + dp(1);
        canvas.drawCircle(centerX, centerY, radius, badgePaint);
        String text = badgeCount > 9 ? "9+" : String.valueOf(badgeCount);
        Paint.FontMetrics metrics = badgeTextPaint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, centerX, baseline, badgeTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (touchListener != null && touchListener.onBallTouch(this, event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}
