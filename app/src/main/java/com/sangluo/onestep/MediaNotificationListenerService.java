package com.sangluo.onestep;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "OneStep40Media";
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,2}:\\d{2}(?::\\d{2})?)(?!\\d)");
    private static final Object LOCK = new Object();
    private static final List<MediaNotificationSnapshot> SNAPSHOTS = new ArrayList<>();
    private static final List<TopComponentNotificationSnapshot> TOP_COMPONENT_SNAPSHOTS =
            new ArrayList<>();
    private static final CopyOnWriteArrayList<MediaUpdateListener> LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final List<NotificationSnapshot> NOTIFICATIONS = new ArrayList<>();
    private static final Map<String, String> SEEN_SIGNATURES = new HashMap<>();
    private static final Map<String, String> APP_LABEL_CACHE = new HashMap<>();
    private static final CopyOnWriteArrayList<NotificationUpdateListener>
            NOTIFICATION_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile int unreadCount;
    private static volatile MediaNotificationListenerService connectedService;

    public interface MediaUpdateListener {
        void onMediaNotificationsChanged();
    }

    /** Receives every change of the general (non-media) notification list. */
    public interface NotificationUpdateListener {
        void onNotificationsChanged();
    }

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, MediaNotificationListenerService.class);
    }

    public static void addListener(MediaUpdateListener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(MediaUpdateListener listener) {
        LISTENERS.remove(listener);
    }

    public static void addNotificationUpdateListener(NotificationUpdateListener listener) {
        if (listener != null && !NOTIFICATION_LISTENERS.contains(listener)) {
            NOTIFICATION_LISTENERS.add(listener);
        }
    }

    public static void removeNotificationUpdateListener(NotificationUpdateListener listener) {
        NOTIFICATION_LISTENERS.remove(listener);
    }

    /** Every clearable notification currently on the status bar, newest first. */
    public static List<NotificationSnapshot> getNotifications() {
        synchronized (LOCK) {
            return new ArrayList<>(NOTIFICATIONS);
        }
    }

    public static int getUnreadCount() {
        return unreadCount;
    }

    public static boolean isConnected() {
        return connectedService != null;
    }

    /** Clears the unread badge: everything currently listed counts as seen. */
    public static void markAllSeen() {
        boolean changed;
        synchronized (LOCK) {
            changed = unreadCount > 0;
            Set<String> activeKeys = new HashSet<>();
            for (NotificationSnapshot snapshot : NOTIFICATIONS) {
                activeKeys.add(snapshot.key);
            }
            SEEN_SIGNATURES.keySet().retainAll(activeKeys);
            for (NotificationSnapshot snapshot : NOTIFICATIONS) {
                SEEN_SIGNATURES.put(snapshot.key, snapshot.signature());
            }
            unreadCount = 0;
        }
        if (changed) {
            notifyNotificationListeners();
        }
    }

    public static void dismissNotification(String key) {
        MediaNotificationListenerService service = connectedService;
        if (service == null || TextUtils.isEmpty(key)) {
            return;
        }
        try {
            service.cancelNotification(key);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cancel notification failed: " + e.getClass().getSimpleName());
        }
    }

    public static void dismissNotificationsForPackage(String packageName) {
        MediaNotificationListenerService service = connectedService;
        if (service == null || TextUtils.isEmpty(packageName)) {
            return;
        }
        List<String> keys = new ArrayList<>();
        synchronized (LOCK) {
            for (NotificationSnapshot snapshot : NOTIFICATIONS) {
                if (!snapshot.ongoing
                        && TextUtils.equals(packageName, snapshot.packageName)) {
                    keys.add(snapshot.key);
                }
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        try {
            service.cancelNotifications(keys.toArray(new String[0]));
        } catch (RuntimeException e) {
            Log.w(TAG, "Cancel package notifications failed: "
                    + e.getClass().getSimpleName());
        }
    }

    public static void dismissAllNotifications() {
        MediaNotificationListenerService service = connectedService;
        if (service == null) {
            return;
        }
        try {
            service.cancelAllNotifications();
        } catch (RuntimeException e) {
            Log.w(TAG, "Cancel all notifications failed: " + e.getClass().getSimpleName());
        }
    }

    /** True when the user granted this app notification-listener access. */
    public static boolean isAccessGranted(Context context) {
        if (context == null) {
            return false;
        }
        String enabledListeners = android.provider.Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        ComponentName componentName = getComponentName(context);
        return isComponentListed(enabledListeners, componentName.flattenToString())
                || isComponentListed(enabledListeners,
                        componentName.flattenToShortString());
    }

    private static boolean isComponentListed(String enabledListeners, String component) {
        if (TextUtils.isEmpty(enabledListeners) || TextUtils.isEmpty(component)) {
            return false;
        }
        String normalized = ":" + enabledListeners + ":";
        return normalized.contains(":" + component + ":");
    }

    public static List<MediaNotificationSnapshot> getSnapshots() {
        synchronized (LOCK) {
            return new ArrayList<>(SNAPSHOTS);
        }
    }

    public static List<TopComponentNotificationSnapshot> getTopComponentSnapshots() {
        synchronized (LOCK) {
            return new ArrayList<>(TOP_COMPONENT_SNAPSHOTS);
        }
    }

    @Override
    public void onListenerConnected() {
        connectedService = this;
        rebuildSnapshotsSafely();
    }

    @Override
    public void onListenerDisconnected() {
        if (connectedService == this) {
            connectedService = null;
        }
    }

    @Override
    public void onDestroy() {
        if (connectedService == this) {
            connectedService = null;
        }
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        connectedService = this;
        rebuildSnapshotsSafely();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        connectedService = this;
        rebuildSnapshotsSafely();
    }

    private void rebuildSnapshotsSafely() {
        try {
            rebuildSnapshots(getActiveNotifications());
        } catch (RuntimeException e) {
            Log.w(TAG, "Rebuild media notifications failed: " + e.getClass().getSimpleName());
        }
    }

    private void rebuildSnapshots(StatusBarNotification[] activeNotifications) {
        List<MediaNotificationSnapshot> nextSnapshots = new ArrayList<>();
        List<TopComponentNotificationSnapshot> nextTopComponentSnapshots = new ArrayList<>();
        List<NotificationSnapshot> nextNotifications = new ArrayList<>();
        if (activeNotifications != null) {
            for (StatusBarNotification sbn : activeNotifications) {
                MediaNotificationSnapshot snapshot = createMediaSnapshot(sbn);
                if (snapshot != null) {
                    nextSnapshots.add(snapshot);
                }
                TopComponentNotificationSnapshot topSnapshot = createTopComponentSnapshot(sbn);
                if (topSnapshot != null) {
                    nextTopComponentSnapshots.add(topSnapshot);
                }
                NotificationSnapshot notificationSnapshot = createNotificationSnapshot(sbn);
                if (notificationSnapshot != null) {
                    nextNotifications.add(notificationSnapshot);
                }
            }
        }
        Collections.sort(nextNotifications, new Comparator<NotificationSnapshot>() {
            @Override
            public int compare(NotificationSnapshot left, NotificationSnapshot right) {
                return Long.compare(right.postTime, left.postTime);
            }
        });
        Collections.sort(nextSnapshots, new Comparator<MediaNotificationSnapshot>() {
            @Override
            public int compare(MediaNotificationSnapshot left, MediaNotificationSnapshot right) {
                if (left.likelyPlaying != right.likelyPlaying) {
                    return left.likelyPlaying ? -1 : 1;
                }
                if (left.ongoing != right.ongoing) {
                    return left.ongoing ? -1 : 1;
                }
                return Long.compare(right.postTime, left.postTime);
            }
        });
        Collections.sort(nextTopComponentSnapshots,
                new Comparator<TopComponentNotificationSnapshot>() {
                    @Override
                    public int compare(TopComponentNotificationSnapshot left,
                                       TopComponentNotificationSnapshot right) {
                        if (left.ongoing != right.ongoing) {
                            return left.ongoing ? -1 : 1;
                        }
                        return Long.compare(right.postTime, left.postTime);
                    }
                });
        synchronized (LOCK) {
            SNAPSHOTS.clear();
            SNAPSHOTS.addAll(nextSnapshots);
            TOP_COMPONENT_SNAPSHOTS.clear();
            TOP_COMPONENT_SNAPSHOTS.addAll(nextTopComponentSnapshots);
            NOTIFICATIONS.clear();
            NOTIFICATIONS.addAll(nextNotifications);
            Set<String> activeKeys = new HashSet<>();
            for (NotificationSnapshot snapshot : NOTIFICATIONS) {
                activeKeys.add(snapshot.key);
            }
            SEEN_SIGNATURES.keySet().retainAll(activeKeys);
            int nextUnread = 0;
            for (NotificationSnapshot snapshot : NOTIFICATIONS) {
                if (!snapshot.ongoing
                        && !snapshot.signature().equals(SEEN_SIGNATURES.get(snapshot.key))) {
                    nextUnread += snapshot.unreadWeight();
                }
            }
            unreadCount = nextUnread;
        }
        notifyListeners();
        notifyNotificationListeners();
    }

    private static void notifyNotificationListeners() {
        for (NotificationUpdateListener listener : NOTIFICATION_LISTENERS) {
            try {
                listener.onNotificationsChanged();
            } catch (RuntimeException e) {
                Log.w(TAG, "Notification listener failed: " + e.getClass().getSimpleName());
            }
        }
    }

    private void notifyListeners() {
        for (MediaUpdateListener listener : LISTENERS) {
            listener.onMediaNotificationsChanged();
        }
    }

    private NotificationSnapshot createNotificationSnapshot(StatusBarNotification sbn) {
        if (sbn == null || TextUtils.equals(getPackageName(), sbn.getPackageName())) {
            return null;
        }
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) {
            return null;
        }
        int flags = notification.flags;
        if ((flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return null;
        }
        // Ongoing / foreground-service notifications stay visible in the list (VPN,
        // hotspot, casting services...), they just never count as unread messages and
        // cannot be dismissed.
        boolean ongoing = (flags & (Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_FOREGROUND_SERVICE)) != 0 || !sbn.isClearable();
        Bundle extras = notification.extras;
        String title = firstNonEmpty(
                text(extras.getCharSequence(Notification.EXTRA_TITLE)),
                text(extras.getCharSequence(Notification.EXTRA_TITLE_BIG)),
                text(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)));
        String body = firstNonEmpty(
                text(extras.getCharSequence(Notification.EXTRA_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)));
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(body)) {
            return null;
        }
        String subText = text(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        return new NotificationSnapshot(
                sbn.getKey(),
                sbn.getPackageName(),
                resolveAppLabel(sbn.getPackageName()),
                title,
                body,
                subText,
                sbn.getPostTime(),
                countUnreadMessages(notification, extras),
                ongoing);
    }

    /**
     * Number of unread messages a notification represents. Messaging apps put the
     * count in {@link Notification#number}; otherwise fall back to the messages /
     * inbox lines the notification carries.
     */
    private int countUnreadMessages(Notification notification, Bundle extras) {
        int count = Math.max(notification.number, 0);
        if (count > 0) {
            return count;
        }
        android.os.Parcelable[] messages =
                extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) {
            return messages.length;
        }
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            return lines.length;
        }
        return 1;
    }

    private String resolveAppLabel(String packageName) {
        synchronized (APP_LABEL_CACHE) {
            String cached = APP_LABEL_CACHE.get(packageName);
            if (cached != null) {
                return cached;
            }
        }
        String label = packageName;
        try {
            PackageManager packageManager = getPackageManager();
            label = String.valueOf(packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)));
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
        }
        synchronized (APP_LABEL_CACHE) {
            APP_LABEL_CACHE.put(packageName, label);
        }
        return label;
    }

    private MediaNotificationSnapshot createMediaSnapshot(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) {
            return null;
        }
        Bundle extras = notification.extras;
        MediaSession.Token token = getParcelable(extras,
                Notification.EXTRA_MEDIA_SESSION, MediaSession.Token.class);
        boolean mediaLike = token != null
                || TextUtils.equals(Notification.CATEGORY_TRANSPORT, notification.category)
                || hasMediaAction(notification.actions);
        if (!mediaLike) {
            return null;
        }

        String title = firstNonEmpty(
                text(extras.getCharSequence(Notification.EXTRA_TITLE)),
                text(extras.getCharSequence(Notification.EXTRA_TITLE_BIG)));
        String body = firstNonEmpty(
                text(extras.getCharSequence(Notification.EXTRA_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)));
        String subText = firstNonEmpty(
                text(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)));
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(body)) {
            return null;
        }

        return new MediaNotificationSnapshot(
                sbn.getPackageName(),
                title,
                body,
                subText,
                getArtwork(notification, extras),
                token,
                sbn.getPostTime(),
                (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0,
                isLikelyPlaying(notification.actions));
    }

    private TopComponentNotificationSnapshot createTopComponentSnapshot(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) {
            return null;
        }
        Bundle extras = notification.extras;
        String title = firstNonEmpty(
                text(extras.getCharSequence(Notification.EXTRA_TITLE)),
                text(extras.getCharSequence(Notification.EXTRA_TITLE_BIG)));
        String body = firstNonEmpty(
                text(extras.getCharSequence(Notification.EXTRA_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)));
        String subText = firstNonEmpty(
                text(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)));
        String infoText = firstNonEmpty(
                text(extras.getCharSequence(Notification.EXTRA_INFO_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)));
        String combined = joinText(title, body, subText, infoText);
        boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        Notification.Action toggleAction = findToggleAction(notification.actions);
        Notification.Action stopAction = findStopAction(notification.actions);
        Notification.Action lapAction = findLapAction(notification.actions);
        TimerViewState timerViewState = isTimerNotificationCandidate(notification, combined)
                ? extractTimerViewState(notification) : null;
        int type = classifyTopComponent(notification, combined, sbn.getPackageName(), ongoing,
                toggleAction, timerViewState);
        if (type == TopComponentNotificationSnapshot.TYPE_NONE) {
            return null;
        }
        Notification.Action secondaryAction = type == TopComponentNotificationSnapshot.TYPE_STOPWATCH
                ? lapAction : null;
        String primary = firstNonEmpty(title, body, getPackageNameLabelFallback(sbn));
        String secondary = firstNonEmpty(body, subText, infoText, title);
        return new TopComponentNotificationSnapshot(
                type,
                sbn.getKey(),
                sbn.getPackageName(),
                primary,
                secondary,
                subText,
                infoText,
                getActionTitle(toggleAction),
                toggleAction == null ? null : toggleAction.actionIntent,
                getActionTitle(stopAction),
                stopAction == null ? null : stopAction.actionIntent,
                getActionTitle(secondaryAction),
                secondaryAction == null ? null : secondaryAction.actionIntent,
                isRunningFromActions(notification.actions),
                firstNonEmpty(timerViewState == null ? "" : timerViewState.displayedTime,
                        extractDurationText(combined)),
                timerViewState == null ? 0L : timerViewState.baseElapsedRealtime,
                extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
                        || timerViewState != null,
                timerViewState != null
                        ? timerViewState.countDown
                        : extras.getBoolean("android.chronometerCountDown", false),
                notification.when,
                sbn.getPostTime(),
                ongoing);
    }

    private int classifyTopComponent(Notification notification, String combined,
                                     String packageName, boolean ongoing,
                                     Notification.Action toggleAction,
                                     TimerViewState timerViewState) {
        String category = notification.category;
        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? notification.getChannelId() : "";
        boolean chronometer = notification.extras.getBoolean(
                Notification.EXTRA_SHOW_CHRONOMETER, false);
        boolean countdown = notification.extras.getBoolean("android.chronometerCountDown", false);
        boolean timerChannel = containsAny(channelId, "timer", "countdown", "stopwatch",
                "计时", "秒表");
        boolean stopwatchChannel = containsAny(channelId, "stopwatch", "秒表");
        boolean timerText = containsAny(combined, "倒计时", "计时", "秒表", "timer",
                "countdown", "stopwatch");
        boolean stopwatchCategory = TextUtils.equals("stopwatch", category);
        boolean stopwatchActions = containsActionTitle(notification.actions, "计次", "一圈", "lap");
        boolean timerActions = containsActionTitle(notification.actions,
                "加上", "add minute");
        boolean structuredTimer = timerViewState != null
                && (timerViewState.countDown
                || TextUtils.equals(Notification.CATEGORY_ALARM, category)
                || timerActions);
        if ((ongoing || toggleAction != null)
                && (stopwatchChannel || stopwatchCategory || stopwatchActions
                || containsAny(combined, "正计时", "秒表", "stopwatch"))) {
            return TopComponentNotificationSnapshot.TYPE_STOPWATCH;
        }
        if ((ongoing || toggleAction != null)
                && (timerChannel || timerText || structuredTimer
                || (chronometer && (countdown
                || TextUtils.equals(Notification.CATEGORY_ALARM, category))))) {
            return TopComponentNotificationSnapshot.TYPE_TIMER;
        }
        boolean recordingIdentity = containsAny(combined, "录音", "录制中", "正在录制",
                "recording", "recorder", "voice memo", "voice recorder")
                || containsAny(channelId, "record", "recorder", "录音")
                || containsAny(packageName, "record", "recorder");
        if ((ongoing || hasResumeAction(notification.actions)) && recordingIdentity) {
            return TopComponentNotificationSnapshot.TYPE_RECORDING;
        }
        if (chronometer && toggleAction != null
                && containsAny(combined, "暂停", "继续", "pause", "resume", "stop")) {
            return TopComponentNotificationSnapshot.TYPE_TIMER;
        }
        return TopComponentNotificationSnapshot.TYPE_NONE;
    }

    private boolean isTimerNotificationCandidate(Notification notification, String combined) {
        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? notification.getChannelId() : "";
        return notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
                || TextUtils.equals(Notification.CATEGORY_ALARM, notification.category)
                || TextUtils.equals("stopwatch", notification.category)
                || containsAny(channelId, "timer", "countdown", "stopwatch", "计时", "秒表")
                || containsAny(combined, "倒计时", "计时", "秒表", "timer", "countdown",
                "stopwatch");
    }

    private TimerViewState extractTimerViewState(Notification notification) {
        RemoteViews[] remoteViews = {
                notification.contentView,
                notification.bigContentView,
                notification.headsUpContentView
        };
        for (RemoteViews views : remoteViews) {
            if (views == null) {
                continue;
            }
            try {
                FrameLayout parent = new FrameLayout(this);
                View root = views.apply(this, parent);
                TimerViewState state = new TimerViewState();
                collectTimerViewState(root, state);
                if (state.baseElapsedRealtime > 0L
                        || !TextUtils.isEmpty(state.displayedTime)) {
                    return state;
                }
            } catch (RuntimeException e) {
                Log.d(TAG, "Read timer notification view failed: "
                        + e.getClass().getSimpleName());
            }
        }
        return null;
    }

    private void collectTimerViewState(View view, TimerViewState state) {
        if (view == null || state == null || view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof Chronometer && state.baseElapsedRealtime <= 0L) {
            Chronometer chronometer = (Chronometer) view;
            state.baseElapsedRealtime = chronometer.getBase();
            state.countDown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && chronometer.isCountDown();
        }
        if (view instanceof TextView && TextUtils.isEmpty(state.displayedTime)) {
            state.displayedTime = extractDurationText(
                    text(((TextView) view).getText()));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTimerViewState(group.getChildAt(i), state);
            }
        }
    }

    private String extractDurationText(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        Matcher matcher = DURATION_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean containsActionTitle(Notification.Action[] actions, String... needles) {
        if (actions == null) {
            return false;
        }
        for (Notification.Action action : actions) {
            if (containsAny(text(action == null ? null : action.title), needles)) {
                return true;
            }
        }
        return false;
    }

    private Notification.Action findToggleAction(Notification.Action[] actions) {
        if (actions == null) {
            return null;
        }
        for (Notification.Action action : actions) {
            if (action == null || action.actionIntent == null) {
                continue;
            }
            String title = text(action.title);
            if (containsAny(title, "暂停", "继续", "恢复", "开始", "pause", "resume",
                    "start", "play")) {
                return action;
            }
        }
        return null;
    }

    private Notification.Action findStopAction(Notification.Action[] actions) {
        if (actions == null) {
            return null;
        }
        for (Notification.Action action : actions) {
            if (action == null || action.actionIntent == null) {
                continue;
            }
            String title = text(action.title);
            if (containsAny(title, "停止", "结束", "完成", "stop", "finish", "done")) {
                return action;
            }
        }
        return null;
    }

    private Notification.Action findLapAction(Notification.Action[] actions) {
        return findActionByTitle(actions, "计次", "计圈", "记圈", "一圈", "lap");
    }

    private Notification.Action findActionByTitle(Notification.Action[] actions,
                                                  String... needles) {
        if (actions == null) {
            return null;
        }
        for (Notification.Action action : actions) {
            if (action != null && action.actionIntent != null
                    && containsAny(text(action.title), needles)) {
                return action;
            }
        }
        return null;
    }

    private boolean hasResumeAction(Notification.Action[] actions) {
        if (actions == null) {
            return false;
        }
        for (Notification.Action action : actions) {
            String title = text(action == null ? null : action.title);
            if (containsAny(title, "继续", "恢复", "resume")) {
                return true;
            }
        }
        return false;
    }

    private String getActionTitle(Notification.Action action) {
        return action == null ? "" : text(action.title);
    }

    private boolean isRunningFromActions(Notification.Action[] actions) {
        if (actions == null) {
            return true;
        }
        for (Notification.Action action : actions) {
            String title = text(action == null ? null : action.title);
            if (containsAny(title, "继续", "恢复", "开始", "resume", "start", "play")) {
                return false;
            }
            if (containsAny(title, "暂停", "pause")) {
                return true;
            }
        }
        return true;
    }

    private String getPackageNameLabelFallback(StatusBarNotification sbn) {
        return sbn == null ? "" : sbn.getPackageName();
    }

    private boolean hasMediaAction(Notification.Action[] actions) {
        if (actions == null) {
            return false;
        }
        for (Notification.Action action : actions) {
            String title = text(action == null ? null : action.title);
            if (containsAny(title, "播放", "暂停", "上一", "下一", "play", "pause", "previous",
                    "next")) {
                return true;
            }
        }
        return false;
    }

    private boolean isLikelyPlaying(Notification.Action[] actions) {
        if (actions == null) {
            return false;
        }
        for (Notification.Action action : actions) {
            String title = text(action == null ? null : action.title);
            if (containsAny(title, "暂停", "pause")) {
                return true;
            }
            if (containsAny(title, "播放", "play")) {
                return false;
            }
        }
        return false;
    }

    private Bitmap getArtwork(Notification notification, Bundle extras) {
        Bitmap bitmap = getParcelable(extras, Notification.EXTRA_LARGE_ICON, Bitmap.class);
        if (bitmap != null) {
            return bitmap;
        }
        bitmap = getParcelable(extras, Notification.EXTRA_LARGE_ICON_BIG, Bitmap.class);
        if (bitmap != null) {
            return bitmap;
        }
        bitmap = getParcelable(extras, Notification.EXTRA_PICTURE, Bitmap.class);
        if (bitmap != null) {
            return bitmap;
        }
        Icon icon = notification.getLargeIcon();
        if (icon == null) {
            icon = getParcelable(extras, Notification.EXTRA_LARGE_ICON, Icon.class);
        }
        if (icon == null) {
            icon = getParcelable(extras, Notification.EXTRA_LARGE_ICON_BIG, Icon.class);
        }
        if (icon == null) {
            icon = getParcelable(extras, Notification.EXTRA_PICTURE_ICON, Icon.class);
        }
        return icon == null ? null : drawableToBitmap(icon.loadDrawable(this));
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private static <T> T getParcelable(Bundle extras, String key, Class<T> type) {
        Object value = extras.getParcelable(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private static boolean containsAny(String text, String... needles) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        String normalized = text.toLowerCase();
        for (String needle : needles) {
            if (normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equals(value)) {
                return value;
            }
        }
        return "";
    }

    private static String text(CharSequence value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String joinText(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static final class TimerViewState {
        long baseElapsedRealtime;
        boolean countDown;
        String displayedTime = "";
    }

    /** One clearable status-bar notification, grouped by package in the panel. */
    public static final class NotificationSnapshot {
        public final String key;
        public final String packageName;
        public final String appLabel;
        public final String title;
        public final String text;
        public final String subText;
        public final long postTime;
        /** Unread messages represented by this notification, at least 1. */
        public final int messageCount;
        /** Ongoing service notification: shown in the list, but never unread. */
        public final boolean ongoing;

        private NotificationSnapshot(String key, String packageName, String appLabel,
                                     String title, String text, String subText,
                                     long postTime, int messageCount, boolean ongoing) {
            this.key = key;
            this.packageName = packageName;
            this.appLabel = appLabel;
            this.title = title;
            this.text = text;
            this.subText = subText;
            this.postTime = postTime;
            this.messageCount = Math.max(1, messageCount);
            this.ongoing = ongoing;
        }

        /** How many messages this notification stands for in the unread badge. */
        public int unreadWeight() {
            return messageCount;
        }

        /** Changes when content changes, so an update counts as a new unread message. */
        public String signature() {
            return postTime + "|" + title + "|" + text + "|" + subText + "|" + messageCount;
        }
    }

    public static final class MediaNotificationSnapshot {
        public final String packageName;
        public final String title;
        public final String text;
        public final String subText;
        public final Bitmap artwork;
        public final MediaSession.Token sessionToken;
        public final long postTime;
        public final boolean ongoing;
        public final boolean likelyPlaying;

        private MediaNotificationSnapshot(String packageName, String title, String text,
                                          String subText, Bitmap artwork,
                                          MediaSession.Token sessionToken, long postTime,
                                          boolean ongoing, boolean likelyPlaying) {
            this.packageName = packageName;
            this.title = title;
            this.text = text;
            this.subText = subText;
            this.artwork = artwork;
            this.sessionToken = sessionToken;
            this.postTime = postTime;
            this.ongoing = ongoing;
            this.likelyPlaying = likelyPlaying;
        }
    }

    public static final class TopComponentNotificationSnapshot {
        public static final int TYPE_NONE = 0;
        public static final int TYPE_TIMER = 2;
        public static final int TYPE_RECORDING = 3;
        public static final int TYPE_STOPWATCH = 4;

        public final int type;
        public final String notificationKey;
        public final String packageName;
        public final String title;
        public final String text;
        public final String subText;
        public final String infoText;
        public final String actionTitle;
        public final PendingIntent toggleAction;
        public final String stopActionTitle;
        public final PendingIntent stopAction;
        public final String secondaryActionTitle;
        public final PendingIntent secondaryAction;
        public final boolean running;
        public final String displayedTime;
        public final long chronometerBaseElapsedRealtime;
        public final boolean showChronometer;
        public final boolean chronometerCountDown;
        public final long when;
        public final long postTime;
        public final boolean ongoing;

        private TopComponentNotificationSnapshot(int type, String notificationKey,
                                                 String packageName, String title,
                                                 String text, String subText, String infoText,
                                                 String actionTitle,
                                                 PendingIntent toggleAction,
                                                 String stopActionTitle,
                                                 PendingIntent stopAction,
                                                 String secondaryActionTitle,
                                                 PendingIntent secondaryAction, boolean running,
                                                 String displayedTime,
                                                 long chronometerBaseElapsedRealtime,
                                                 boolean showChronometer,
                                                 boolean chronometerCountDown, long when,
                                                 long postTime, boolean ongoing) {
            this.type = type;
            this.notificationKey = notificationKey;
            this.packageName = packageName;
            this.title = title;
            this.text = text;
            this.subText = subText;
            this.infoText = infoText;
            this.actionTitle = actionTitle;
            this.toggleAction = toggleAction;
            this.stopActionTitle = stopActionTitle;
            this.stopAction = stopAction;
            this.secondaryActionTitle = secondaryActionTitle;
            this.secondaryAction = secondaryAction;
            this.running = running;
            this.displayedTime = displayedTime;
            this.chronometerBaseElapsedRealtime = chronometerBaseElapsedRealtime;
            this.showChronometer = showChronometer;
            this.chronometerCountDown = chronometerCountDown;
            this.when = when;
            this.postTime = postTime;
            this.ongoing = ongoing;
        }
    }
}
