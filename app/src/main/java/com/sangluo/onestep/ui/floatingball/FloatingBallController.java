package com.sangluo.onestep.ui.floatingball;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import com.sangluo.onestep.data.settings.OneStepSettings;
import com.sangluo.onestep.model.LauncherApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns the floating ball's lifecycle, gesture state machine, ring geometry and
 * action dispatch.
 *
 * <p>Both the ball and the ring live in their own {@code TYPE_APPLICATION_PANEL}
 * windows: OneStep hosts apps inside {@code SurfaceView}s, and a view added to the
 * activity's hierarchy is covered by those surfaces once an app takes the pane
 * fullscreen. Panel windows ride on top of the activity window instead, exactly like
 * the main-pane swap control.</p>
 */
public final class FloatingBallController {
    private static final String TAG = "OneStep40_FloatingBall";
    private static final float DEFAULT_Y_RATIO = 0.82f;
    private static final float BALL_EDGE_MARGIN_DP = 8f;
    /** Idle time before the ball slides back to the edge, leaving half of it visible. */
    private static final long AUTO_DOCK_DELAY_MS = 3000L;
    private static final long DOCK_ANIM_MS = 180L;
    private static final long ATTACH_RETRY_MS = 100L;
    private static final long RAISE_THROTTLE_MS = 250L;
    /**
     * Side slots with content install their own {@code TYPE_APPLICATION_PANEL} touch
     * shields, which sit above any panel sub-window added earlier and swallow the ball's
     * gestures. Attached-dialog windows sort above panels, so the ball stays clickable
     * even when it overlaps a shrunken slot.
     */
    private static final int SUB_WINDOW_TYPE =
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
    private static final int FALLBACK_SUB_WINDOW_TYPE =
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;

    public interface Callbacks {
        boolean isEnabled();

        /** Snapshot of apps currently hosted in OneStep slots, in visual order. */
        List<FloatingBallItem> openedAppItems();

        /** User-configured second-layer apps, already reconciled with the launcher list. */
        List<LauncherApp> pinnedApps();

        Drawable collapsedIcon();

        String collapsedLabel();

        boolean isInteractionBlocked();

        void onFocusSlot(int slot);

        void onOpenApp(LauncherApp app);

        void onOpenPicker();

        void onExpandedChanged(boolean expanded);

        float[] savedPosition();

        void onPositionChanged(float xRatio, float yRatio);
    }

    private enum State {
        IDLE, PRESSED, DRAGGING, EXPANDED, LOCKED
    }

    private enum Layer {
        OPENED, PINNED
    }

    private final Activity activity;
    private final Callbacks callbacks;
    private final float density;
    private final int ballSizePx;
    private final float itemRadiusPx;
    private final float hitSlopPx;
    private final float touchSlopPx;
    private final float relightSlopPx;

    private final Runnable attachRetryRunnable = this::ensureWindows;

    private FloatingBallView ball;
    private FloatingBallOverlayView overlay;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams overlayParams;
    private boolean ballWindowAttached;
    private boolean overlayWindowAttached;
    private boolean attached;
    private boolean suppressed;

    private State state = State.IDLE;
    private Layer layer = Layer.OPENED;

    private float downRawX;
    private float downRawY;
    private float lastRawX;
    private float lastRawY;
    private float relightAnchorX;
    private float relightAnchorY;
    private boolean relightPending;

    private List<FloatingBallItem> ringItems = Collections.emptyList();
    private float[] ringCenters = new float[0];
    private int highlightIndex = -1;

    private float positionYRatio = DEFAULT_Y_RATIO;
    private boolean dockedRight = true;
    private boolean docked = true;
    private ValueAnimator dockAnimator;
    private Runnable autoDockRunnable;
    private long lastRaiseUptime;

    private Runnable longPressRunnable;
    private Runnable dwellRunnable;
    private Runnable lockTimeoutRunnable;

