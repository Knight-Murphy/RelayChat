package com.relaychat.app.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Full screen reader for one table. The grid is fitted to the screen on open, so the whole table is
 * visible at once, and it can be pinched to zoom and dragged to pan for the small print.
 */
public final class TableViewerDialog extends Dialog {
    private TableViewerDialog(Context context, MarkdownText.Table table,
                              MarkdownRenderer.Palette palette) {
        super(context);
        int padding = palette.dp(12);
        LinearLayout root = UiKit.vertical(context);
        root.setBackgroundColor(UiKit.CANVAS);
        root.setPadding(padding, padding, padding, padding);

        LinearLayout bar = UiKit.horizontal(context);
        TextView title = UiKit.heading(context, "\u8868\u683c", 16);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button copy = UiKit.compactButton(context, "\u590d\u5236", false);
        copy.setOnClickListener(view -> copy(table, context));
        bar.addView(copy);

        Button close = UiKit.compactButton(context, "\u5173\u95ed", true);
        close.setOnClickListener(view -> dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.leftMargin = palette.dp(8);
        bar.addView(close, closeParams);
        root.addView(bar);

        TextView hint = UiKit.text(context,
                "\u53cc\u6307\u7f29\u653e\uff0c\u62d6\u52a8\u5e73\u79fb\uff0c\u53cc\u51fb\u590d\u4f4d",
                12, UiKit.MUTED);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = palette.dp(4);
        hintParams.bottomMargin = palette.dp(8);
        root.addView(hint, hintParams);

        ZoomPane pane = new ZoomPane(context);
        pane.setPadding(palette.dp(8), palette.dp(8), palette.dp(8), palette.dp(8));
        pane.setBackground(UiKit.rounded(UiKit.SURFACE, palette.dp(8), UiKit.BORDER, palette.dp(1)));
        TableGridView grid = new TableGridView(context);
        grid.bind(table, palette, 0, TableGridView.VIEWER_TEXT_SP, null);
        pane.addView(grid, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(pane, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.55f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        setCanceledOnTouchOutside(true);
    }

    /** Opens the reader for one table, styled like the light assistant bubble. */
    public static void show(Context context, MarkdownText.Table table) {
        if (table == null) {
            return;
        }
        float density = context.getResources().getDisplayMetrics().density;
        new TableViewerDialog(context, table,
                MarkdownRenderer.Palette.assistant(density)).show();
    }

    private void copy(MarkdownText.Table table, Context context) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("table", MarkdownText.toPlainText(table)));
        Toast.makeText(context, "\u5df2\u590d\u5236\u8868\u683c", Toast.LENGTH_SHORT).show();
    }
}
