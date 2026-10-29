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
    private static final int CHILD_TEXT = 0;
    private static final int CHILD_TABLE = 1;
    private static final int CHILD_DIAGRAM = 2;
    private static final int CHILD_CODE = 3;

    private final List<View> children = new ArrayList<>();
    private final List<Integer> childTypes = new ArrayList<>();
    private final List<String> childKeys = new ArrayList<>();
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
        TextView view = (TextView) reusable(0, CHILD_TEXT, "");
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
            MarkdownText.Block block = blocks.get(index);
            if (block.kind == MarkdownText.TABLE) {
                TableGridView grid = (TableGridView) reusable(used++, CHILD_TABLE, "");
                grid.setLayoutParams(tableParams());
                grid.bind(block.table, palette, contentWidth,
                        TableGridView.CELL_TEXT_SP, tableOpener);
                index++;
                continue;
            }
            LocalDiagramView.Kind diagramKind = diagramKind(block);
            if (diagramKind != null) {
                String diagramSource = LocalDiagramView.sourceOf(block);
                String key = diagramKind.name() + "\n" + diagramSource;
                DiagramBlockView diagram =
                        (DiagramBlockView) reusable(used++, CHILD_DIAGRAM, key);
                diagram.setLayoutParams(diagramParams());
                diagram.setContentDescription("\u6253\u5f00" + diagramKind.name() + "\u56fe\u793a");
                index++;
                continue;
            }
            if (isCollapsibleCode(block)) {
                String codeSource = LocalDiagramView.sourceOf(block);
                String key = block.language + "\n" + codeSource;
                CollapsibleCodeBlockView code =
                        (CollapsibleCodeBlockView) reusable(used++, CHILD_CODE, key);
                code.setLayoutParams(codeParams());
                code.bind(block.language, codeSource, contentWidth);
                index++;
                continue;
            }
            int start = index;
            while (index < blocks.size()
                    && blocks.get(index).kind != MarkdownText.TABLE
                    && diagramKind(blocks.get(index)) == null
                    && !isCollapsibleCode(blocks.get(index))) {
                index++;
            }
            TextView text = (TextView) reusable(used++, CHILD_TEXT, "");
            applyTextWidth(text);
            text.setTextColor(palette.text);
            text.setText(MarkdownRenderer.render(blocks.subList(start, index), palette));
        }
        trimFrom(used);
    }

    /** Keeps one view per block run, so a streaming reply reuses views instead of rebuilding them. */
    private View reusable(int index, int type, String key) {
        if (index < children.size()) {
            if (childTypes.get(index) == type && childKeys.get(index).equals(key)) {
                return children.get(index);
            }
            trimFrom(index);
        }
        View created;
        if (type == CHILD_TABLE) {
            created = new TableGridView(getContext());
        } else if (type == CHILD_DIAGRAM) {
            int split = key.indexOf('\n');
            String kindName = split < 0 ? key : key.substring(0, split);
            String diagramSource = split < 0 ? "" : key.substring(split + 1);
            created = new DiagramBlockView(getContext(),
                    LocalDiagramView.Kind.valueOf(kindName), diagramSource, palette);
        } else if (type == CHILD_CODE) {
            created = new CollapsibleCodeBlockView(getContext());
        } else {
            TextView text = UiKit.text(getContext(), "", BODY_TEXT_SP, palette.text);
            text.setTextIsSelectable(true);
            created = text;
        }
        childTypes.add(type);
        childKeys.add(key);
        children.add(created);
        addView(created, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return created;
    }

    private void trimFrom(int index) {
        while (children.size() > index) {
            int last = children.size() - 1;
            View removed = children.remove(last);
            if (removed instanceof DiagramBlockView) {
                ((DiagramBlockView) removed).release();
            }
            removeView(removed);
            childTypes.remove(last);
            childKeys.remove(last);
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

    private LinearLayout.LayoutParams diagramParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                contentWidth > 0 ? contentWidth : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int gap = UiKit.dp(getContext(), 8);
        params.topMargin = gap;
        params.bottomMargin = gap;
        return params;
    }

    private LinearLayout.LayoutParams codeParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                contentWidth > 0 ? contentWidth : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int gap = UiKit.dp(getContext(), 8);
        params.topMargin = gap;
        params.bottomMargin = gap;
        return params;
    }

    private static LocalDiagramView.Kind diagramKind(MarkdownText.Block block) {
        if (block == null || block.kind != MarkdownText.CODE_BLOCK || !block.closed) {
            return null;
        }
        LocalDiagramView.Kind kind = LocalDiagramView.kindFor(block.language);
        if (kind == null || LocalDiagramView.sourceOf(block).trim().isEmpty()) {
            return null;
        }
        return kind;
    }

    private static boolean isCollapsibleCode(MarkdownText.Block block) {
        return block != null && block.kind == MarkdownText.CODE_BLOCK
                && diagramKind(block) == null;
    }
}
