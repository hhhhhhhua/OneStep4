package com.sangluo.onestep.feature.screenshot;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Hosts the screenshot editor window, owns the text input panel and hands finished images
 * to the clipboard or gallery. The host Activity keeps the floating ball hidden until
 * {@link Listener#onClosed()} fires.
 */
public final class ScreenshotController implements ScreenshotEditorView.Listener {
    public interface Listener {
        void onClosed();
    }

    private static final String TAG = "OneStepScreenshot";
    private static final float INPUT_PANEL_WIDTH_DP = 300f;
    private static final float INPUT_PANEL_TOP_DP = 32f;

    private final Activity activity;
    private final Bitmap source;
    private final int displayId;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DisplayManager displayManager;
    private final DisplayManager.DisplayListener displayListener;

    private final EditorRootLayout root;
    private final ScreenshotEditorView editorView;
    private final float displayScale;
    private final int initialWidth;
    private final int initialHeight;

    private boolean showing;
    private boolean closed;
    private volatile boolean exportInFlight;

    private LinearLayout inputPanel;
    private EditText inputField;
    private float pendingTextX;
    private float pendingTextY;

    public ScreenshotController(Activity activity, Bitmap source, int displayId,
                                float[] mainSlotBounds, Listener listener) {
        this.activity = activity;
        this.source = source;
        this.displayId = displayId;
        this.listener = listener;
        DisplayMetrics metrics = screenMetrics();
        this.initialWidth = metrics.widthPixels;
        this.initialHeight = metrics.heightPixels;
        this.displayScale = source.getWidth() <= 0 || initialWidth <= 0
                ? 1f : source.getWidth() / (float) initialWidth;
        this.root = new EditorRootLayout(activity);
        this.root.setBackHandler(this::handleBack);
        this.root.setFocusableInTouchMode(true);
        this.root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        this.editorView = new ScreenshotEditorView(activity, source, displayScale);
        this.editorView.setListener(this);
        this.editorView.setMainSlotBounds(mainSlotBounds);
        root.addView(editorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.displayManager =
                (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        this.displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int addedDisplayId) {
            }

            @Override
            public void onDisplayRemoved(int removedDisplayId) {
            }

            @Override
            public void onDisplayChanged(int changedDisplayId) {
                if (changedDisplayId == ScreenshotController.this.displayId) {
                    mainHandler.post(ScreenshotController.this::closeOnDisplayChange);
                }
            }
        };
    }

    /**
     * Installs the editor as the hosting activity's content view. A real activity window
     * (instead of a system overlay) is required for the IME to attach reliably on
     * secondary displays.
     */
    public boolean show() {
        if (closed || showing) {
            return false;
        }
        try {
            activity.setContentView(root);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to install screenshot editor content view", error);
            return false;
        }
        showing = true;
        if (displayManager != null) {
            displayManager.registerDisplayListener(displayListener, mainHandler);
        }
        root.requestFocus();
        return true;
    }

    public boolean isShowing() {
        return !closed && showing;
    }

    private DisplayMetrics screenMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        return metrics;
    }

    private void closeOnDisplayChange() {
        if (closed) {
            return;
        }
        DisplayMetrics metrics = screenMetrics();
        if (metrics.widthPixels != initialWidth || metrics.heightPixels != initialHeight) {
            Toast.makeText(activity, "屏幕尺寸已变化，已退出截图", Toast.LENGTH_SHORT).show();
            close();
        }
    }

    public boolean handleBack() {
        if (inputPanel != null) {
            hideTextPanel();
            return true;
        }
        close();
        return true;
    }

    @Override
    public void onCloseRequested() {
        close();
    }

    @Override
    public void onCopyRequested() {
        export(false);
    }

    @Override
    public void onSaveRequested() {
        export(true);
    }

    @Override
    public void onTextRequested(float x, float y) {
        if (closed) {
            return;
        }
        pendingTextX = x;
        pendingTextY = y;
        showTextPanel();
    }

    // -------------------------------------------------------------- text input

    private void showTextPanel() {
        hideTextPanel();
        editorView.setInputBlocked(true);
        inputPanel = new LinearLayout(activity);
        inputPanel.setOrientation(LinearLayout.VERTICAL);
        inputPanel.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xe61f2328);
        background.setCornerRadius(dp(14));
        background.setStroke(Math.round(dp(1)), 0x33ffffff);
        inputPanel.setBackground(background);

        inputField = new EditText(activity);
        inputField.setHint("输入文字");
        inputField.setHintTextColor(0x80ffffff);
        inputField.setTextColor(Color.WHITE);
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        inputField.setBackground(null);
        inputField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        inputField.setMaxLines(4);
        inputField.setMinLines(1);
        inputField.setPadding(0, 0, 0, 0);
        inputPanel.addView(inputField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        TextView cancel = actionButton("取消", 0xccffffff);
        TextView confirm = actionButton("确定", 0xff4f8cff);
        cancel.setOnClickListener(view -> hideTextPanel());
        confirm.setOnClickListener(view -> commitText());
        actions.addView(cancel, actionParams());
        actions.addView(confirm, actionParams());
        inputPanel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        DisplayMetrics metrics = screenMetrics();
        int width = Math.min(Math.round(dp(INPUT_PANEL_WIDTH_DP)), metrics.widthPixels - dp(32));
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        layout.topMargin = Math.max(dp(INPUT_PANEL_TOP_DP), dp(16));
        root.addView(inputPanel, layout);

        inputField.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            inputField.postDelayed(() -> imm.showSoftInput(inputField,
                    InputMethodManager.SHOW_IMPLICIT), 120L);
            // Some secondary displays ignore the first implicit request.
            inputField.postDelayed(() -> {
                if (inputField != null && !imm.isActive(inputField)) {
                    imm.showSoftInput(inputField, InputMethodManager.SHOW_FORCED);
                }
            }, 420L);
        }
    }

    private TextView actionButton(String text, int color) {
        TextView button = new TextView(activity);
        button.setText(text);
        button.setTextColor(color);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(4);
        return params;
    }

    private void commitText() {
        if (inputField == null) {
            return;
        }
        String text = inputField.getText().toString();
        hideTextPanel();
        editorView.addText(pendingTextX, pendingTextY, text);
    }

    private void hideTextPanel() {
        if (inputField != null) {
            InputMethodManager imm =
                    (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(inputField.getWindowToken(), 0);
            }
        }
        if (inputPanel != null) {
            root.removeView(inputPanel);
        }
        inputPanel = null;
        inputField = null;
        editorView.setInputBlocked(false);
        root.requestFocus();
    }

    // ------------------------------------------------------------------ export

    private void export(boolean toGallery) {
        if (closed || exportInFlight) {
            return;
        }
        hideTextPanel();
        exportInFlight = true;
        editorView.setExporting(true);
        final float[] selection = editorView.getSelection();
        final List<ScreenshotAnnotation> annotations =
                new ArrayList<>(editorView.getAnnotations());
        final float scale = editorView.getDisplayScale();
        final Bitmap capture = source;
        new Thread(() -> {
            Bitmap output = ScreenshotCompositor.render(capture, selection, annotations, scale);
            boolean success = false;
            if (output != null) {
                success = toGallery
                        ? ScreenshotExportClient.saveToGallery(activity, output)
                        : ScreenshotExportClient.copyToClipboard(activity, output);
                output.recycle();
            }
            final boolean result = success;
            mainHandler.post(() -> {
                exportInFlight = false;
                if (closed) {
                    recycleSourceIfIdle();
                    return;
                }
                editorView.setExporting(false);
                if (result) {
                    Toast.makeText(activity,
                            toGallery ? "已保存到相册" : "已复制到剪贴板",
                            Toast.LENGTH_SHORT).show();
                    close();
                } else {
                    Toast.makeText(activity,
                            toGallery ? "保存失败，请重试" : "复制失败，请重试",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }, "onestep-screenshot-export").start();
    }

    // ------------------------------------------------------------------ close

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (displayManager != null) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (RuntimeException ignored) {
            }
        }
        hideTextPanel();
        root.setBackHandler(null);
        editorView.setListener(null);
        showing = false;
        editorView.release();
        recycleSourceIfIdle();
        if (listener != null) {
            listener.onClosed();
        }
    }

    private void recycleSourceIfIdle() {
        if (exportInFlight) {
            return;
        }
        if (!source.isRecycled()) {
            source.recycle();
        }
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** Root that routes Back to the editor instead of the activity under the overlay. */
    private static final class EditorRootLayout extends FrameLayout {
        interface BackHandler {
            boolean onBack();
        }

        private BackHandler backHandler;

        EditorRootLayout(Context context) {
            super(context);
        }

        void setBackHandler(BackHandler handler) {
            backHandler = handler;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP
                        && backHandler != null && backHandler.onBack()) {
                    return true;
                }
                // Consume both edges so Back never reaches the activity below.
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
    }
}
