package com.sangluo.onestep.ui.audio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sangluo.onestep.feature.audio.AudioOutputController;
import com.sangluo.onestep.feature.audio.AudioOutputDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Sound-output picker for the top bar: list the phone's audio outputs, switch the media
 * route and pin the current choice.
 */
public final class AudioOutputDialog {
    private static final int PANEL_COLOR = 0xf21b1f24;
    private static final int PANEL_STROKE_COLOR = 0x33ffffff;
    private static final int TEXT_PRIMARY = 0xffffffff;
    private static final int TEXT_SECONDARY = 0xffb9c2cc;
    private static final int TEXT_TERTIARY = 0xff8a94a0;
    private static final int ROW_FILL = 0x12ffffff;
    private static final int ROW_FILL_SELECTED = 0x244f8cff;
    private static final int ROW_STROKE_SELECTED = 0x664f8cff;
    private static final int ACCENT = 0xff4f8cff;
    private static final int WARNING = 0xffffb020;
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create("sans-serif-medium", Typeface.BOLD);

    private final Activity activity;
    private final AudioOutputController controller;
    private final AudioOutputController.Listener listener = this::rebuild;

    private AlertDialog dialog;
    private LinearLayout deviceList;
    private TextView pinStateView;
    private TextView statusView;
    private final List<AudioOutputDevice> devices = new ArrayList<>();
    private String selectedKey;
    private boolean pinSelected;
    private boolean commandBusy;
    private boolean registered;

    private AudioOutputDialog(Activity activity, AudioOutputController controller) {
        this.activity = activity;
        this.controller = controller;
    }

    public static void show(Activity activity, AudioOutputController controller) {
        if (activity == null || controller == null || activity.isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
            return;
        }
        new AudioOutputDialog(activity, controller).show();
    }

    private void show() {
        LinearLayout content = buildContent();
        dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .create();
        dialog.setOnDismissListener(dialogInterface -> {
            if (registered) {
                controller.removeListener(listener);
                registered = false;
            }
        });
        controller.addListener(listener);
        registered = true;
        dialog.show();
        styleWindow();
        rebuild();
    }

    private void styleWindow() {
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = Math.min(Math.round(metrics.widthPixels * 0.92f), dp(440));
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
        View decor = window.getDecorView();
        decor.setBackground(panelBackground());
        decor.setClipToOutline(true);
        decor.setElevation(dp(10));
    }

    private GradientDrawable panelBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(PANEL_COLOR);
        background.setStroke(dp(1), PANEL_STROKE_COLOR);
        background.setCornerRadius(dp(18));
        return background;
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView title = new TextView(activity);
        title.setText("声音输出");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(BOLD_TYPEFACE);
        root.addView(title, matchWrap());

        TextView hint = new TextView(activity);
        hint.setText("选择媒体声音的输出设备；固定后系统自动切换会被拒绝。");
        hint.setTextColor(TEXT_SECONDARY);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams hintLp = matchWrap();
        hintLp.topMargin = dp(4);
        root.addView(hint, hintLp);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        deviceList = new LinearLayout(activity);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(deviceList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = matchWrap();
        scrollLp.topMargin = dp(12);
        root.addView(scroll, scrollLp);

        statusView = new TextView(activity);
        statusView.setTextColor(WARNING);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        statusView.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(8);
        root.addView(statusView, statusLp);

        root.addView(buildPinRow(), pinRowParams());
        return root;
    }