    public FloatingBallController(Activity activity, Callbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.ballSizePx = dp(OneStepSettings.FLOATING_BALL_SIZE_DP);
        this.itemRadiusPx = dp(FloatingBallLayoutPolicy.ITEM_RADIUS_DP);
        this.hitSlopPx = dp(FloatingBallLayoutPolicy.HIT_SLOP_DP);
        // The system slop is a floor; the policy value is the design intent.
        this.touchSlopPx = Math.max(dp(FloatingBallGesturePolicy.TOUCH_SLOP_DP),
                ViewConfiguration.get(activity).getScaledTouchSlop());
        this.relightSlopPx = dp(FloatingBallGesturePolicy.RELIGHT_SLOP_DP);
    }

    // --------------------------------------------------------------- lifecycle

    public void attach() {
        if (attached) {
            return;
        }
        attached = true;
        float[] saved = callbacks.savedPosition();
        if (saved != null && saved.length == 2) {
            dockedRight = saved[0] >= 0.5f;
            positionYRatio = saved[1];
        }
        ball = new FloatingBallView(activity);
        ball.setBallTouchListener(this::onBallTouch);
        ballParams = createBallParams();
        overlay = new FloatingBallOverlayView(activity);
        overlay.setVisibility(View.GONE);
        overlay.setTouchListener(this::onOverlayTouch);
        overlayParams = createOverlayParams();
        refresh();
    }

    public void detach() {
        attached = false;
        cancelAutoDock();
        cancelDockAnimation();
        cancelLongPress();
        cancelDwell();
        cancelLockTimeout();
        removeBallWindow();
        removeOverlayWindow();
        activity.getWindow().getDecorView().removeCallbacks(attachRetryRunnable);
        ball = null;
        overlay = null;
        state = State.IDLE;
    }

    private void ensureWindows() {
        if (!attached || suppressed || !callbacks.isEnabled()) {
            return;
        }
        View decor = activity.getWindow().getDecorView();
        IBinder token = decor.getWindowToken();
        if (token == null || !decor.isAttachedToWindow()) {
            decor.removeCallbacks(attachRetryRunnable);
            decor.postDelayed(attachRetryRunnable, ATTACH_RETRY_MS);
            return;
        }
        if (ball != null && !ballWindowAttached) {
            ballParams.token = token;
            applyBallPosition();
            if (!addSubWindow(ball, ballParams, "ball")) {
                decor.removeCallbacks(attachRetryRunnable);
                decor.postDelayed(attachRetryRunnable, ATTACH_RETRY_MS);
                return;
            }
            ballWindowAttached = true;
        }
        if (overlay != null && !overlayWindowAttached) {
            overlayParams.token = token;
            if (addSubWindow(overlay, overlayParams, "ring")) {
                overlayWindowAttached = true;
            }
        }
    }

