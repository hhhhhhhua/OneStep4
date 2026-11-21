package com.sangluo.onestep.feature.screenshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen editor: frozen screenshot, crop rectangle, annotation canvas and toolbar.
 * All geometry is expressed in view pixels.
 */
public final class ScreenshotEditorView extends View {
    public interface Listener {
        void onCloseRequested();

        void onCopyRequested();

        void onSaveRequested();

        void onTextRequested(float x, float y);
    }

    public enum Tool {
        NONE, TEXT, DOODLE, MOSAIC
    }

    private static final int ITEM_TEXT = 0;
    private static final int ITEM_DOODLE = 1;
    private static final int ITEM_MOSAIC = 2;
    private static final int ITEM_UNDO = 3;
    private static final int ITEM_COPY = 4;
    private static final int ITEM_SAVE = 5;

    private static final int SCRIM_COLOR = 0xb3000000;
    private static final int TOOLBAR_BACKGROUND = 0xe61f2328;
    private static final int TOOL_ACTIVE_BACKGROUND = 0x384f8cff;
    private static final int ACCENT_COLOR = 0xff4f8cff;
    private static final int TEXT_COLOR = 0xffffffff;
    private static final int MUTED_COLOR = 0x80ffffff;
    private static final int SELECTION_COLOR = 0xffffffff;
    private static final int CLOSE_BACKGROUND = 0x99000000;

    private static final float TOOLBAR_HEIGHT_DP = 52f;
    private static final float TOOLBAR_ITEM_WIDTH_DP = 58f;
    private static final float TOOLBAR_GAP_DP = 12f;
    private static final float TOOLBAR_EDGE_DP = 8f;
    private static final float CHIP_HEIGHT_DP = 34f;
    private static final float CHIP_WIDTH_DP = 44f;
    private static final float CHIP_GAP_DP = 8f;
    private static final float CLOSE_RADIUS_DP = 16f;
    private static final float CLOSE_MARGIN_DP = 20f;
    private static final float HANDLE_HIT_DP = 20f;
    private static final float HANDLE_RADIUS_DP = 5f;
    private static final float TEXT_SIZE_SP = 18f;
    private static final float MODE_CHIP_WIDTH_DP = 64f;
    private static final float MODE_CHIP_HEIGHT_DP = 32f;
    private static final float MODE_CHIP_GAP_DP = 6f;
    private static final float MODE_SELECTION_GAP_DP = 10f;
    private static final float MODE_EDGE_DP = 8f;
    private static final int MODE_BAR_BACKGROUND = 0xcc1f2328;
    private static final int MODE_ACTIVE_BACKGROUND = 0xff4f8cff;
    private static final int MODE_INACTIVE_TEXT = 0xccffffff;

    private static final float[] DOODLE_WIDTH_DP = {2f, 4f, 7f};
    private static final float[] MOSAIC_BLOCK_DP = {8f, 12f, 18f};
    private static final float MOSAIC_DAB_TO_BLOCK = 2.6f;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = new Paint();
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toolbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint itemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint modeBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint modeChipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint modeLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawingRect = new RectF();
    private final RectF modeBarRect = new RectF();
    private final Path glyphPath = new Path();

    private Bitmap source;
    private final float displayScale;
    private final List<ScreenshotAnnotation> annotations = new ArrayList<>();

    private Listener listener;
    private float[] selection;
    private float[] toolbarBounds = new float[4];
    private final float[][] itemBounds = new float[6][4];
    private final float[][] chipBounds = new float[3][4];
    private final float[][] modeChipBounds = new float[3][4];
    private final RectF closeBounds = new RectF();

    private Tool tool = Tool.NONE;
    private int doodleWidthIndex = 1;
    private int mosaicWidthIndex = 1;
    private boolean exporting;
    private boolean inputBlocked;
    private ScreenshotCaptureMode captureMode = ScreenshotCaptureMode.FULL_SCREEN;
    private float[] mainSlotBounds;
    private int pressedMode = -1;

    private int dragMode = DRAG_NONE;
    private int laidOutItemCount = -1;
    private int activeCorner = ScreenshotSelectionPolicy.CORNER_NONE;
    private int pressedItem = -1;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private float[] selectionAtDown;
    private ScreenshotAnnotation activeAnnotation;

