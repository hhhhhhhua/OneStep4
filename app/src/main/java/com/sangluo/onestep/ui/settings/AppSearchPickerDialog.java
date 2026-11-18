package com.sangluo.onestep.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sangluo.onestep.data.apps.AppSearchPolicy;
import com.sangluo.onestep.data.apps.PinyinTransliterator;
import com.sangluo.onestep.model.LauncherApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Multi-select app picker with fuzzy search: matches on the visible name, the pinyin
 * of a Chinese name, the pinyin initials, and the package name.
 */
public final class AppSearchPickerDialog {
    private static final int DIALOG_HEIGHT_DP = 620;
    private static final int DIALOG_TOP_MARGIN_DP = 24;
    private static final int ICON_SIZE_DP = 40;
    // Matches SettingsPanelController.showRoundedDialog: light panel, dark text.
    private static final int PANEL_COLOR = 0xffffffff;
    private static final int PANEL_STROKE_COLOR = 0x1f000000;
    private static final int TITLE_COLOR = 0xde000000;
    private static final int LABEL_COLOR = 0xde000000;
    private static final int SUBTITLE_COLOR = 0x99000000;
    private static final int ACTION_COLOR = 0xff1976d2;
    private static final int SELECTED_ROW_COLOR = 0x141976d2;

    public interface Listener {
        /** Keys in the order the user selected them. */
        void onAppsSelected(List<String> orderedKeys);
    }

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create("sans-serif-medium", Typeface.BOLD);

    private final Context context;
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> visibleRows = new ArrayList<>();
    private final Map<String, Row> rowsByKey = new HashMap<>();
    private final LinkedHashSet<String> selectedKeys = new LinkedHashSet<>();
    private final RowAdapter adapter = new RowAdapter();
    private final int maxCount;
    private final Listener listener;
    private final Runnable onDismissed;

    private String query = "";
    private TextView counterView;
    private TextView hintView;
    private TextView emptyView;
    private AlertDialog dialog;

    private AppSearchPickerDialog(Context context, List<LauncherApp> candidates,
                                  List<String> selectedKeys, int maxCount,
                                  Listener listener, Runnable onDismissed) {
        this.context = context;
        this.maxCount = Math.max(1, maxCount);
        this.listener = listener;
        this.onDismissed = onDismissed;
        if (selectedKeys != null) {
            for (String key : selectedKeys) {
                if (!TextUtils.isEmpty(key)) {
                    this.selectedKeys.add(key);
                }
            }
        }
        if (candidates != null) {
            for (LauncherApp app : candidates) {
                if (app == null) {
                    continue;
                }
                Row row = new Row(app);
                row.checked = this.selectedKeys.contains(app.instanceKey());
                rows.add(row);
                rowsByKey.put(app.instanceKey(), row);
            }
        }
        rebuildVisibleRows();
    }

    public static void show(Context context, String title, List<LauncherApp> candidates,
                            List<String> selectedKeys, int maxCount,
                            Listener listener, Runnable onDismissed) {
        if (context == null || candidates == null || candidates.isEmpty()) {
            Toast.makeText(context, "未找到可添加的应用", Toast.LENGTH_SHORT).show();
            if (onDismissed != null) {
                onDismissed.run();
            }
            return;
        }
        new AppSearchPickerDialog(context, candidates, selectedKeys, maxCount, listener,
                onDismissed).show(title);
    }

