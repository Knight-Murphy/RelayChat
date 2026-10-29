package com.relaychat.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen, zoomable reader for a locally rendered diagram. */
public final class DiagramViewerDialog extends Dialog {
    private DiagramViewerDialog(Context context, LocalDiagramView.Kind kind, String source,
                                MarkdownRenderer.Palette palette) {
        super(context);
        int padding = UiKit.dp(context, 12);
        LinearLayout root = UiKit.vertical(context);
        root.setBackgroundColor(UiKit.CANVAS);
        root.setPadding(padding, padding, padding, padding);

        LinearLayout bar = UiKit.horizontal(context);
        TextView title = UiKit.heading(context, titleFor(kind), 16);
        bar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = UiKit.iconButton(context, UiKit.Icon.CLOSE, true);
        close.setContentDescription("\u5173\u95ed");
        close.setOnClickListener(view -> dismiss());
        bar.addView(close);
        root.addView(bar);

        LocalDiagramView diagram =
                new LocalDiagramView(context, kind, source, palette, true);
        LinearLayout.LayoutParams diagramParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        diagramParams.topMargin = UiKit.dp(context, 8);
        root.addView(diagram, diagramParams);
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

    public static DiagramViewerDialog show(Context context, LocalDiagramView.Kind kind,
                                           String source,
                                           MarkdownRenderer.Palette palette) {
        if (context == null || kind == null || source == null || source.trim().isEmpty()) {
            return null;
        }
        DiagramViewerDialog dialog = new DiagramViewerDialog(context, kind, source, palette);
        dialog.show();
        return dialog;
    }

    private static String titleFor(LocalDiagramView.Kind kind) {
        switch (kind) {
            case SVG:
                return "SVG \u56fe\u793a";
            case MERMAID:
                return "Mermaid \u56fe\u793a";
            case WAVEFORM:
            default:
                return "\u6ce2\u5f62\u56fe";
        }
    }
}