    private static final int DRAG_NONE = 0;
    private static final int DRAG_MOVE = 1;
    private static final int DRAG_RESIZE = 2;
    private static final int DRAG_DRAW = 3;

    public ScreenshotEditorView(Context context, Bitmap source, float displayScale) {
        super(context);
        this.source = source;
        this.displayScale = displayScale <= 0f ? 1f : displayScale;
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);

        scrimPaint.setColor(SCRIM_COLOR);
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(1.5f));
        selectionPaint.setColor(SELECTION_COLOR);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(SELECTION_COLOR);
        toolbarPaint.setStyle(Paint.Style.FILL);
        toolbarPaint.setColor(TOOLBAR_BACKGROUND);
        itemPaint.setStyle(Paint.Style.FILL);
        glyphPaint.setStyle(Paint.Style.STROKE);
        glyphPaint.setStrokeWidth(dp(1.8f));
        glyphPaint.setStrokeCap(Paint.Cap.ROUND);
        glyphPaint.setStrokeJoin(Paint.Join.ROUND);
        glyphPaint.setColor(TEXT_COLOR);
        labelPaint.setColor(TEXT_COLOR);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(10));
        chipPaint.setStyle(Paint.Style.FILL);
        statusPaint.setColor(TEXT_COLOR);
        statusPaint.setTextAlign(Paint.Align.CENTER);
        statusPaint.setTextSize(sp(13));
        statusPaint.setTypeface(Typeface.DEFAULT_BOLD);
        modeBarPaint.setStyle(Paint.Style.FILL);
        modeBarPaint.setColor(MODE_BAR_BACKGROUND);
        modeChipPaint.setStyle(Paint.Style.FILL);
        modeLabelPaint.setColor(MODE_INACTIVE_TEXT);
        modeLabelPaint.setTextAlign(Paint.Align.CENTER);
        modeLabelPaint.setTextSize(sp(12));
        modeLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        setContentDescription("截图编辑");
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Drops the bitmap reference before the controller recycles it. */
    public void release() {
        source = null;
        annotations.clear();
        invalidate();
    }

    public float[] getSelection() {
        return selection == null ? new float[]{0f, 0f, 0f, 0f} : selection.clone();
    }

    public List<ScreenshotAnnotation> getAnnotations() {
        return annotations;
    }

    public float getDisplayScale() {
        return displayScale;
    }

    /**
     * Bounds of the active main slot in view pixels, used by the 主slot capture mode.
     * {@code null} when the host could not resolve a pane.
     */
    public void setMainSlotBounds(float[] bounds) {
        mainSlotBounds = bounds == null || bounds.length < 4 ? null : bounds.clone();
        invalidate();
    }

    public ScreenshotCaptureMode getCaptureMode() {
        return captureMode;
    }

    public void setExporting(boolean exporting) {
        this.exporting = exporting;
        invalidate();
    }

    public boolean isExporting() {
        return exporting;
    }

    /** Blocks editor gestures while the modal text input panel is shown. */
    public void setInputBlocked(boolean inputBlocked) {
        this.inputBlocked = inputBlocked;
    }

    /** Adds a committed text annotation at the requested baseline anchor. */
    public void addText(float x, float y, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        annotations.add(new ScreenshotAnnotation.TextAnnotation(
                x, y, sp(TEXT_SIZE_SP), TEXT_COLOR, text));
        tool = Tool.NONE;
        invalidate();
    }

    public void undo() {
        if (!annotations.isEmpty()) {
            annotations.remove(annotations.size() - 1);
            invalidate();
        }
    }

    public boolean hasAnnotations() {
        return !annotations.isEmpty();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (selection == null) {
            float[] preset = selectionForMode(captureMode, width, height);
            selection = preset == null
                    ? ScreenshotSelectionPolicy.fullScreen(width, height) : preset;
        } else {
            selection = ScreenshotSelectionPolicy.move(selection, 0f, 0f, width, height);
        }
        layoutToolbar(width, height);
    }

    private void layoutToolbar(int width, int height) {
        int count = toolbarItemCount();
        float toolbarWidth = dp(TOOLBAR_ITEM_WIDTH_DP) * count;
        toolbarBounds = ScreenshotToolbarPolicy.toolbarBounds(selection,
                toolbarWidth, dp(TOOLBAR_HEIGHT_DP), width, height,
                dp(TOOLBAR_GAP_DP), dp(TOOLBAR_EDGE_DP));
        for (int index = 0; index < count; index++) {
            int item = itemIndexForSlot(index);
            float[] bounds = ScreenshotToolbarPolicy.itemBounds(toolbarBounds, count, index);
            System.arraycopy(bounds, 0, itemBounds[item], 0, 4);
        }
        laidOutItemCount = count;
        layoutModeBar(width, height);
        layoutWidthChips(width, height);

        float closeRadius = dp(CLOSE_RADIUS_DP);
        float margin = dp(CLOSE_MARGIN_DP) + closeRadius;
        closeBounds.set(width - margin - closeRadius, margin - closeRadius,
                width - margin + closeRadius, margin + closeRadius);
    }

    private void layoutWidthChips(int width, int height) {
        float chipWidth = dp(CHIP_WIDTH_DP);
        float chipHeight = dp(CHIP_HEIGHT_DP);
        float gap = dp(CHIP_GAP_DP);
        float total = chipWidth * 3f + gap * 2f;
        float left = (width - total) / 2f;
        boolean toolbarBelowSelection = selection == null
                || toolbarBounds[1] >= selection[3];
        float top = toolbarBelowSelection
                ? toolbarBounds[3] + dp(8)
                : toolbarBounds[1] - dp(8) - chipHeight;
        if (!toolbarBelowSelection && modeBarRect.bottom <= toolbarBounds[1]) {
            // Mode chips already sit above the flipped toolbar; keep width chips above
            // both rows.
            top = modeBarRect.top - dp(8) - chipHeight;
        }
        if (top + chipHeight > height - dp(8)) {
            top = toolbarBounds[1] - dp(8) - chipHeight;
        }
        if (top < dp(8)) {
            top = Math.min(height - chipHeight - dp(8), toolbarBounds[3] + dp(8));
        }
        top = Math.max(dp(8), top);
        for (int index = 0; index < 3; index++) {
            float chipLeft = left + (chipWidth + gap) * index;
            chipBounds[index][0] = chipLeft;
            chipBounds[index][1] = top;
            chipBounds[index][2] = chipLeft + chipWidth;
            chipBounds[index][3] = top + chipHeight;
        }
    }

    private void layoutModeBar(int width, int height) {
        int count = ScreenshotCaptureMode.values().length;
        float gap = dp(MODE_CHIP_GAP_DP);
        float top = selection == null ? 0f : selection[1];
        if (selection != null && toolbarBounds[3] <= selection[1]) {
            // The toolbar flipped above the selection; stack the mode chips above it so
            // the two control rows never overlap.
            top = Math.min(top, toolbarBounds[1]);
        }
        float[] anchor = selection == null ? null
                : new float[]{selection[0], top, selection[2], selection[3]};
        float[] bar = ScreenshotModeBarPolicy.barBounds(anchor, count,
                dp(MODE_CHIP_WIDTH_DP), dp(MODE_CHIP_HEIGHT_DP), gap,
                width, height, dp(MODE_EDGE_DP), dp(MODE_SELECTION_GAP_DP));
        modeBarRect.set(bar[0], bar[1], bar[2], bar[3]);
        for (int index = 0; index < count; index++) {
            float[] bounds = ScreenshotModeBarPolicy.chipBounds(bar, count, index, gap);
            System.arraycopy(bounds, 0, modeChipBounds[index], 0, 4);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (source == null || selection == null) {
            return;
        }
        if (laidOutItemCount != toolbarItemCount()) {
            layoutToolbar(getWidth(), getHeight());
        }
        drawingRect.set(0f, 0f, getWidth(), getHeight());
        canvas.drawBitmap(source, null, drawingRect, bitmapPaint);

        drawScrim(canvas);
        drawAnnotations(canvas);
        drawSelection(canvas);
        drawModeBar(canvas);
        drawToolbar(canvas);
        drawCloseButton(canvas);
        if (tool == Tool.DOODLE || tool == Tool.MOSAIC) {
            drawWidthChips(canvas);
        }
        if (exporting) {
            statusPaint.setColor(0xcc000000);
            canvas.drawRect(toolbarBounds[0], toolbarBounds[1],
                    toolbarBounds[2], toolbarBounds[3], statusPaint);
            statusPaint.setColor(TEXT_COLOR);
            canvas.drawText("处理中…", (toolbarBounds[0] + toolbarBounds[2]) / 2f,
                    (toolbarBounds[1] + toolbarBounds[3]) / 2f + dp(5), statusPaint);
        }
    }

    private void drawScrim(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        canvas.drawRect(0f, 0f, width, selection[1], scrimPaint);
        canvas.drawRect(0f, selection[3], width, height, scrimPaint);
        canvas.drawRect(0f, selection[1], selection[0], selection[3], scrimPaint);
        canvas.drawRect(selection[2], selection[1], width, selection[3], scrimPaint);
    }

    private void drawAnnotations(Canvas canvas) {
        if (annotations.isEmpty()) {
            return;
        }
        canvas.save();
        canvas.clipRect(selection[0], selection[1], selection[2], selection[3]);
        for (ScreenshotAnnotation annotation : annotations) {
            annotation.draw(canvas, source, displayScale);
        }
        canvas.restore();
    }

    private void drawSelection(Canvas canvas) {
        canvas.drawRect(selection[0], selection[1], selection[2], selection[3],
                selectionPaint);
        float radius = dp(HANDLE_RADIUS_DP);
        drawHandle(canvas, selection[0], selection[1], radius);
        drawHandle(canvas, selection[2], selection[1], radius);
        drawHandle(canvas, selection[2], selection[3], radius);
        drawHandle(canvas, selection[0], selection[3], radius);
    }

    private void drawHandle(Canvas canvas, float x, float y, float radius) {
        canvas.drawCircle(x, y, radius + dp(1), selectionPaint);
        canvas.drawCircle(x, y, radius, handlePaint);
    }

    /** Capture-mode chips, centred above the crop rectangle. */
    private void drawModeBar(Canvas canvas) {
        ScreenshotCaptureMode[] modes = ScreenshotCaptureMode.values();
        canvas.drawRoundRect(modeBarRect, dp(12), dp(12), modeBarPaint);
        for (int index = 0; index < modes.length; index++) {
            float[] bounds = modeChipBounds[index];
            boolean active = modes[index] == captureMode;
            if (index == pressedMode) {
                modeChipPaint.setColor(0x40ffffff);
                canvas.drawRoundRect(bounds[0], bounds[1], bounds[2], bounds[3],
                        dp(10), dp(10), modeChipPaint);
            }
            if (active) {
                modeChipPaint.setColor(MODE_ACTIVE_BACKGROUND);
                canvas.drawRoundRect(bounds[0], bounds[1], bounds[2], bounds[3],
                        dp(10), dp(10), modeChipPaint);
            }
            modeLabelPaint.setColor(active ? TEXT_COLOR : MODE_INACTIVE_TEXT);
            float centerX = (bounds[0] + bounds[2]) / 2f;
            float centerY = (bounds[1] + bounds[3]) / 2f;
            Paint.FontMetrics metrics = modeLabelPaint.getFontMetrics();
            canvas.drawText(modeLabel(modes[index]), centerX,
                    centerY - (metrics.ascent + metrics.descent) / 2f, modeLabelPaint);
        }
    }

    private static String modeLabel(ScreenshotCaptureMode mode) {
        switch (mode) {
            case FULL_SCREEN:
                return "全屏";
            case MAIN_SLOT:
                return "主slot";
            case REGION:
            default:
                return "区域";
        }
    }

    private void drawToolbar(Canvas canvas) {
        canvas.drawRoundRect(toolbarBounds[0], toolbarBounds[1] - dp(2),
                toolbarBounds[2], toolbarBounds[3] + dp(2), dp(14), dp(14), toolbarPaint);
        int count = toolbarItemCount();
        float itemHeight = toolbarBounds[3] - toolbarBounds[1];
        for (int slot = 0; slot < count; slot++) {
            int item = itemIndexForSlot(slot);
            float[] bounds = itemBounds[item];
            boolean active = (item == ITEM_TEXT && tool == Tool.TEXT)
                    || (item == ITEM_DOODLE && tool == Tool.DOODLE)
                    || (item == ITEM_MOSAIC && tool == Tool.MOSAIC);
            if (item == pressedItem) {
                itemPaint.setColor(0x33ffffff);
                canvas.drawRoundRect(bounds[0] + dp(2), bounds[1] + dp(2),
                        bounds[2] - dp(2), bounds[3] - dp(2), dp(10), dp(10), itemPaint);
            } else if (active) {
                itemPaint.setColor(TOOL_ACTIVE_BACKGROUND);
                canvas.drawRoundRect(bounds[0] + dp(2), bounds[1] + dp(2),
                        bounds[2] - dp(2), bounds[3] - dp(2), dp(10), dp(10), itemPaint);
            }
            float centerX = (bounds[0] + bounds[2]) / 2f;
            float iconCenterY = bounds[1] + itemHeight * 0.38f;
            boolean enabled = item != ITEM_UNDO || !annotations.isEmpty();
            int color = active ? ACCENT_COLOR : enabled ? TEXT_COLOR : MUTED_COLOR;
            drawItemGlyph(canvas, item, centerX, iconCenterY, color);
            labelPaint.setColor(color);
            canvas.drawText(itemLabel(item), centerX,
                    bounds[1] + itemHeight * 0.8f, labelPaint);
        }
    }

    private void drawItemGlyph(Canvas canvas, int item, float cx, float cy, int color) {
        glyphPaint.setColor(color);
        switch (item) {
            case ITEM_TEXT:
                glyphPaint.setStyle(Paint.Style.FILL);
                glyphPaint.setTypeface(Typeface.DEFAULT_BOLD);
                glyphPaint.setTextSize(dp(19));
                glyphPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("A", cx, cy + dp(7), glyphPaint);
                glyphPaint.setStyle(Paint.Style.STROKE);
                break;
            case ITEM_DOODLE:
                glyphPath.reset();
                glyphPath.moveTo(cx - dp(9), cy + dp(5));
                glyphPath.quadTo(cx - dp(4), cy - dp(9), cx + dp(2), cy + dp(1));
                glyphPath.quadTo(cx + dp(6), cy + dp(7), cx + dp(9), cy - dp(3));
                canvas.drawPath(glyphPath, glyphPaint);
                break;
            case ITEM_MOSAIC:
                float size = dp(6);
                for (int row = 0; row < 2; row++) {
                    for (int column = 0; column < 2; column++) {
                        float left = cx - size + column * (size + dp(1));
                        float top = cy - size + row * (size + dp(1));
                        if ((row + column) % 2 == 0) {
                            glyphPaint.setStyle(Paint.Style.FILL);
                            canvas.drawRect(left, top, left + size, top + size, glyphPaint);
                        } else {
                            glyphPaint.setStyle(Paint.Style.STROKE);
                            canvas.drawRect(left, top, left + size, top + size, glyphPaint);
                        }
                    }
                }
                glyphPaint.setStyle(Paint.Style.STROKE);
                break;
            case ITEM_UNDO:
                drawingRect.set(cx - dp(8), cy - dp(7), cx + dp(8), cy + dp(9));
                canvas.drawArc(drawingRect, 200f, 250f, false, glyphPaint);
                canvas.drawLine(cx - dp(8), cy - dp(7), cx - dp(11), cy - dp(1), glyphPaint);
                canvas.drawLine(cx - dp(8), cy - dp(7), cx - dp(1), cy - dp(7), glyphPaint);
                break;
            case ITEM_COPY:
                canvas.drawRoundRect(cx - dp(8), cy - dp(7), cx + dp(3), cy + dp(5),
                        dp(2), dp(2), glyphPaint);
                canvas.drawRoundRect(cx - dp(3), cy - dp(3), cx + dp(8), cy + dp(9),
                        dp(2), dp(2), glyphPaint);
                break;
            case ITEM_SAVE:
                canvas.drawLine(cx, cy - dp(9), cx, cy + dp(3), glyphPaint);
                canvas.drawLine(cx, cy + dp(3), cx - dp(4), cy - dp(1), glyphPaint);
                canvas.drawLine(cx, cy + dp(3), cx + dp(4), cy - dp(1), glyphPaint);
                canvas.drawLine(cx - dp(9), cy + dp(9), cx + dp(9), cy + dp(9), glyphPaint);
                break;
            default:
                break;
        }
    }

    private static String itemLabel(int item) {
        switch (item) {
            case ITEM_TEXT:
                return "文本";
            case ITEM_DOODLE:
                return "涂鸦";
            case ITEM_MOSAIC:
                return "马赛克";
            case ITEM_UNDO:
                return "撤销";
            case ITEM_COPY:
                return "复制";
            case ITEM_SAVE:
                return "保存";
            default:
                return "";
        }
    }

    private void drawCloseButton(Canvas canvas) {
        float cx = closeBounds.centerX();
        float cy = closeBounds.centerY();
        itemPaint.setColor(CLOSE_BACKGROUND);
        canvas.drawCircle(cx, cy, closeBounds.width() / 2f, itemPaint);
        glyphPaint.setColor(TEXT_COLOR);
        canvas.drawLine(cx - dp(5), cy - dp(5), cx + dp(5), cy + dp(5), glyphPaint);
        canvas.drawLine(cx - dp(5), cy + dp(5), cx + dp(5), cy - dp(5), glyphPaint);
    }

    private void drawWidthChips(Canvas canvas) {
        int selected = activeWidthIndex();
        int count = 3;
        for (int index = 0; index < count; index++) {
            float[] bounds = chipBounds[index];
            itemPaint.setColor(0xcc1f2328);
            canvas.drawRoundRect(bounds[0], bounds[1], bounds[2], bounds[3],
                    dp(10), dp(10), itemPaint);
            float cx = (bounds[0] + bounds[2]) / 2f;
            float cy = (bounds[1] + bounds[3]) / 2f;
            float radius = tool == Tool.DOODLE
                    ? dp(2f + index * 2.5f) : dp(5f + index * 3f);
            float maxRadius = Math.min(bounds[2] - bounds[0], bounds[3] - bounds[1]) * 0.32f;
            radius = Math.max(dp(2), Math.min(radius, maxRadius));
            cellPaintColor(index == selected);
            canvas.drawCircle(cx, cy, radius, chipPaint);
            if (index == selected) {
                selectionPaint.setColor(ACCENT_COLOR);
                canvas.drawCircle(cx, cy, radius + dp(4), selectionPaint);
                selectionPaint.setColor(SELECTION_COLOR);
            }
        }
    }

    private void cellPaintColor(boolean selected) {
        chipPaint.setColor(selected ? ACCENT_COLOR : TEXT_COLOR);
    }

    // ------------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (selection == null || exporting || inputBlocked) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return handleDown(x, y);
            case MotionEvent.ACTION_MOVE:
                handleMove(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                handleUp(x, y);
                return true;
            case MotionEvent.ACTION_CANCEL:
                resetGesture();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private boolean handleDown(float x, float y) {
        downX = x;
        downY = y;
        lastX = x;
        lastY = y;
        pressedItem = -1;
        pressedMode = -1;
        dragMode = DRAG_NONE;
        activeAnnotation = null;

        if (closeBounds.contains(x, y)) {
            dragMode = DRAG_NONE;
            pressedItem = -2;
            invalidate();
            return true;
        }
        int mode = hitModeChip(x, y);
        if (mode >= 0) {
            pressedMode = mode;
            invalidate();
            return true;
        }
        if (tool == Tool.DOODLE || tool == Tool.MOSAIC) {
            int chip = hitWidthChip(x, y);
            if (chip >= 0) {
                if (tool == Tool.DOODLE) {
                    doodleWidthIndex = chip;
                } else {
                    mosaicWidthIndex = chip;
                }
                invalidate();
                return true;
            }
        }
        int item = hitToolbarItem(x, y);
        if (item >= 0) {
            pressedItem = item;
            invalidate();
            return true;
        }
        int corner = ScreenshotSelectionPolicy.hitHandle(
                selection, x, y, dp(HANDLE_HIT_DP));
        if (corner != ScreenshotSelectionPolicy.CORNER_NONE) {
            dragMode = DRAG_RESIZE;
            activeCorner = corner;
            selectionAtDown = selection.clone();
            return true;
        }
        if (!ScreenshotSelectionPolicy.contains(selection, x, y)) {
            return true;
        }
        if (tool != Tool.NONE && isNearSelectionBorder(x, y, dp(16))) {
            // Keep the rectangle movable without leaving the active drawing tool.
            dragMode = DRAG_MOVE;
            selectionAtDown = selection.clone();
            return true;
        }
        if (tool == Tool.TEXT) {
            if (listener != null) {
                listener.onTextRequested(x, y);
            }
            return true;
        }
        if (tool == Tool.DOODLE) {
            ScreenshotAnnotation.DoodleAnnotation annotation =
                    new ScreenshotAnnotation.DoodleAnnotation(
                            dp(DOODLE_WIDTH_DP[doodleWidthIndex]), 0xffff3b30);
            annotation.addPoint(x, y);
            annotations.add(annotation);
            activeAnnotation = annotation;
            dragMode = DRAG_DRAW;
            invalidate();
            return true;
        }
        if (tool == Tool.MOSAIC) {
            float block = dp(MOSAIC_BLOCK_DP[mosaicWidthIndex]);
            ScreenshotAnnotation.MosaicAnnotation annotation =
                    new ScreenshotAnnotation.MosaicAnnotation(
                            block * MOSAIC_DAB_TO_BLOCK, block);
            annotation.addPoint(x, y);
            annotations.add(annotation);
            activeAnnotation = annotation;
            dragMode = DRAG_DRAW;
            invalidate();
            return true;
        }
        dragMode = DRAG_MOVE;
        selectionAtDown = selection.clone();
        return true;
    }

    private void handleMove(float x, float y) {
        if (dragMode == DRAG_MOVE && selectionAtDown != null) {
            markRegionMode();
            selection = ScreenshotSelectionPolicy.move(selectionAtDown,
                    x - downX, y - downY, getWidth(), getHeight());
            layoutToolbar(getWidth(), getHeight());
            invalidate();
        } else if (dragMode == DRAG_RESIZE) {
            markRegionMode();
            selection = ScreenshotSelectionPolicy.resize(selectionAtDown, activeCorner,
                    x, y, dp(96), getWidth(), getHeight());
            layoutToolbar(getWidth(), getHeight());
            invalidate();
        } else if (dragMode == DRAG_DRAW && activeAnnotation != null) {
            float dx = x - lastX;
            float dy = y - lastY;
            if (dx * dx + dy * dy >= dp(1.5f) * dp(1.5f)) {
                addPointToActive(x, y);
                lastX = x;
                lastY = y;
                invalidate();
            }
        }
    }

    private void addPointToActive(float x, float y) {
        if (activeAnnotation instanceof ScreenshotAnnotation.DoodleAnnotation) {
            ((ScreenshotAnnotation.DoodleAnnotation) activeAnnotation).addPoint(x, y);
        } else if (activeAnnotation instanceof ScreenshotAnnotation.MosaicAnnotation) {
            ((ScreenshotAnnotation.MosaicAnnotation) activeAnnotation).addPoint(x, y);
        }
    }

    private void handleUp(float x, float y) {
        if (pressedMode >= 0) {
            int index = pressedMode;
            resetGesture();
            applyCaptureMode(ScreenshotCaptureMode.values()[index]);
            return;
        }
        if (pressedItem == -2) {
            if (listener != null) {
                listener.onCloseRequested();
            }
            resetGesture();
            return;
        }
        if (pressedItem >= 0) {
            int item = pressedItem;
            resetGesture();
            performItem(item);
            return;
        }
        if (dragMode == DRAG_DRAW && activeAnnotation != null) {
            addPointToActive(x, y);
        }
        if (dragMode == DRAG_MOVE || dragMode == DRAG_RESIZE) {
            layoutToolbar(getWidth(), getHeight());
        }
        resetGesture();
        invalidate();
    }

    private void resetGesture() {
        dragMode = DRAG_NONE;
        activeCorner = ScreenshotSelectionPolicy.CORNER_NONE;
        pressedItem = -1;
        pressedMode = -1;
        activeAnnotation = null;
        selectionAtDown = null;
    }

    private void performItem(int item) {
        switch (item) {
            case ITEM_TEXT:
                tool = tool == Tool.TEXT ? Tool.NONE : Tool.TEXT;
                break;
            case ITEM_DOODLE:
                tool = tool == Tool.DOODLE ? Tool.NONE : Tool.DOODLE;
                break;
            case ITEM_MOSAIC:
                tool = tool == Tool.MOSAIC ? Tool.NONE : Tool.MOSAIC;
                break;
            case ITEM_UNDO:
                undo();
                break;
            case ITEM_COPY:
                if (listener != null) {
                    listener.onCopyRequested();
                }
                break;
            case ITEM_SAVE:
                if (listener != null) {
                    listener.onSaveRequested();
                }
                break;
            default:
                break;
        }
        layoutToolbar(getWidth(), getHeight());
        invalidate();
    }

    /** Applies a chip preset and moves the highlight to it. */
    private void applyCaptureMode(ScreenshotCaptureMode mode) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            captureMode = mode;
            return;
        }
        float[] preset = selectionForMode(mode, width, height);
        if (preset == null) {
            // No usable pane for 主slot; stay on the full frame rather than showing an
            // empty selection.
            captureMode = ScreenshotCaptureMode.FULL_SCREEN;
            preset = ScreenshotSelectionPolicy.fullScreen(width, height);
        } else {
            captureMode = mode;
        }
        selection = preset;
        layoutToolbar(width, height);
        invalidate();
    }

    private float[] selectionForMode(ScreenshotCaptureMode mode, int width, int height) {
        switch (mode) {
            case MAIN_SLOT:
                return ScreenshotSelectionPolicy.clampToScreen(
                        mainSlotBounds, width, height, 1f);
            case REGION:
                return ScreenshotSelectionPolicy.initial(width, height, dp(96));
            case FULL_SCREEN:
            default:
                return ScreenshotSelectionPolicy.fullScreen(width, height);
        }
    }

    /** Dragging the rectangle means the user is refining a free region. */
    private void markRegionMode() {
        if (captureMode != ScreenshotCaptureMode.REGION) {
            captureMode = ScreenshotCaptureMode.REGION;
        }
    }

    private int hitModeChip(float x, float y) {
        for (int index = 0; index < modeChipBounds.length; index++) {
            float[] bounds = modeChipBounds[index];
            if (x >= bounds[0] && x <= bounds[2] && y >= bounds[1] && y <= bounds[3]) {
                return index;
            }
        }
        return -1;
    }

    private int hitToolbarItem(float x, float y) {
        int count = toolbarItemCount();
        for (int slot = 0; slot < count; slot++) {
            int item = itemIndexForSlot(slot);
            float[] bounds = itemBounds[item];
            if (x >= bounds[0] && x <= bounds[2] && y >= bounds[1] && y <= bounds[3]) {
                return item;
            }
        }
        return -1;
    }

    private boolean isNearSelectionBorder(float x, float y, float band) {
        if (selection == null || band <= 0f) {
            return false;
        }
        return x - selection[0] <= band || selection[2] - x <= band
                || y - selection[1] <= band || selection[3] - y <= band;
    }

    private int hitWidthChip(float x, float y) {
        if (tool != Tool.DOODLE && tool != Tool.MOSAIC) {
            return -1;
        }
        for (int index = 0; index < 3; index++) {
            float[] bounds = chipBounds[index];
            if (x >= bounds[0] && x <= bounds[2] && y >= bounds[1] && y <= bounds[3]) {
                return index;
            }
        }
        return -1;
    }

    private int activeWidthIndex() {
        return tool == Tool.MOSAIC ? mosaicWidthIndex : doodleWidthIndex;
    }

    private int toolbarItemCount() {
        return annotations.isEmpty() ? 5 : 6;
    }

    private int itemIndexForSlot(int slot) {
        if (annotations.isEmpty()) {
            return slot < ITEM_UNDO ? slot : slot + 1;
        }
        return slot;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}