    /** Adds a sub-window above the panel shields, falling back to a plain panel window. */
    private boolean addSubWindow(View view, WindowManager.LayoutParams params, String name) {
        params.type = SUB_WINDOW_TYPE;
        try {
            activity.getWindowManager().addView(view, params);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to attach floating ball " + name + " as "
                    + SUB_WINDOW_TYPE + ": " + e.getClass().getSimpleName());
        }
        params.type = FALLBACK_SUB_WINDOW_TYPE;
        try {
            activity.getWindowManager().addView(view, params);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to attach floating ball " + name + ": "
                    + e.getClass().getSimpleName());
            return false;
        }
    }

    private WindowManager.LayoutParams createBallParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ballSizePx,
                ballSizePx,
                SUB_WINDOW_TYPE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("OneStep floating ball");
        return params;
    }

    private WindowManager.LayoutParams createOverlayParams() {
        DisplayMetrics metrics = screenMetrics();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                metrics.widthPixels,
                metrics.heightPixels,
                SUB_WINDOW_TYPE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("OneStep floating ball ring");
        return params;
    }

    private void removeBallWindow() {
        if (!ballWindowAttached || ball == null) {
            ballWindowAttached = false;
            return;
        }
        try {
            activity.getWindowManager().removeViewImmediate(ball);
        } catch (IllegalArgumentException ignored) {
        }
        ballWindowAttached = false;
    }

    private void removeOverlayWindow() {
        if (!overlayWindowAttached || overlay == null) {
            overlayWindowAttached = false;
            return;
        }
        try {
            activity.getWindowManager().removeViewImmediate(overlay);
        } catch (IllegalArgumentException ignored) {
        }
        overlayWindowAttached = false;
    }

    /** Re-reads slot state; only touches the ball while collapsed. */
    public void refresh() {
        if (!attached || ball == null) {
            return;
        }
        if (!callbacks.isEnabled() || suppressed) {
            collapse();
            cancelAutoDock();
            cancelDockAnimation();
            docked = true;
            removeBallWindow();
            return;
        }
        ensureWindows();
        ball.setIcon(callbacks.collapsedIcon());
        ball.setContentLabel(callbacks.collapsedLabel());
        ball.setBadgeCount(callbacks.openedAppItems().size());
        if (state == State.IDLE && (dockAnimator == null || !dockAnimator.isRunning())) {
            applyBallPosition();
        }
    }

    /** Ball bounds in screen coordinates, or {@code null} while it has no window. */
    public Rect ballScreenRect() {
        if (ball == null || !ballWindowAttached || ball.getWidth() <= 0) {
            return null;
        }
        int[] location = new int[2];
        ball.getLocationOnScreen(location);
        return new Rect(location[0], location[1],
                location[0] + ball.getWidth(), location[1] + ball.getHeight());
    }

    /**
     * Re-attaches the ball (and ring) windows so they sort above windows added later.
     *
     * <p>Sub-windows of the same parent are stacked by insertion order, not by type:
     * the side-slot touch shields are created lazily and would otherwise bury the ball.
     * Only done while idle, since removing the window would cancel an active gesture.</p>
     */
    public void raise() {
        if (!attached || ball == null || !ballWindowAttached
                || state != State.IDLE || suppressed || !callbacks.isEnabled()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastRaiseUptime < RAISE_THROTTLE_MS) {
            return;
        }
        lastRaiseUptime = now;
        cancelDockAnimation();
        WindowManager windowManager = activity.getWindowManager();
        try {
            windowManager.removeViewImmediate(ball);
        } catch (RuntimeException ignored) {
        }
        ballWindowAttached = false;
        if (overlayWindowAttached && overlay != null) {
            try {
                windowManager.removeViewImmediate(overlay);
            } catch (RuntimeException ignored) {
            }
            overlayWindowAttached = false;
        }
        ensureWindows();
    }

    /** Temporarily hides the ball, e.g. while a modal dialog owns the screen. */
    public void setSuppressed(boolean suppressed) {
        if (this.suppressed == suppressed) {
            return;
        }
        this.suppressed = suppressed;
        refresh();
    }

    public boolean isTouchOnBall(float rawX, float rawY) {
        if (ball == null || !ballWindowAttached || ball.getWidth() <= 0) {
            return false;
        }
        int[] location = new int[2];
        ball.getLocationOnScreen(location);
        return rawX >= location[0] && rawX <= location[0] + ball.getWidth()
                && rawY >= location[1] && rawY <= location[1] + ball.getHeight();
    }

    public boolean isExpanded() {
        return state == State.EXPANDED || state == State.LOCKED;
    }

    public void collapse() {
        if (state == State.IDLE) {
            return;
        }
        boolean wasExpanded = isExpanded();
        boolean wasDragging = state == State.DRAGGING;
        cancelLongPress();
        cancelDwell();
        cancelLockTimeout();
        state = State.IDLE;
        highlightIndex = -1;
        if (overlay != null) {
            setOverlayInteractive(false);
            overlay.setHighlight(-1);
            overlay.playCollapse();
        }
        if (ball != null) {
            ball.setPressed(false);
        }
        if (wasDragging) {
            snapToSide();
        }
        if (wasExpanded) {
            callbacks.onExpandedChanged(false);
        }
        scheduleAutoDock();
    }

    // ---------------------------------------------------------------- gestures

    private boolean onBallTouch(FloatingBallView view, MotionEvent event) {
        if (!callbacks.isEnabled()) {
            return false;
        }
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (state != State.IDLE) {
                    return true;
                }
                downRawX = rawX;
                downRawY = rawY;
                lastRawX = rawX;
                lastRawY = rawY;
                state = State.PRESSED;
                undock();
                view.setPressed(true);
                ViewParent parent = view.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                scheduleLongPress();
                return true;
            case MotionEvent.ACTION_MOVE:
                lastRawX = rawX;
                lastRawY = rawY;
                handleMove(rawX, rawY);
                return true;
            case MotionEvent.ACTION_UP:
                lastRawX = rawX;
                lastRawY = rawY;
                handleUp(rawX, rawY);
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                collapse();
                return true;
            default:
                return true;
        }
    }

    private void handleMove(float rawX, float rawY) {
        switch (state) {
            case PRESSED:
                if (!FloatingBallGesturePolicy.shouldStartLongPress(
                        rawX - downRawX, rawY - downRawY, touchSlopPx)) {
                    cancelLongPress();
                    state = State.DRAGGING;
                    moveBallTo(rawX, rawY);
                }
                break;
            case DRAGGING:
                moveBallTo(rawX, rawY);
                break;
            case EXPANDED:
            case LOCKED:
                updateHighlightAt(rawX, rawY);
                break;
            default:
                break;
        }
    }

    private void handleUp(float rawX, float rawY) {
        switch (state) {
            case PRESSED:
                // Plain tap: open the ring in click-to-select mode.
                resetPressed();
                expand(true);
                break;
            case DRAGGING:
                moveBallTo(rawX, rawY);
                finishDrag();
                break;
            case EXPANDED:
            case LOCKED:
                if (FloatingBallGesturePolicy.isWithinSlop(
                        rawX - downRawX, rawY - downRawY, touchSlopPx)) {
                    collapse();
                    break;
                }
                updateHighlightAt(rawX, rawY);
                if (highlightIndex >= 0) {
                    execute(highlightIndex);
                } else {
                    collapse();
                }
                break;
            default:
                break;
        }
    }

    private void scheduleLongPress() {
        cancelLongPress();
        longPressRunnable = () -> {
            longPressRunnable = null;
            if (state != State.PRESSED) {
                return;
            }
            if (callbacks.isInteractionBlocked()) {
                collapse();
                return;
            }
            expand(false);
        };
        if (ball != null) {
            ball.postDelayed(longPressRunnable,
                    FloatingBallGesturePolicy.LONG_PRESS_TIMEOUT_MS);
        }
    }

    private void cancelLongPress() {
        if (longPressRunnable != null && ball != null) {
            ball.removeCallbacks(longPressRunnable);
        }
        longPressRunnable = null;
    }

    private void resetPressed() {
        cancelLongPress();
        state = State.IDLE;
        if (ball != null) {
            ball.setPressed(false);
        }
    }

    private void expand(boolean locked) {
        if (isExpanded() || overlay == null) {
            return;
        }
        if (callbacks.isInteractionBlocked() || !overlayWindowAttached) {
            scheduleAutoDock();
            return;
        }
        layer = initialLayer();
        state = locked ? State.LOCKED : State.EXPANDED;
        relightPending = false;
        rebuildRing();
        Log.d(TAG, "expand locked=" + locked + " layer=" + layer
                + " items=" + ringItems.size());
        setOverlayInteractive(locked);
        overlay.playExpand();
        if (locked) {
            scheduleLockTimeout();
        }
        if (ball != null) {
            ball.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        callbacks.onExpandedChanged(true);
    }

    private Layer initialLayer() {
        return callbacks.openedAppItems().isEmpty() ? Layer.PINNED : Layer.OPENED;
    }

    private void rebuildRing() {
        List<FloatingBallItem> items = new ArrayList<>();
        items.add(FloatingBallItem.layerSwitch());
        if (layer == Layer.OPENED) {
            items.addAll(callbacks.openedAppItems());
        } else {
            Set<String> openedKeys = openedInstanceKeys();
            for (LauncherApp app : callbacks.pinnedApps()) {
                items.add(FloatingBallItem.pinnedApp(app,
                        openedKeys.contains(app.instanceKey())));
            }
            items.add(FloatingBallItem.addButton());
        }
        ringItems = items;
        highlightIndex = -1;
        float[] geometry = computeGeometry();
        ringCenters = FloatingBallLayoutPolicy.computeCenters(
                geometry[0], geometry[1], geometry[2], items.size());
        overlay.setRing(items, layerLabel(), geometry[0], geometry[1], geometry[2],
                itemRadiusPx);
        overlay.setHighlight(-1);
    }

    private Set<String> openedInstanceKeys() {
        Set<String> keys = new HashSet<>();
        for (FloatingBallItem item : callbacks.openedAppItems()) {
            if (item != null && item.app != null) {
                keys.add(item.instanceKey());
            }
        }
        return keys;
    }

    private String layerLabel() {
        int max = OneStepSettings.MAX_FLOATING_BALL_APPS;
        if (layer == Layer.OPENED) {
            return "已打开 " + callbacks.openedAppItems().size() + "/" + max;
        }
        return "应用 " + callbacks.pinnedApps().size() + "/" + max;
    }

    // --------------------------------------------------------------- geometry

    private DisplayMetrics screenMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        return metrics;
    }

    private void parentLocationOnScreen(int[] out) {
        activity.getWindow().getDecorView().getLocationOnScreen(out);
    }

    /** Ring centre and radius in screen coordinates: {@code [centerX, centerY, radius]}. */
    private float[] computeGeometry() {
        int[] location = new int[2];
        float ballCenterX = 0f;
        float ballCenterY = 0f;
        if (ball != null && ballWindowAttached) {
            ball.getLocationOnScreen(location);
            ballCenterX = location[0] + ballSizePx / 2f;
            ballCenterY = location[1] + ballSizePx / 2f;
        }
        DisplayMetrics metrics = screenMetrics();
        float available = FloatingBallLayoutPolicy.availableRadius(
                ballCenterX, ballCenterY, metrics.widthPixels, metrics.heightPixels);
        float radiusPx = FloatingBallLayoutPolicy.resolveRadiusDp(available / density) * density;
        float centerX = FloatingBallLayoutPolicy.clampCenterX(
                ballCenterX, metrics.widthPixels, radiusPx);
        float centerY = FloatingBallLayoutPolicy.clampCenterY(
                ballCenterY, metrics.heightPixels, radiusPx);
        return new float[]{centerX, centerY, radiusPx};
    }

    private void updateHighlightAt(float rawX, float rawY) {
        if (relightPending) {
            if (!FloatingBallGesturePolicy.canRelight(rawX - relightAnchorX,
                    rawY - relightAnchorY, relightSlopPx)) {
                return;
            }
            relightPending = false;
        }
        if (overlay == null || !overlayWindowAttached) {
            return;
        }
        int[] location = new int[2];
        overlay.getLocationOnScreen(location);
        int index = FloatingBallLayoutPolicy.hitTest(ringCenters,
                rawX - location[0], rawY - location[1], itemRadiusPx, hitSlopPx);
        setHighlight(index);
    }

    private void setHighlight(int index) {
        if (index == highlightIndex) {
            return;
        }
        highlightIndex = index;
        if (overlay != null) {
            overlay.setHighlight(index);
        }
        cancelDwell();
        if (index == FloatingBallLayoutPolicy.SWITCH_INDEX) {
            scheduleDwell();
        }
        if (index >= 0 && ball != null) {
            ball.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private void scheduleDwell() {
        cancelDwell();
        dwellRunnable = () -> {
            dwellRunnable = null;
            if (!isExpanded() || highlightIndex != FloatingBallLayoutPolicy.SWITCH_INDEX) {
                return;
            }
            switchLayer();
        };
        if (ball != null) {
            ball.postDelayed(dwellRunnable, FloatingBallGesturePolicy.SWITCH_DWELL_MS);
        }
    }

    private void cancelDwell() {
        if (dwellRunnable != null && ball != null) {
            ball.removeCallbacks(dwellRunnable);
        }
        dwellRunnable = null;
    }

    private void switchLayer() {
        layer = layer == Layer.OPENED ? Layer.PINNED : Layer.OPENED;
        rebuildRing();
        relightPending = true;
        relightAnchorX = lastRawX;
        relightAnchorY = lastRawY;
        if (ball != null) {
            ball.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void execute(int index) {
        if (index < 0 || index >= ringItems.size()) {
            collapse();
            return;
        }
        FloatingBallItem item = ringItems.get(index);
        Log.d(TAG, "execute " + item.kind + (item.app == null ? "" : " " + item.app.label)
                + " slot=" + item.slot);
        switch (item.kind) {
            case SWITCH:
                switchLayer();
                state = State.LOCKED;
                setOverlayInteractive(true);
                scheduleLockTimeout();
                break;
            case OPENED_APP:
                collapse();
                callbacks.onFocusSlot(item.slot);
                break;
            case PINNED_APP:
                collapse();
                callbacks.onOpenApp(item.app);
                break;
            case ADD:
                collapse();
                callbacks.onOpenPicker();
                break;
            default:
                collapse();
                break;
        }
    }

    // ------------------------------------------------------------- locked mode

    private boolean onOverlayTouch(FloatingBallOverlayView view, MotionEvent event) {
        if (state != State.LOCKED) {
            return false;
        }
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                lastRawX = rawX;
                lastRawY = rawY;
                updateHighlightAt(rawX, rawY);
                resetLockTimeout();
                return true;
            case MotionEvent.ACTION_UP:
                lastRawX = rawX;
                lastRawY = rawY;
                updateHighlightAt(rawX, rawY);
                if (highlightIndex >= 0) {
                    execute(highlightIndex);
                } else {
                    collapse();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                collapse();
                return true;
            default:
                return true;
        }
    }

    private void setOverlayInteractive(boolean interactive) {
        if (overlay == null) {
            return;
        }
        overlay.setInteractive(interactive);
        if (overlayParams == null) {
            return;
        }
        if (interactive) {
            overlayParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        if (overlayWindowAttached) {
            try {
                activity.getWindowManager().updateViewLayout(overlay, overlayParams);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void scheduleLockTimeout() {
        cancelLockTimeout();
        lockTimeoutRunnable = () -> {
            lockTimeoutRunnable = null;
            if (state == State.LOCKED) {
                collapse();
            }
        };
        if (ball != null) {
            ball.postDelayed(lockTimeoutRunnable,
                    FloatingBallGesturePolicy.LOCKED_AUTO_COLLAPSE_MS);
        }
    }

    private void resetLockTimeout() {
        if (state == State.LOCKED) {
            scheduleLockTimeout();
        }
    }

    private void cancelLockTimeout() {
        if (lockTimeoutRunnable != null && ball != null) {
            ball.removeCallbacks(lockTimeoutRunnable);
        }
        lockTimeoutRunnable = null;
    }

    // ------------------------------------------------------------- positioning

    private void applyBallPosition() {
        if (ballParams == null) {
            return;
        }
        if (docked) {
            setBallWindowPosition(dockedLeft(), ballTop(), false);
        } else {
            setBallWindowPosition(fullVisibleLeft(), ballTop(), false);
        }
    }

    /** Left edge of the ball window while docked: half of it hangs off screen. */
    private int dockedLeft() {
        DisplayMetrics metrics = screenMetrics();
        return dockedRight ? metrics.widthPixels - ballSizePx / 2 : -ballSizePx / 2;
    }

    /** Left edge of the ball window while fully visible, pinned to its side. */
    private int fullVisibleLeft() {
        DisplayMetrics metrics = screenMetrics();
        int margin = dp(BALL_EDGE_MARGIN_DP);
        return dockedRight
                ? Math.max(0, metrics.widthPixels - ballSizePx - margin) : margin;
    }

    private int ballTop() {
        DisplayMetrics metrics = screenMetrics();
        int half = ballSizePx / 2;
        int centerY = Math.round(positionYRatio * metrics.heightPixels);
        centerY = clampInt(centerY, half + dp(BALL_EDGE_MARGIN_DP),
                metrics.heightPixels - half - dp(BALL_EDGE_MARGIN_DP));
        return centerY - half;
    }

    private void setBallWindowPosition(int left, int top, boolean animate) {
        if (ballParams == null) {
            return;
        }
        int[] parent = new int[2];
        parentLocationOnScreen(parent);
        int targetX = left - parent[0];
        int targetY = top - parent[1];
        if (!animate || !ballWindowAttached) {
            ballParams.x = targetX;
            ballParams.y = targetY;
            updateBallLayout();
            return;
        }
        animateBallTo(targetX, targetY);
    }

    private void updateBallLayout() {
        if (!ballWindowAttached || ballParams == null) {
            return;
        }
        try {
            activity.getWindowManager().updateViewLayout(ball, ballParams);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void animateBallTo(int targetX, int targetY) {
        cancelDockAnimation();
        int fromX = ballParams.x;
        int fromY = ballParams.y;
        if (fromX == targetX && fromY == targetY) {
            return;
        }
        dockAnimator = ValueAnimator.ofFloat(0f, 1f);
        dockAnimator.setDuration(DOCK_ANIM_MS);
        dockAnimator.setInterpolator(new DecelerateInterpolator());
        dockAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            ballParams.x = Math.round(fromX + (targetX - fromX) * fraction);
            ballParams.y = Math.round(fromY + (targetY - fromY) * fraction);
            updateBallLayout();
        });
        dockAnimator.start();
    }

    private void cancelDockAnimation() {
        if (dockAnimator != null) {
            dockAnimator.cancel();
            dockAnimator = null;
        }
    }

    /** Slides the ball to the nearest side; called when a drag ends. */
    private void snapToSide() {
        docked = false;
        setBallWindowPosition(fullVisibleLeft(), ballTop(), true);
    }

    /** Pulls the ball back to half-hidden whenever the user stops interacting. */
    private void scheduleAutoDock() {
        if (autoDockRunnable != null || !ballWindowAttached || !attached || suppressed
                || docked || !callbacks.isEnabled()) {
            return;
        }
        autoDockRunnable = () -> {
            autoDockRunnable = null;
            if (state == State.IDLE && !suppressed && ballWindowAttached) {
                docked = true;
                setBallWindowPosition(dockedLeft(), ballTop(), true);
            }
        };
        activity.getWindow().getDecorView().postDelayed(autoDockRunnable, AUTO_DOCK_DELAY_MS);
    }

    private void cancelAutoDock() {
        if (autoDockRunnable != null) {
            activity.getWindow().getDecorView().removeCallbacks(autoDockRunnable);
        }
        autoDockRunnable = null;
    }

    /** Brings a docked ball fully on screen as soon as the user touches it. */
    private void undock() {
        cancelAutoDock();
        cancelDockAnimation();
        docked = false;
        setBallWindowPosition(fullVisibleLeft(), ballTop(), false);
    }

    private void moveBallTo(float rawX, float rawY) {
        if (ballParams == null || !ballWindowAttached) {
            return;
        }
        DisplayMetrics metrics = screenMetrics();
        int half = ballSizePx / 2;
        int[] parent = new int[2];
        parentLocationOnScreen(parent);
        int left = Math.round(rawX) - half;
        int top = Math.round(rawY) - half;
        left = clampInt(left, 0, Math.max(0, metrics.widthPixels - ballSizePx));
        top = clampInt(top, dp(BALL_EDGE_MARGIN_DP),
                Math.max(0, metrics.heightPixels - ballSizePx - dp(BALL_EDGE_MARGIN_DP)));
        ballParams.x = left - parent[0];
        ballParams.y = top - parent[1];
        updateBallLayout();
    }

    private void finishDrag() {
        state = State.IDLE;
        if (ball != null) {
            ball.setPressed(false);
        }
        DisplayMetrics metrics = screenMetrics();
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0 || ballParams == null) {
            return;
        }
        int[] parent = new int[2];
        parentLocationOnScreen(parent);
        float centerX = ballParams.x + parent[0] + ballSizePx / 2f;
        float centerY = ballParams.y + parent[1] + ballSizePx / 2f;
        dockedRight = centerX >= metrics.widthPixels / 2f;
        positionYRatio = clampRatio(centerY / metrics.heightPixels);
        callbacks.onPositionChanged(dockedRight ? 1f : 0f, positionYRatio);
        snapToSide();
        scheduleAutoDock();
    }

    private static int clampInt(int value, int min, int max) {
        if (max < min) {
            return (min + max) / 2;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static float clampRatio(float value) {
        if (Float.isNaN(value)) {
            return 0.5f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private int dp(float value) {
        return Math.round(value * density);
    }
}
