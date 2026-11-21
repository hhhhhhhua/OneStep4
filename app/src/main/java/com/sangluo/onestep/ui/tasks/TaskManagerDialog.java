package com.sangluo.onestep.ui.tasks;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sangluo.onestep.data.apps.AppSearchPolicy;
import com.sangluo.onestep.data.apps.PinyinTransliterator;
import com.sangluo.onestep.model.LauncherApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Task-manager dialog: running apps with fuzzy search and per-row open / kill actions.
 */
public final class TaskManagerDialog {
    public interface Listener {
        void onOpen(Entry entry);

        void onKill(Entry entry, KillCallback callback);
    }

    public interface KillCallback {
        void onFinished(boolean success);
    }

    public static final class Entry {
        public final LauncherApp app;
        final String pinyin;
        final String initials;

        public Entry(LauncherApp app) {
            this.app = app;
            this.pinyin = PinyinTransliterator.toPinyin(app.label);
            this.initials = PinyinTransliterator.initials(app.label);
        }
    }

    private static final int DIALOG_HEIGHT_DP = 640;
    private static final int DIALOG_TOP_MARGIN_DP = 24;
    private static final int ICON_SIZE_DP = 40;
    private static final int PANEL_COLOR = 0xf21b1f24;
    private static final int PANEL_STROKE_COLOR = 0x33ffffff;
    private static final int TEXT_PRIMARY = 0xffffffff;
    private static final int TEXT_SECONDARY = 0xffb9c2cc;
    private static final int TEXT_TERTIARY = 0xff8a94a0;
    private static final int SEARCH_FILL = 0x14ffffff;
    private static final int SEARCH_STROKE = 0x33ffffff;
    private static final int ROW_FILL = 0x12ffffff;
    private static final int OPEN_COLOR = 0xff3b82f6;
    private static final int KILL_COLOR = 0xffef4444;

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create("sans-serif-medium", Typeface.BOLD);

    private final Context context;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> visibleEntries = new ArrayList<>();
    private final Listener listener;
    private final Runnable onDismissed;
    private final EntryAdapter adapter = new EntryAdapter();

    private AlertDialog dialog;
    private EditText search;
    private TextView countView;
    private TextView emptyView;
    private String query = "";
    private boolean loading = true;

    private TaskManagerDialog(Context context, Listener listener, Runnable onDismissed) {
        this.context = context;
        this.listener = listener;
        this.onDismissed = onDismissed;
    }

    public static TaskManagerDialog show(Context context, Listener listener,
                                         Runnable onDismissed) {
        if (context == null) {
            if (onDismissed != null) {
                onDismissed.run();
            }
            return null;
        }
        TaskManagerDialog result = new TaskManagerDialog(context, listener, onDismissed);
        return result.show() ? result : null;
    }

    private boolean show() {
        try {
            LinearLayout content = buildContent();
            dialog = new AlertDialog.Builder(context)
                    .setView(content)
                    .create();
            dialog.setOnDismissListener(dialogInterface -> {
                hideKeyboard();
                if (onDismissed != null) {
                    onDismissed.run();
                }
            });
            rebuildVisibleEntries();
            dialog.show();
            applyWindowLayout();
            return true;
        } catch (RuntimeException e) {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
            if (onDismissed != null) {
                onDismissed.run();
            }
            return false;
        }
    }

    /** Fills the dialog after the running-app query finishes. */
    public void setEntries(List<Entry> loadedEntries) {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        entries.clear();
        if (loadedEntries != null) {
            entries.addAll(loadedEntries);
        }
        loading = false;
        rebuildVisibleEntries();
    }

    public void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    private void applyWindowLayout() {
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) {
            return;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int height = Math.min(dp(DIALOG_HEIGHT_DP),
                Math.round(metrics.heightPixels * 0.64f));
        WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = height;
        params.y = dp(DIALOG_TOP_MARGIN_DP);
        window.setAttributes(params);
        // Keep the field pinned while the IME opens; some ROMs re-center the dialog.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        View decorView = window.getDecorView();
        decorView.setBackground(createPanelBackground());
        decorView.setClipToOutline(true);
        decorView.setElevation(dp(10));
    }

