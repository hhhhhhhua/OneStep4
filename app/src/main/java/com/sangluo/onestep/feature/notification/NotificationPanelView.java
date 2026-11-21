package com.sangluo.onestep.feature.notification;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.sangluo.onestep.MediaNotificationListenerService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full-screen card that lists the active notifications aggregated by application.
 *
 * <p>It only renders; opening apps, dismissing notifications and requesting notification
 * access are forwarded to the {@link Callbacks} so the floating-ball controller keeps
 * owning the windows and the notification service keeps owning the data.</p>
 */
public final class NotificationPanelView extends FrameLayout {
    public interface Callbacks {
        void onClose();

        void onOpenNotification(MediaNotificationListenerService.NotificationSnapshot notification);

        void onDismissNotification(
                MediaNotificationListenerService.NotificationSnapshot notification);

        void onDismissApp(String packageName);

        void onDismissAll();

        void onRequestAccess();

        boolean hasNotificationAccess();
    }

    private static final int SCRIM_COLOR = 0xb3000000;
    private static final int CARD_COLOR = 0xf21b1f24;
    private static final int CARD_STROKE = 0x33ffffff;
    private static final int DIVIDER_COLOR = 0x1affffff;
    private static final int TEXT_PRIMARY = 0xffffffff;
    private static final int TEXT_SECONDARY = 0xffb9c2cc;
    private static final int TEXT_TERTIARY = 0xff8a94a0;
    private static final int ACCENT_COLOR = 0xff5b9bff;

    private final LinearLayout card;
    private final ScrollView scrollView;
    private final LinearLayout list;
    private final TextView countLabel;
    private final TextView clearAllView;
    private final TextView emptyView;
    private final LinearLayout accessCard;
    private final Rect scrollBounds = new Rect();
    private final Set<String> expandedPackages = new HashSet<>();
    private List<MediaNotificationListenerService.NotificationSnapshot> currentItems =
            new ArrayList<>();
    private boolean currentAccessGranted;
    private Callbacks callbacks;
    private float swipeDownX;
    private float swipeDownY;
    private boolean swipeUpCandidate;
    private boolean swipeUpTracking;
    private boolean downInsideScrollableList;