    private LinearLayout buildPinRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundedBackground(ROW_FILL, 0));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> onPinClicked());

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText("固定所选设备");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(BOLD_TYPEFACE);
        labels.addView(title, matchWrap());
        TextView subtitle = new TextView(activity);
        subtitle.setText("拒绝系统自动切换；设备断开时自动放行");
        subtitle.setTextColor(TEXT_TERTIARY);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        labels.addView(subtitle, matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pinStateView = new TextView(activity);
        pinStateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pinStateView.setTypeface(BOLD_TYPEFACE);
        pinStateView.setGravity(Gravity.CENTER);
        pinStateView.setPadding(dp(12), dp(5), dp(12), dp(5));
        row.addView(pinStateView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout.LayoutParams pinRowParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(12);
        return params;
    }

    // ------------------------------------------------------------------ rebuild

    private void rebuild() {
        if (dialog == null || !dialog.isShowing() || deviceList == null) {
            return;
        }
        devices.clear();
        devices.addAll(controller.connectedOutputs());
        AudioOutputDevice pinned = controller.getPinnedOutput();
        AudioOutputDevice current = controller.currentOutput();
        pinSelected = pinned != null;
        if (pinned != null && containsKey(pinned.key())) {
            selectedKey = pinned.key();
        } else if (!pinSelected) {
            if (current != null && containsKey(current.key())) {
                selectedKey = current.key();
            } else if (!containsKey(selectedKey)) {
                selectedKey = devices.isEmpty() ? null : devices.get(0).key();
            }
        }

        deviceList.removeAllViews();
        if (devices.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("没有检测到可选择的音频输出");
            empty.setTextColor(TEXT_TERTIARY);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            deviceList.addView(empty, matchWrap());
        } else {
            for (AudioOutputDevice device : devices) {
                deviceList.addView(buildDeviceRow(device, current));
            }
        }
        updatePinView();
        updateStatusView(pinned);
    }

    private View buildDeviceRow(AudioOutputDevice device, AudioOutputDevice current) {
        boolean selected = device.matchesKey(selectedKey);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundedBackground(selected ? ROW_FILL_SELECTED : ROW_FILL,
                selected ? ROW_STROKE_SELECTED : 0));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> onDeviceClicked(device));

        TextView radio = new TextView(activity);
        radio.setText(selected ? "●" : "○");
        radio.setTextColor(selected ? ACCENT : TEXT_TERTIARY);
        radio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        radio.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams radioLp = new LinearLayout.LayoutParams(dp(20),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        radioLp.rightMargin = dp(10);
        row.addView(radio, radioLp);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(activity);
        name.setText(device.name);
        name.setTextColor(TEXT_PRIMARY);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name, matchWrap());
        TextView subtitle = new TextView(activity);
        subtitle.setText(device.subtitle());
        subtitle.setTextColor(TEXT_TERTIARY);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(subtitle, matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (current != null && device.matchesKey(current.key())) {
            TextView badge = new TextView(activity);
            badge.setText("当前");
            badge.setTextColor(ACCENT);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            badge.setTypeface(BOLD_TYPEFACE);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            badge.setBackground(roundedBackground(0x244f8cff, 0x554f8cff));
            row.addView(badge, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout.LayoutParams rowLp = matchWrap();
        rowLp.bottomMargin = dp(6);
        row.setLayoutParams(rowLp);
        return row;
    }

    private void updatePinView() {
        if (pinStateView == null) {
            return;
        }
        pinStateView.setText(pinSelected ? "已固定" : "未固定");
        pinStateView.setTextColor(pinSelected ? TEXT_PRIMARY : TEXT_TERTIARY);
        pinStateView.setBackground(roundedBackground(
                pinSelected ? 0xff2f6fe0 : 0x1affffff, pinSelected ? 0 : 0x33ffffff));
    }

    private void updateStatusView(AudioOutputDevice pinned) {
        if (statusView == null) {
            return;
        }
        String message = null;
        if (pinned != null && !containsKey(pinned.key())) {
            message = "已固定的 " + pinned.name + " 未连接，系统默认输出已放行；重新连接后会自动切回。";
        } else if (Boolean.FALSE.equals(controller.isCommandSupported())) {
            message = "当前系统不支持声音输出切换（cmd audio 不可用）。";
        }
        statusView.setText(message);
        statusView.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    // -------------------------------------------------------------------- taps

    private void onDeviceClicked(AudioOutputDevice device) {
        if (commandBusy) {
            Toast.makeText(activity, "正在切换音频设备…", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedKey = device.key();
        rebuild();
        setBusy(true);
        AudioOutputController.ResultCallback callback = this::onCommandResult;
        if (pinSelected) {
            controller.pin(device, callback);
        } else {
            controller.selectOnce(device, callback);
        }
    }

    private void onPinClicked() {
        if (commandBusy) {
            Toast.makeText(activity, "正在切换音频设备…", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true);
        AudioOutputController.ResultCallback callback = this::onCommandResult;
        if (pinSelected) {
            controller.unpin(callback);
            return;
        }
        AudioOutputDevice selected = findSelected();
        if (selected == null) {
            setBusy(false);
            Toast.makeText(activity, "请先选择一个音频设备", Toast.LENGTH_SHORT).show();
            return;
        }
        controller.pin(selected, callback);
    }

    private void onCommandResult(boolean success, String message) {
        setBusy(false);
        if (!activity.isFinishing()) {
            Toast.makeText(activity, message, success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG)
                    .show();
        }
        rebuild();
    }

    private void setBusy(boolean busy) {
        commandBusy = busy;
    }

    // ----------------------------------------------------------------- helpers

    private AudioOutputDevice findSelected() {
        for (AudioOutputDevice device : devices) {
            if (device.matchesKey(selectedKey)) {
                return device;
            }
        }
        return null;
    }

    private boolean containsKey(String key) {
        if (key == null) {
            return false;
        }
        for (AudioOutputDevice device : devices) {
            if (device.matchesKey(key)) {
                return true;
            }
        }
        return false;
    }

    private GradientDrawable roundedBackground(int fill, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(fill);
        background.setCornerRadius(dp(12));
        if (stroke != 0) {
            background.setStroke(dp(1), stroke);
        }
        return background;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
