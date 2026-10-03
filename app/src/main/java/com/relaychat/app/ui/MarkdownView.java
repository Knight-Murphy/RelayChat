package com.relaychat.app.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out one reply as views: runs of text blocks share a styled {@link TextView}, while a pipe
 * table becomes a {@link TableGridView} grid so wide tables stay readable instead of wrapping into
 * monospaced ASCII art.
 */
public final class MarkdownView extends LinearLayout {
    private static final float BODY_TEXT_SP = 15f;

    private final List<View> children = new ArrayList<>();
    private final List<Boolean> childIsTable = new ArrayList<>();
    private MarkdownRenderer.Palette palette = MarkdownRenderer.Palette.assistant(1f);
    private TableGridView.OpenListener tableOpener;
    private String source = "";
    private int contentWidth;
    private boolean plain;

    public MarkdownView(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    /** Sets the width a table may use; a table always fills it, which also caps the bubble. */
    public void setContentWidth(int widthPx) {
        if (widthPx <= 0 || widthPx == contentWidth) {
            return;
        }
        contentWidth = widthPx;
        if (!plain) {
            render();
        }
    }

    /** Hands wide tables a way to open in the full screen reader. */
    public void setTableOpener(TableGridView.OpenListener listener) {
        tableOpener = listener;
        if (!plain && !source.isEmpty()) {
            render();
        }
    }

    public void setMarkdown(String text, MarkdownRenderer.Palette style) {
        if (style != null) {
            palette = style;
        }
        source = text == null ? "" : text;
        plain = false;
        render();
    }

    /** Shows plain text without markdown styling, used by the pending and error bubbles. */
    public void setPlainText(String text, int color) {
        source = text == null ? "" : text;
        plain = true;
        TextView view = (TextView) reusable(0, false);
        applyTextWidth(view);
        view.setTextColor(color);
        view.setText(source);
        trimFrom(1);
    }

    private void render() {
        List<MarkdownText.Block> blocks = MarkdownText.parse(source);
        int used = 0;
        int index = 0;
        while (index < blocks.size()) {
            if (blocks.get(index).kind == MarkdownText.TABLE) {
                TableGridView grid = (TableGridView) reusable(used++, true);
                grid.setLayoutParams(tableParams());
                grid.bind(blocks.get(index).table, palette, contentWidth,
                        TableGridView.CELL_TEXT_SP, tableOpener);
                index++;
                continue;
            }
            int start = index;
            while (index < blocks.size() && blocks.get(index).kind != MarkdownText.TABLE) {
                index++;
            }
            TextView text = (TextView) reusable(used++, false);
            applyTextWidth(text);
            text.setTextColor(palette.text);
            text.setText(MarkdownRenderer.render(blocks.subList(start, index), palette));
        }
        trimFrom(used);
    }

    /** Keeps one view per block run, so a streaming reply reuses views instead of rebuilding them. */
    private View reusable(int index, boolean table) {
        if (index < children.size()) {
            if (childIsTable.get(index) == table) {
                return children.get(index);
            }
            trimFrom(index);
        }
        View created;
        if (table) {
            created = new TableGridView(getContext());
        } else {
            TextView text = UiKit.text(getContext(), "", BODY_TEXT_SP, palette.text);
            text.setTextIsSelectable(true);
            created = text;
        }
        childIsTable.add(table);
        children.add(created);
        addView(created, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return created;
    }

    private void trimFrom(int index) {
        while (children.size() > index) {
            int last = children.size() - 1;
            removeView(children.remove(last));
            childIsTable.remove(last);
        }
    }

    private void applyTextWidth(TextView view) {
        view.setMaxWidth(contentWidth > 0 ? contentWidth : Integer.MAX_VALUE);
    }

    private LinearLayout.LayoutParams tableParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                contentWidth > 0 ? contentWidth : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int gap = UiKit.dp(getContext(), 8);
        params.topMargin = gap;
        params.bottomMargin = gap;
        return params;
    }
}