    public NotificationPanelView(Context context) {
        super(context);
        setBackgroundColor(SCRIM_COLOR);
        setClickable(true);
        setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onClose();
            }
        });

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int cardWidth = Math.min(screenWidth - dp(24), dp(460));
        int cardHeight = Math.min(screenHeight - dp(32), dp(640));

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(CARD_COLOR);
        cardBackground.setCornerRadius(dp(18));
        cardBackground.setStroke(Math.max(1, dp(1)), CARD_STROKE);
        card.setBackground(cardBackground);
        card.setClickable(true);
        card.setOnClickListener(v -> {
            // Swallow taps that land on the card itself.
        });
        addView(card, new FrameLayout.LayoutParams(cardWidth, cardHeight, Gravity.CENTER));

        View dragHandle = new View(context);
        GradientDrawable dragHandleBackground = new GradientDrawable();
        dragHandleBackground.setColor(0x40ffffff);
        dragHandleBackground.setCornerRadius(dp(2));
        dragHandle.setBackground(dragHandleBackground);
        dragHandle.setContentDescription("上滑关闭通知列表");
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dp(8);
        card.addView(dragHandle, handleLp);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(12), dp(12));
        card.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText("通知");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        countLabel = new TextView(context);
        countLabel.setTextColor(TEXT_TERTIARY);
        countLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.leftMargin = dp(8);
        countLp.weight = 1f;
        countLp.gravity = Gravity.CENTER_VERTICAL;
        header.addView(countLabel, countLp);

        clearAllView = new TextView(context);
        clearAllView.setText("全部清除");
        clearAllView.setTextColor(ACCENT_COLOR);
        clearAllView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        clearAllView.setPadding(dp(10), dp(6), dp(10), dp(6));
        clearAllView.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onDismissAll();
            }
        });
        header.addView(clearAllView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView close = new TextView(context);
        close.setText("✕");
        close.setTextColor(TEXT_SECONDARY);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭通知列表");
        close.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onClose();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));

        View divider = new View(context);
        divider.setBackgroundColor(DIVIDER_COLOR);
        card.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f))));

        FrameLayout body = new FrameLayout(context);
        card.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        body.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(12));
        scrollView.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));


        emptyView = new TextView(context);
        emptyView.setText("暂无通知");
        emptyView.setTextColor(TEXT_TERTIARY);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        emptyView.setGravity(Gravity.CENTER);
        body.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        accessCard = buildAccessCard(context);
        body.addView(accessCard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContent(new ArrayList<MediaNotificationListenerService.NotificationSnapshot>(), false);
    }

    public void setCallbacks(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void setContent(
            List<MediaNotificationListenerService.NotificationSnapshot> notifications,
            boolean accessGranted) {
        List<MediaNotificationListenerService.NotificationSnapshot> items = notifications == null
                ? new ArrayList<MediaNotificationListenerService.NotificationSnapshot>()
                : new ArrayList<>(notifications);
        currentItems = items;
        currentAccessGranted = accessGranted;
        int previousScroll = scrollView.getScrollY();
        list.removeAllViews();

        if (!accessGranted) {
            countLabel.setText("");
            clearAllView.setVisibility(GONE);
            scrollView.setVisibility(GONE);
            emptyView.setVisibility(GONE);
            accessCard.setVisibility(VISIBLE);
            return;
        }

        accessCard.setVisibility(GONE);
        int unreadTotal = 0;
        for (MediaNotificationListenerService.NotificationSnapshot item : items) {
            if (item != null) {
                unreadTotal += item.unreadWeight();
            }
        }
        boolean hasClearable = false;
        for (MediaNotificationListenerService.NotificationSnapshot item : items) {
            if (item != null && !item.ongoing) {
                hasClearable = true;
                break;
            }
        }
        countLabel.setText(unreadTotal > 0 ? "共 " + unreadTotal + " 条" : "");
        clearAllView.setVisibility(hasClearable ? VISIBLE : GONE);
        scrollView.setVisibility(VISIBLE);
        emptyView.setVisibility(items.isEmpty() ? VISIBLE : GONE);

        Map<String, List<MediaNotificationListenerService.NotificationSnapshot>> groups =
                new LinkedHashMap<>();
        for (MediaNotificationListenerService.NotificationSnapshot item : items) {
            if (item == null) {
                continue;
            }
            List<MediaNotificationListenerService.NotificationSnapshot> group =
                    groups.get(item.packageName);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(item.packageName, group);
            }
            group.add(item);
        }
        for (Map.Entry<String, List<MediaNotificationListenerService.NotificationSnapshot>> entry
                : groups.entrySet()) {
            String packageName = entry.getKey();
            List<MediaNotificationListenerService.NotificationSnapshot> group = entry.getValue();
            boolean collapsible = group.size() > 1;
            boolean expanded = !collapsible || expandedPackages.contains(packageName);
            list.addView(buildGroupHeader(packageName, group, collapsible, expanded),
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!expanded) {
                continue;
            }
            for (MediaNotificationListenerService.NotificationSnapshot item : group) {
                list.addView(buildRow(item), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        final int restoreScroll = previousScroll;
        scrollView.post(() -> scrollView.scrollTo(0, restoreScroll));
    }

    public void playShow() {
        card.animate().cancel();
        card.setTranslationY(0f);
        card.setAlpha(1f);
        setVisibility(VISIBLE);
        setAlpha(0f);
        ViewPropertyAnimator animator = animate().alpha(1f).setDuration(140L);
        animator.start();
    }

    private void startSwipeCloseAnimation() {
        card.animate()
                .translationY(-card.getHeight())
                .alpha(0f)
                .setDuration(160L)
                .withEndAction(() -> {
                    card.setTranslationY(0f);
                    card.setAlpha(1f);
                    if (callbacks != null) {
                        callbacks.onClose();
                    }
                })
                .start();
    }

    private void resetSwipePosition() {
        card.animate().translationY(0f).alpha(1f).setDuration(140L).start();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                swipeDownX = event.getRawX();
                swipeDownY = event.getRawY();
                swipeUpCandidate = true;
                swipeUpTracking = false;
                downInsideScrollableList = isInsideScrollableList(event.getRawX(),
                        event.getRawY());
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!swipeUpCandidate) {
                    return false;
                }
                float dx = event.getRawX() - swipeDownX;
                float dy = event.getRawY() - swipeDownY;
                if (dy < -dp(8) && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                    // An upward swipe inside a list that still has content below it
                    // belongs to the list; everywhere else it dismisses the panel.
                    if (downInsideScrollableList) {
                        swipeUpCandidate = false;
                        return false;
                    }
                    swipeUpTracking = true;
                    card.setTranslationY(dy);
                    return true;
                }
                if (Math.abs(dx) > dp(8) || dy > dp(8)) {
                    swipeUpCandidate = false;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                swipeUpCandidate = false;
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!swipeUpTracking) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float dy = event.getRawY() - swipeDownY;
                card.setTranslationY(Math.min(0f, dy));
                return true;
            case MotionEvent.ACTION_UP:
                float totalDy = event.getRawY() - swipeDownY;
                if (totalDy <= -dp(56)) {
                    startSwipeCloseAnimation();
                } else {
                    resetSwipePosition();
                }
                swipeUpTracking = false;
                swipeUpCandidate = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                resetSwipePosition();
                swipeUpTracking = false;
                swipeUpCandidate = false;
                return true;
            default:
                return true;
        }
    }

    private boolean isInsideScrollableList(float rawX, float rawY) {
        if (!scrollView.canScrollVertically(1)) {
            return false;
        }
        if (!scrollView.getGlobalVisibleRect(scrollBounds)) {
            return false;
        }
        return rawX >= scrollBounds.left && rawX <= scrollBounds.right
                && rawY >= scrollBounds.top && rawY <= scrollBounds.bottom;
    }

    private LinearLayout buildAccessCard(Context context) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dp(32), dp(24), dp(32), dp(24));

        TextView title = new TextView(context);
        title.setText("未开启通知访问");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        container.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView description = new TextView(context);
        description.setText("授予通知访问权限后，即可在 OneStep 中查看和管理各应用通知");
        description.setTextColor(TEXT_SECONDARY);
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionLp.topMargin = dp(8);
        container.addView(description, descriptionLp);

        TextView action = new TextView(context);
        action.setText("开启通知访问");
        action.setTextColor(TEXT_PRIMARY);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        action.setGravity(Gravity.CENTER);
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(ACCENT_COLOR);
        buttonBackground.setCornerRadius(dp(20));
        action.setBackground(buttonBackground);
        action.setPadding(dp(20), dp(9), dp(20), dp(9));
        action.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onRequestAccess();
            }
        });
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.topMargin = dp(18);
        container.addView(action, actionLp);
        return container;
    }

    private void toggleGroup(String packageName) {
        if (!expandedPackages.remove(packageName)) {
            expandedPackages.add(packageName);
        }
        setContent(currentItems, currentAccessGranted);
    }

    private View buildGroupHeader(
            String packageName,
            List<MediaNotificationListenerService.NotificationSnapshot> group,
            boolean collapsible,
            boolean expanded) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(12), dp(12), dp(6));

        ImageView icon = new ImageView(getContext());
        Drawable drawable = loadAppIcon(packageName);
        if (drawable != null) {
            icon.setImageDrawable(drawable);
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView name = new TextView(getContext());
        name.setText(appLabel(group));
        name.setTextColor(TEXT_SECONDARY);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameLp.leftMargin = dp(8);
        row.addView(name, nameLp);

        int unreadCount = 0;
        for (MediaNotificationListenerService.NotificationSnapshot item : group) {
            unreadCount += item.unreadWeight();
        }
        TextView count = new TextView(getContext());
        count.setText(String.valueOf(unreadCount));
        count.setTextColor(TEXT_SECONDARY);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.leftMargin = dp(6);
        row.addView(count, countLp);

        if (collapsible) {
            TextView chevron = new TextView(getContext());
            chevron.setText(expanded ? "▾" : "▸");
            chevron.setTextColor(TEXT_TERTIARY);
            chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            chevron.setGravity(Gravity.CENTER);
            chevron.setContentDescription(expanded
                    ? "收起 " + appLabel(group) + " 的通知"
                    : "展开 " + appLabel(group) + " 的通知");
            LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(
                    dp(28), dp(28));
            chevronLp.leftMargin = dp(2);
            row.addView(chevron, chevronLp);
            row.setClickable(true);
            row.setOnClickListener(v -> list.post(() -> toggleGroup(packageName)));
        }

        boolean hasClearable = false;
        for (MediaNotificationListenerService.NotificationSnapshot item : group) {
            if (!item.ongoing) {
                hasClearable = true;
                break;
            }
        }
        if (!hasClearable) {
            return row;
        }

        TextView clear = new TextView(getContext());
        clear.setText("清除");
        clear.setTextColor(ACCENT_COLOR);
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        clear.setPadding(dp(10), dp(4), dp(8), dp(4));
        clear.setContentDescription("清除 " + appLabel(group) + " 的通知");
        clear.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onDismissApp(packageName);
            }
        });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clearLp.leftMargin = dp(4);
        row.addView(clear, clearLp);
        return row;
    }

    private View buildRow(MediaNotificationListenerService.NotificationSnapshot item) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(8), dp(8), dp(10));

        LinearLayout textColumn = new LinearLayout(getContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(getContext());
        String titleText = TextUtils.isEmpty(item.title) ? item.appLabel : item.title;
        title.setText(titleText);
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(getContext());
        body.setText(TextUtils.isEmpty(item.text) ? item.subText : item.text);
        body.setTextColor(TEXT_SECONDARY);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.setMaxLines(3);
        body.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(2);
        textColumn.addView(body, bodyLp);

        TextView time = new TextView(getContext());
        CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(item.postTime,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
        time.setText(item.unreadWeight() > 1
                ? relativeTime + " · " + item.unreadWeight() + " 条消息" : relativeTime);
        time.setTextColor(TEXT_TERTIARY);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeLp.topMargin = dp(3);
        textColumn.addView(time, timeLp);

        if (item.ongoing) {
            TextView ongoing = new TextView(getContext());
            ongoing.setText("常驻");
            ongoing.setTextColor(TEXT_TERTIARY);
            ongoing.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            ongoing.setGravity(Gravity.CENTER);
            row.addView(ongoing, new LinearLayout.LayoutParams(dp(40), dp(40)));
        } else {
            TextView delete = new TextView(getContext());
            delete.setText("✕");
            delete.setTextColor(TEXT_TERTIARY);
            delete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            delete.setGravity(Gravity.CENTER);
            delete.setContentDescription("删除通知");
            delete.setOnClickListener(v -> {
                if (callbacks != null) {
                    callbacks.onDismissNotification(item);
                }
            });
            row.addView(delete, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }

        row.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onOpenNotification(item);
            }
        });

        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View divider = new View(getContext());
        divider.setBackgroundColor(DIVIDER_COLOR);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
        dividerLp.leftMargin = dp(18);
        wrapper.addView(divider, dividerLp);
        return wrapper;
    }

    private String appLabel(List<MediaNotificationListenerService.NotificationSnapshot> group) {
        if (group.isEmpty()) {
            return "";
        }
        String label = group.get(0).appLabel;
        return TextUtils.isEmpty(label) ? group.get(0).packageName : label;
    }

    private Drawable loadAppIcon(String packageName) {
        try {
            return getContext().getPackageManager().getApplicationIcon(packageName);
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
