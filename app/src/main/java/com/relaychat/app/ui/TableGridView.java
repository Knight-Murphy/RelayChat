package com.relaychat.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Draws one pipe table as a real grid: a shaded bold header, columns sized from their widest cell and
 * cell text that wraps inside its own column. A table that is wider than the bubble is squeezed down
 * to a floor that keeps it readable; when even that is not enough the extra columns are clipped and
 * the grid gains a footer that opens the full screen {@link TableViewerDialog}. Replies used to show
 * tables as monospaced ASCII art, which wrapped into noise on a phone.
 */
public final class TableGridView extends LinearLayout {
    /** Called when the reader asks for the full screen view of a table that does not fit. */
    public interface OpenListener {
        void onOpenTable(MarkdownText.Table table);
    }

    public static final float CELL_TEXT_SP = 13.5f;
    public static final float VIEWER_TEXT_SP = 15f;

    private static final int MIN_COLUMN_DP = 46;
    private static final int MAX_COLUMN_DP = 190;

    private final LinearLayout card;

    public TableGridView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setClipToOutline(true);
        addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * Lays the table out for a bubble whose text column is {@code contentWidth} pixels wide. A width
     * that is not positive asks for the natural width, which is what the full screen reader uses.
     */
    public void bind(MarkdownText.Table table, MarkdownRenderer.Palette palette, int contentWidth,
                     float textSp, OpenListener listener) {
        card.removeAllViews();
        if (table == null || table.rowCount() == 0 || table.columns() == 0) {
            return;
        }
        Context context = getContext();
        int rows = table.rowCount();
        int columns = table.columns();
        int edge = palette.dp(9);
        int vertical = palette.dp(7);
        int border = Math.max(1, palette.dp(1));
        int[] widths = measureColumns(table, palette, edge, textSp);
        int natural = totalOf(widths);
        int available = contentWidth > 0 ? contentWidth - border * 2 : natural;
        boolean overflowing = natural > available;
        if (overflowing) {
            squeeze(widths, natural - available, palette);
        } else if (natural < available) {
            stretch(widths, available - natural);
        }
        int total = totalOf(widths);

        card.setPadding(border, border, border, border);
        card.setBackground(UiKit.rounded(palette.tableBase, palette.dp(6), palette.tableBorder,
                border));

        for (int row = 0; row < rows; row++) {
            boolean header = row == 0;
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(HORIZONTAL);
            line.setBackgroundColor(header ? palette.tableHeader
                    : (row % 2 == 1 ? palette.tableStripe : Color.TRANSPARENT));
            for (int column = 0; column < columns; column++) {
                TextView cell = UiKit.text(context, "", textSp, palette.text);
                cell.setText(MarkdownRenderer.renderPieces(table.cell(row, column), palette));
                cell.setPadding(edge, vertical, edge, vertical);
                cell.setGravity(gravityOf(table.alignOf(column)) | Gravity.CENTER_VERTICAL);
                if (header) {
                    cell.setTypeface(Typeface.DEFAULT_BOLD);
                }
                line.addView(cell, new LayoutParams(widths[column], LayoutParams.WRAP_CONTENT));
            }
            card.addView(line, new LayoutParams(total, LayoutParams.WRAP_CONTENT));
            if (row + 1 < rows) {
                View divider = new View(context);
                divider.setBackgroundColor(palette.tableDivider);
                card.addView(divider, new LayoutParams(total, border));
            }
        }
        if (overflowing && listener != null) {
            addFooter(table, palette, listener, border);
        }
    }

    /** Adds the tap target that opens the reader, so nobody has to scroll the bubble sideways. */
    private void addFooter(MarkdownText.Table table, MarkdownRenderer.Palette palette,
                           OpenListener listener, int border) {
        Context context = getContext();
        View divider = new View(context);
        divider.setBackgroundColor(palette.tableDivider);
        card.addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, border));

        TextView footer = UiKit.text(context, "\u67e5\u770b\u5b8c\u6574\u8868\u683c \u2197", 13,
                palette.accent);
        footer.setTypeface(Typeface.DEFAULT_BOLD);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackgroundColor(palette.tableHeader);
        footer.setPadding(palette.dp(9), palette.dp(9), palette.dp(9), palette.dp(9));
        card.addView(footer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        OnClickListener open = view -> listener.onOpenTable(table);
        footer.setOnClickListener(open);
        card.setOnClickListener(open);
    }

    private int[] measureColumns(MarkdownText.Table table, MarkdownRenderer.Palette palette,
                                 int edge, float textSp) {
        Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);
        plain.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSp,
                getResources().getDisplayMetrics()));
        Paint bold = new Paint(plain);
        bold.setTypeface(Typeface.DEFAULT_BOLD);
        int shortest = palette.dp(MIN_COLUMN_DP);
        int longest = Math.max(shortest, palette.dp(MAX_COLUMN_DP));
        int[] widths = new int[table.columns()];
        for (int column = 0; column < widths.length; column++) {
            int widest = 0;
            for (int row = 0; row < table.rowCount(); row++) {
                Paint paint = row == 0 ? bold : plain;
                int measured = Math.round(paint.measureText(table.plainCell(row, column)));
                widest = Math.max(widest, measured);
            }
            widths[column] = Math.min(longest, Math.max(shortest, widest + edge * 2));
        }
        return widths;
    }

    private static int totalOf(int[] widths) {
        int total = 0;
        for (int width : widths) {
            total += width;
        }
        return total;
    }

    /** Shares the room left over by narrow columns, so a small table still spans the bubble. */
    private static void stretch(int[] widths, int extra) {
        int total = totalOf(widths);
        if (total <= 0 || extra <= 0) {
            return;
        }
        int used = 0;
        for (int index = 0; index < widths.length; index++) {
            int add = index == widths.length - 1
                    ? extra - used
                    : Math.round(extra * (widths[index] / (float) total));
            add = Math.max(0, add);
            widths[index] += add;
            used += add;
        }
    }

    /** Shrinks wide columns so the table can fit, stopping at a floor that keeps cells readable. */
    private static void squeeze(int[] widths, int excess, MarkdownRenderer.Palette palette) {
        int floor = palette.dp(MIN_COLUMN_DP);
        int shrinkable = 0;
        for (int width : widths) {
            shrinkable += Math.max(0, width - floor);
        }
        if (shrinkable <= 0 || excess <= 0) {
            return;
        }
        double factor = Math.min(1d, excess / (double) shrinkable);
        for (int index = 0; index < widths.length; index++) {
            int room = Math.max(0, widths[index] - floor);
            widths[index] -= Math.min(room, (int) Math.round(room * factor));
        }
    }

    private static int gravityOf(int align) {
        switch (align) {
            case MarkdownText.ALIGN_CENTER:
                return Gravity.CENTER_HORIZONTAL;
            case MarkdownText.ALIGN_RIGHT:
                return Gravity.END;
            default:
                return Gravity.START;
        }
    }
}
