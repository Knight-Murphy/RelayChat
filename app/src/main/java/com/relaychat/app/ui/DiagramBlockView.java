package com.relaychat.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

/** A compact diagram in the conversation; tapping opens an interactive full-screen copy. */
public final class DiagramBlockView extends FrameLayout {
    private final LocalDiagramView.Kind kind;
    private final String source;
    private final MarkdownRenderer.Palette palette;
    private final LocalDiagramView diagram;

    public DiagramBlockView(Context context, LocalDiagramView.Kind kind, String source,
                            MarkdownRenderer.Palette palette) {
        super(context);
        this.kind = kind;
        this.source = source == null ? "" : source;
        this.palette = palette == null
                ? MarkdownRenderer.Palette.assistant(context.getResources().getDisplayMetrics().density)
                : palette;
        setBackground(UiKit.rounded(Color.WHITE, UiKit.dp(context, 8), UiKit.BORDER,
                UiKit.dp(context, 1)));
        setClipToOutline(true);
        setContentDescription("\u56fe\u793a");

        diagram = new LocalDiagramView(context, this.kind, this.source, this.palette, false);
        addView(diagram, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        View touchLayer = new View(context);
        touchLayer.setBackgroundColor(Color.TRANSPARENT);
        touchLayer.setClickable(true);
        touchLayer.setFocusable(true);
        touchLayer.setContentDescription("\u6253\u5f00" + kind.name() + "\u56fe\u793a");
        touchLayer.setOnClickListener(view -> DiagramViewerDialog.show(
                getContext(), this.kind, this.source, this.palette));
        touchLayer.setOnLongClickListener(view -> {
            copySource();
            return true;
        });
        addView(touchLayer, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = Math.round(width * 0.68f);
        int minHeight = UiKit.dp(getContext(), 230);
        int maxHeight = UiKit.dp(getContext(), 360);
        int height = Math.max(minHeight, Math.min(maxHeight, desiredHeight));
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        }
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    public void release() {
        diagram.release();
    }

    public LocalDiagramView getDiagramView() {
        return diagram;
    }

    private void copySource() {
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(kind.name(), source));
        Toast.makeText(getContext(), "\u5df2\u590d\u5236\u7ed8\u56fe\u6e90\u7801",
                Toast.LENGTH_SHORT).show();
    }
}