    private GradientDrawable createPanelBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(PANEL_COLOR);
        background.setStroke(dp(1), PANEL_STROKE_COLOR);
        background.setCornerRadius(dp(18));
        return background;
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(10));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleColumn = new LinearLayout(context);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setText("任务管理");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(BOLD_TYPEFACE);
        titleColumn.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        countView = new TextView(context);
        countView.setTextColor(TEXT_TERTIARY);
        countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.topMargin = dp(3);
        titleColumn.addView(countView, countLp);
        header.addView(titleColumn);

        TextView close = new TextView(context);
        close.setText("✕");
        close.setTextColor(TEXT_SECONDARY);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭任务管理");
        close.setOnClickListener(view -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));

        search = new EditText(context);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setHint("搜索应用名 / 拼音 / 首字母");
        search.setHintTextColor(TEXT_TERTIARY);
        search.setTextColor(TEXT_PRIMARY);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        search.setBackground(createSearchBackground());
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                query = editable == null ? "" : editable.toString();
                rebuildVisibleEntries();
            }
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = dp(12);
        root.addView(search, searchLp);

        FrameLayout listContainer = new FrameLayout(context);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.topMargin = dp(10);
        root.addView(listContainer, containerLp);

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        list.setClipToPadding(false);
        listContainer.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setTextColor(TEXT_TERTIARY);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        listContainer.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private GradientDrawable createSearchBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(SEARCH_FILL);
        background.setStroke(dp(1), SEARCH_STROKE);
        background.setCornerRadius(dp(12));
        return background;
    }

    private void rebuildVisibleEntries() {
        visibleEntries.clear();
        if (loading) {
            adapter.notifyDataSetChanged();
            if (countView != null) {
                countView.setText("正在读取运行中的应用…");
            }
            if (emptyView != null) {
                emptyView.setText("正在读取运行中的应用…");
                emptyView.setVisibility(View.VISIBLE);
            }
            return;
        }
        boolean searching = !query.trim().isEmpty();
        if (searching) {
            List<ScoredEntry> scored = new ArrayList<>();
            for (Entry entry : entries) {
                int score = AppSearchPolicy.score(query, entry.app.label, entry.pinyin,
                        entry.initials, entry.app.packageName);
                if (score > AppSearchPolicy.NO_MATCH) {
                    scored.add(new ScoredEntry(entry, score));
                }
            }
            Collections.sort(scored, new Comparator<ScoredEntry>() {
                @Override
                public int compare(ScoredEntry first, ScoredEntry second) {
                    if (first.score != second.score) {
                        return Integer.compare(second.score, first.score);
                    }
                    return String.CASE_INSENSITIVE_ORDER.compare(
                            first.entry.app.label, second.entry.app.label);
                }
            });
            for (ScoredEntry entry : scored) {
                visibleEntries.add(entry.entry);
            }
        } else {
            visibleEntries.addAll(entries);
        }
        adapter.notifyDataSetChanged();
        if (countView != null) {
            countView.setText(searching
                    ? "匹配 " + visibleEntries.size() + " / " + entries.size() + " 个应用"
                    : "正在运行 " + entries.size() + " 个应用");
        }
        if (emptyView != null) {
            emptyView.setText(searching ? "没有匹配的应用" : "没有正在运行的应用");
            emptyView.setVisibility(visibleEntries.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }


    private void hideKeyboard() {
        View view = dialog == null ? null : dialog.getCurrentFocus();
        if (view == null || context.getSystemService(Context.INPUT_METHOD_SERVICE) == null) {
            return;
        }
        InputMethodManager manager = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private final class EntryAdapter extends RecyclerView.Adapter<EntryHolder> {
        @NonNull
        @Override
        public EntryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout wrapper = new LinearLayout(context);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            wrapper.setPadding(0, 0, 0, dp(8));

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            GradientDrawable cardBackground = new GradientDrawable();
            cardBackground.setColor(ROW_FILL);
            cardBackground.setCornerRadius(dp(12));
            card.setBackground(cardBackground);
            wrapper.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageView icon = new ImageView(context);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    dp(ICON_SIZE_DP), dp(ICON_SIZE_DP));
            iconLp.setMarginEnd(dp(12));
            card.addView(icon, iconLp);

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            card.addView(textColumn, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView label = new TextView(context);
            label.setTextColor(TEXT_PRIMARY);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            label.setTypeface(BOLD_TYPEFACE);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            textColumn.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView subtitle = new TextView(context);
            subtitle.setText("运行中");
            subtitle.setTextColor(TEXT_TERTIARY);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subtitleLp.topMargin = dp(2);
            textColumn.addView(subtitle, subtitleLp);

            TextView open = createActionButton("打开", OPEN_COLOR);
            open.setContentDescription("打开应用");
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            openLp.setMarginEnd(dp(6));
            card.addView(open, openLp);

            TextView kill = createActionButton("杀死", KILL_COLOR);
            kill.setContentDescription("杀死应用");
            card.addView(kill, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            return new EntryHolder(wrapper, icon, label, open, kill);
        }

        @Override
        public void onBindViewHolder(@NonNull EntryHolder holder, int position) {
            Entry entry = visibleEntries.get(position);
            holder.icon.setImageDrawable(entry.app.icon);
            holder.label.setText(entry.app.label);
            holder.kill.setEnabled(true);
            holder.kill.setAlpha(1f);
            holder.open.setOnClickListener(view -> {
                dismiss();
                if (listener != null) {
                    listener.onOpen(entry);
                }
            });
            holder.kill.setOnClickListener(view -> {
                holder.kill.setEnabled(false);
                holder.kill.setAlpha(0.5f);
                if (listener != null) {
                    listener.onKill(entry, success -> {
                        if (success) {
                            entries.remove(entry);
                            rebuildVisibleEntries();
                        } else {
                            holder.kill.setEnabled(true);
                            holder.kill.setAlpha(1f);
                        }
                    });
                }
            });
        }

        @Override
        public int getItemCount() {
            return visibleEntries.size();
        }
    }

    private TextView createActionButton(String text, int color) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextColor(TEXT_PRIMARY);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(BOLD_TYPEFACE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(18));
        button.setBackground(background);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private static final class EntryHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView open;
        final TextView kill;

        EntryHolder(@NonNull View itemView, ImageView icon, TextView label,
                    TextView open, TextView kill) {
            super(itemView);
            this.icon = icon;
            this.label = label;
            this.open = open;
            this.kill = kill;
        }
    }

    private static final class ScoredEntry {
        final Entry entry;
        final int score;

        ScoredEntry(Entry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
