package com.relaychat.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** A fenced code block that starts collapsed so long model-generated source stays scannable. */
public final class CollapsibleCodeBlockView extends LinearLayout {
    private final TextView title;
    private final TextView summary;
    private final ImageView chevron;
    private final TextView code;
    private boolean expanded;
    private String source = "";
    private String language = "";

    public CollapsibleCodeBlockView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClickable(true);
        setFocusable(true);
        setBackground(UiKit.rounded(UiKit.FIELD, UiKit.dp(context, 8),
                UiKit.BORDER, UiKit.dp(context, 1)));
        setOnClickListener(view -> {
            expanded = !expanded;
            refreshExpandedState();
        });

        LinearLayout header = UiKit.horizontal(context);
        header.setPadding(UiKit.dp(context, 12), UiKit.dp(context, 9),
                UiKit.dp(context, 10), UiKit.dp(context, 9));
        header.setMinimumHeight(UiKit.dp(context, 48));
        header.addView(UiKit.iconImage(context, UiKit.Icon.TERMINAL, UiKit.INK),
                new LinearLayout.LayoutParams(UiKit.dp(context, 22), UiKit.dp(context, 22)));

        LinearLayout labels = UiKit.vertical(context);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = UiKit.dp(context, 10);
        title = UiKit.text(context, "", 14, UiKit.INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title);
        summary = UiKit.text(context, "", 12, UiKit.MUTED);
        labels.addView(summary);
        header.addView(labels, labelParams);

        chevron = UiKit.iconImage(context, UiKit.Icon.CHEVRON_DOWN, UiKit.MUTED);
        header.addView(chevron, new LinearLayout.LayoutParams(
                UiKit.dp(context, 24), UiKit.dp(context, 24)));
        addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setFillViewport(false);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setPadding(UiKit.dp(context, 10), 0, UiKit.dp(context, 10),
                UiKit.dp(context, 10));
        code = UiKit.text(context, "", 12, UiKit.INK);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextIsSelectable(true);
        code.setLineSpacing(UiKit.dp(context, 2), 1f);
        code.setHorizontallyScrolling(true);
        code.setPadding(UiKit.dp(context, 10), UiKit.dp(context, 8),
                UiKit.dp(context, 10), UiKit.dp(context, 8));
        code.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(context, 6),
                UiKit.BORDER, UiKit.dp(context, 1)));
        code.setOnLongClickListener(view -> {
            copySource();
            return true;
        });
        scroller.addView(code, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroller);
        setTag(scroller);
        scroller.setVisibility(GONE);
    }

    public void bind(String language, String value, int widthPx) {
        source = value == null ? "" : value;
        this.language = language == null ? "" : language.trim();
        setVisibility(source.isEmpty() ? GONE : VISIBLE);
        if (source.isEmpty()) {
            return;
        }
        String label = this.language.isEmpty() ? "代码" : displayLanguage(this.language) + " 代码";
        title.setText(label);
        int lines = source.split("\\n", -1).length;
        summary.setText(lines + " 行");
        code.setText(source);
        if (widthPx > 0) {
            setMinimumWidth(0);
            code.setMaxWidth(widthPx);
        }
        expanded = false;
        refreshExpandedState();
    }

    private void refreshExpandedState() {
        ViewGroup details = (ViewGroup) getTag();
        if (details != null) {
            details.setVisibility(expanded ? VISIBLE : GONE);
        }
        chevron.setImageDrawable(UiKit.iconImage(getContext(),
                expanded ? UiKit.Icon.CHEVRON_UP : UiKit.Icon.CHEVRON_DOWN,
                UiKit.MUTED).getDrawable());
        setContentDescription(title.getText() + "，" + summary.getText() + "，"
                + (expanded ? "点击收起代码" : "点击展开代码"));
    }

    private void copySource() {
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    language.isEmpty() ? "code" : language, source));
            Toast.makeText(getContext(), "代码已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private static String displayLanguage(String value) {
        if ("js".equalsIgnoreCase(value) || "javascript".equalsIgnoreCase(value)) {
            return "JavaScript";
        }
        if ("ts".equalsIgnoreCase(value) || "typescript".equalsIgnoreCase(value)) {
            return "TypeScript";
        }
        if ("py".equalsIgnoreCase(value) || "python".equalsIgnoreCase(value)) {
            return "Python";
        }
        if ("json".equalsIgnoreCase(value)) {
            return "JSON";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