    private void show(String title) {
        LinearLayout content = buildContent(title);
        dialog = new AlertDialog.Builder(context)
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialogInterface, which) -> {
                    if (listener != null) {
                        listener.onAppsSelected(new ArrayList<>(selectedKeys));
                    }
                })
                .create();
        dialog.setOnDismissListener(dialogInterface -> {
            hideKeyboard();
            if (onDismissed != null) {
                onDismissed.run();
            }
        });
        rebuildVisibleRows();
        dialog.show();
        applyWindowLayout();
        updateCounter();
    }

    /**
     * Pins the dialog to the top edge and disables IME-driven resizing: on some ROMs the
     * resize path re-centres the dialog and visibly shifts it to the right while the
     * keyboard is open.
     */
    private void applyWindowLayout() {
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) {
            return;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int height = Math.min(dp(DIALOG_HEIGHT_DP),
                Math.round(metrics.heightPixels * 0.55f));
        WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = height;
        params.y = dp(DIALOG_TOP_MARGIN_DP);
        window.setAttributes(params);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        // The app theme is DayNight, so the platform dialog may come up light while the
        // content was styled for a dark surface (or vice versa). Pin both explicitly.
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        View decorView = window.getDecorView();
        decorView.setBackground(createPanelBackground());
        decorView.setClipToOutline(true);
        decorView.setElevation(dp(8));

        TextView positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(ACTION_COLOR);
            positive.setTypeface(BOLD_TYPEFACE);
        }
        TextView negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(SUBTITLE_COLOR);
            negative.setTypeface(BOLD_TYPEFACE);
        }
    }

    private GradientDrawable createPanelBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(PANEL_COLOR);
        background.setStroke(dp(1), PANEL_STROKE_COLOR);
        background.setCornerRadius(dp(12));
        return background;
    }

    private LinearLayout buildContent(String title) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));

        if (!TextUtils.isEmpty(title)) {
            TextView titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            titleView.setTextColor(TITLE_COLOR);
            titleView.setTypeface(BOLD_TYPEFACE);
            titleView.setPadding(0, dp(8), 0, dp(4));
            root.addView(titleView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint("搜索应用名 / 拼音 / 首字母");
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setTypeface(BOLD_TYPEFACE);
        search.setHintTextColor(SUBTITLE_COLOR);
        search.setTextColor(LABEL_COLOR);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                applyQuery(editable == null ? "" : editable.toString());
            }
        });
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        counterView = new TextView(context);
        counterView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        counterView.setTextColor(SUBTITLE_COLOR);
        counterView.setTypeface(BOLD_TYPEFACE);
        header.addView(counterView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clearSelection = new TextView(context);
        clearSelection.setText("清空");
        clearSelection.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        clearSelection.setTextColor(ACTION_COLOR);
        clearSelection.setTypeface(BOLD_TYPEFACE);
        clearSelection.setPadding(dp(8), dp(4), dp(8), dp(4));
        clearSelection.setOnClickListener(view -> {
            for (Row row : rows) {
                row.checked = false;
            }
            selectedKeys.clear();
            rebuildVisibleRows();
            updateCounter();
        });
        header.addView(clearSelection);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = dp(4);
        root.addView(header, headerParams);

        hintView = new TextView(context);
        hintView.setText("已选应用排在最前面，点击任意一行可勾选 / 取消勾选");
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hintView.setTextColor(SUBTITLE_COLOR);
        hintView.setTypeface(BOLD_TYPEFACE);
        hintView.setVisibility(View.GONE);
        root.addView(hintView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyView = new TextView(context);
        emptyView.setText("没有匹配的应用");
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        emptyView.setTextColor(SUBTITLE_COLOR);
        emptyView.setTypeface(BOLD_TYPEFACE);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.topMargin = dp(4);
        root.addView(list, listParams);
        return root;
    }

    private void applyQuery(String query) {
        this.query = query == null ? "" : query;
        rebuildVisibleRows();
        updateCounter();
    }

    /**
     * Selected apps always come first (in selection order), then the rest alphabetically.
     * While searching the ranking wins, with selected matches ahead of equal scores.
     */
    private void rebuildVisibleRows() {
        visibleRows.clear();
        if (TextUtils.isEmpty(query) || query.trim().isEmpty()) {
            for (String key : selectedKeys) {
                Row row = rowsByKey.get(key);
                if (row != null) {
                    row.checked = true;
                    visibleRows.add(row);
                }
            }
            List<Row> remaining = new ArrayList<>(rows.size());
            for (Row row : rows) {
                if (!selectedKeys.contains(row.app.instanceKey())) {
                    row.checked = false;
                    remaining.add(row);
                }
            }
            Collections.sort(remaining, new Comparator<Row>() {
                @Override
                public int compare(Row first, Row second) {
                    return String.CASE_INSENSITIVE_ORDER.compare(
                            first.displayName(), second.displayName());
                }
            });
            visibleRows.addAll(remaining);
            adapter.notifyDataSetChanged();
            updateEmptyState();
            return;
        }

        List<ScoredRow> scored = new ArrayList<>();
        for (Row row : rows) {
            row.resolveKeys();
            int score = AppSearchPolicy.score(query, row.app.label, row.pinyin,
                    row.initials, row.app.packageName);
            if (score > AppSearchPolicy.NO_MATCH) {
                scored.add(new ScoredRow(row, score));
            }
        }
        Collections.sort(scored, new Comparator<ScoredRow>() {
            @Override
            public int compare(ScoredRow first, ScoredRow second) {
                if (first.score != second.score) {
                    return Integer.compare(second.score, first.score);
                }
                if (first.row.checked != second.row.checked) {
                    return first.row.checked ? -1 : 1;
                }
                return String.CASE_INSENSITIVE_ORDER.compare(
                        first.row.displayName(), second.row.displayName());
            }
        });
        for (ScoredRow entry : scored) {
            visibleRows.add(entry.row);
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    /** Safe before the content view exists (the constructor builds the first ordering). */
    private void updateEmptyState() {
        if (emptyView == null) {
            return;
        }
        emptyView.setVisibility(visibleRows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toggle(Row row, boolean checked) {
        if (checked) {
            String key = row.app.instanceKey();
            if (!selectedKeys.contains(key) && selectedKeys.size() >= maxCount) {
                row.checked = false;
                Toast.makeText(context, "最多只能选择 " + maxCount + " 个应用",
                        Toast.LENGTH_SHORT).show();
                int index = visibleRows.indexOf(row);
                if (index >= 0) {
                    adapter.notifyItemChanged(index);
                }
                return;
            }
            selectedKeys.add(key);
        } else {
            selectedKeys.remove(row.app.instanceKey());
        }
        row.checked = checked;
        int position = visibleRows.indexOf(row);
        if (position >= 0) {
            adapter.notifyItemChanged(position);
        }
        updateCounter();
    }

    private void updateCounter() {
        if (counterView != null) {
            counterView.setText("已选 " + selectedKeys.size() + "/" + maxCount);
        }
        if (hintView != null) {
            hintView.setVisibility(selectedKeys.isEmpty() ? View.GONE : View.VISIBLE);
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

    private static final class ScoredRow {
        final Row row;
        final int score;

        ScoredRow(Row row, int score) {
            this.row = row;
            this.score = score;
        }
    }

    private static final class Row {
        final LauncherApp app;
        boolean checked;
        String pinyin;
        String initials;
        private boolean keysResolved;

        Row(LauncherApp app) {
            this.app = app;
        }

        void resolveKeys() {
            if (keysResolved) {
                return;
            }
            keysResolved = true;
            pinyin = PinyinTransliterator.toPinyin(app.label);
            initials = PinyinTransliterator.initials(app.label);
        }

        String displayName() {
            return app.label == null ? app.packageName : app.label;
        }
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowHolder> {
        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageView icon = new ImageView(context);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    dp(ICON_SIZE_DP), dp(ICON_SIZE_DP));
            iconParams.setMarginEnd(dp(12));
            row.addView(icon, iconParams);

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(context);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            label.setTextColor(LABEL_COLOR);
            label.setTypeface(BOLD_TYPEFACE);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(label);
            TextView subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subtitle.setTextColor(SUBTITLE_COLOR);
            subtitle.setTypeface(BOLD_TYPEFACE);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setVisibility(View.GONE);
            texts.addView(subtitle);
            row.addView(texts, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            CheckBox check = new CheckBox(context);
            check.setClickable(false);
            check.setFocusable(false);
            row.addView(check);

            RowHolder holder = new RowHolder(row, icon, label, subtitle, check);
            row.setOnClickListener(view -> {
                int position = holder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION
                        || position >= visibleRows.size()) {
                    return;
                }
                Row item = visibleRows.get(position);
                boolean next = !item.checked;
                toggle(item, next);
                check.setChecked(item.checked);
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            Row row = visibleRows.get(position);
            holder.label.setText(row.displayName());
            holder.icon.setImageDrawable(row.app.icon);
            holder.check.setChecked(row.checked);
            holder.itemView.setBackgroundColor(row.checked
                    ? SELECTED_ROW_COLOR : 0x00000000);
            row.resolveKeys();
            if (!TextUtils.isEmpty(row.pinyin)
                    && !row.pinyin.equalsIgnoreCase(row.displayName())) {
                holder.subtitle.setText(row.pinyin);
                holder.subtitle.setVisibility(View.VISIBLE);
            } else {
                holder.subtitle.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return visibleRows.size();
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView subtitle;
        final CheckBox check;

        RowHolder(@NonNull View itemView, ImageView icon, TextView label,
                  TextView subtitle, CheckBox check) {
            super(itemView);
            this.icon = icon;
            this.label = label;
            this.subtitle = subtitle;
            this.check = check;
        }
    }
}
