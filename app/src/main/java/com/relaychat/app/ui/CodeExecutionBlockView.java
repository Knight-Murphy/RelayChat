package com.relaychat.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.relaychat.app.model.CodeExecution;
import com.relaychat.app.model.ImageAttachment;

import java.util.ArrayList;
import java.util.List;

/** Collapsible record of one local Python execution and its generated figures. */
public final class CodeExecutionBlockView extends LinearLayout {
    private final LinearLayout header;
    private final TextView status;
    private final ImageView chevron;
    private final LinearLayout details;
    private final TextView code;
    private final TextView logs;
    private final LinearLayout images;
    private final List<Bitmap> bitmaps = new ArrayList<>();
    private CodeExecution execution;
    private boolean expanded;
    private String renderedKey = "";

    public CodeExecutionBlockView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(UiKit.rounded(UiKit.FIELD, UiKit.dp(context, 8),
                UiKit.BORDER, UiKit.dp(context, 1)));

        header = UiKit.horizontal(context);
        header.setPadding(UiKit.dp(context, 12), UiKit.dp(context, 10),
                UiKit.dp(context, 10), UiKit.dp(context, 10));
        header.setMinimumHeight(UiKit.dp(context, 52));
        header.addView(UiKit.iconImage(context, UiKit.Icon.TERMINAL, UiKit.INK),
                new LinearLayout.LayoutParams(UiKit.dp(context, 24), UiKit.dp(context, 24)));

        LinearLayout labels = UiKit.vertical(context);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = UiKit.dp(context, 10);
        TextView title = UiKit.text(context, "执行 Python 代码", 15, UiKit.INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title);
        status = UiKit.text(context, "", 12, UiKit.MUTED);
        labels.addView(status, UiKit.matchWrap(UiKit.dp(context, 2), 0));
        header.addView(labels, labelParams);

        chevron = UiKit.iconImage(context, UiKit.Icon.CHEVRON_DOWN, UiKit.MUTED);
        header.addView(chevron, new LinearLayout.LayoutParams(
                UiKit.dp(context, 24), UiKit.dp(context, 24)));
        header.setOnClickListener(view -> {
            expanded = !expanded;
            refreshExpandedState();
        });
        addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        details = UiKit.vertical(context);
        details.setPadding(UiKit.dp(context, 12), 0, UiKit.dp(context, 12),
                UiKit.dp(context, 10));
        code = monospace(context);
        details.addView(code, UiKit.matchWrap(0, UiKit.dp(context, 8)));
        logs = monospace(context);
        logs.setTextColor(UiKit.MUTED);
        details.addView(logs, UiKit.matchWrap(0, 0));
        addView(details);

        images = UiKit.vertical(context);
        images.setPadding(UiKit.dp(context, 10), 0, UiKit.dp(context, 10),
                UiKit.dp(context, 10));
        addView(images);
        setVisibility(GONE);
    }

    public void bind(CodeExecution value, int widthPx, MarkdownRenderer.Palette palette) {
        if (value == null || (value.getCode().trim().isEmpty()
                && value.getLogs().trim().isEmpty() && value.getImages().isEmpty())) {
            execution = null;
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        execution = value;
        status.setText(statusText(value));
        String key = value.getId() + "\n" + value.getStatus() + "\n"
                + value.getCode().hashCode() + "\n" + value.getLogs().hashCode() + "\n"
                + imageKey(value.getImages());
        if (!key.equals(renderedKey)) {
            renderedKey = key;
            code.setText(value.getCode());
            code.setVisibility(value.getCode().trim().isEmpty() ? GONE : VISIBLE);
            logs.setText(value.getLogs());
            logs.setVisibility(value.getLogs().trim().isEmpty() ? GONE : VISIBLE);
            bindImages(value.getImages(), widthPx);
        }
        header.setContentDescription(statusText(value)
                + (expanded ? "，点击收起代码" : "，点击展开代码"));
        refreshExpandedState();
    }

    private TextView monospace(Context context) {
        TextView view = UiKit.text(context, "", 12, UiKit.INK);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        view.setLineSpacing(UiKit.dp(context, 2), 1f);
        view.setPadding(UiKit.dp(context, 10), UiKit.dp(context, 8),
                UiKit.dp(context, 10), UiKit.dp(context, 8));
        view.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(context, 6),
                UiKit.BORDER, UiKit.dp(context, 1)));
        return view;
    }

    private void bindImages(List<ImageAttachment> values, int widthPx) {
        images.removeAllViews();
        recycleBitmaps();
        if (values == null || values.isEmpty()) {
            images.setVisibility(GONE);
            return;
        }
        images.setVisibility(VISIBLE);
        int maxWidth = widthPx > UiKit.dp(getContext(), 32)
                ? widthPx - UiKit.dp(getContext(), 20) : UiKit.dp(getContext(), 560);
        for (int index = 0; index < values.size(); index++) {
            ImageAttachment attachment = values.get(index);
            Bitmap bitmap = decode(attachment.getData(), maxWidth);
            if (bitmap == null) {
                TextView failed = UiKit.text(getContext(), "图片输出无法显示", 13, UiKit.WARM);
                failed.setGravity(Gravity.CENTER);
                failed.setPadding(0, UiKit.dp(getContext(), 16), 0, UiKit.dp(getContext(), 16));
                images.addView(failed, UiKit.matchWrap(index == 0 ? 0 : 8, 0));
                continue;
            }
            bitmaps.add(bitmap);
            ImageView image = new ImageView(getContext());
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setMaxHeight(UiKit.dp(getContext(), 520));
            image.setImageBitmap(bitmap);
            image.setContentDescription("打开 Python 输出图片 " + (index + 1));
            image.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(getContext(), 6),
                    UiKit.BORDER, UiKit.dp(getContext(), 1)));
            image.setOnClickListener(view -> ImageViewerDialog.show(getContext(), attachment));
            images.addView(image, UiKit.matchWrap(index == 0 ? 0 : 8, 0));
        }
    }

    private void refreshExpandedState() {
        details.setVisibility(expanded ? VISIBLE : GONE);
        chevron.setImageDrawable(UiKit.iconImage(getContext(),
                expanded ? UiKit.Icon.CHEVRON_UP : UiKit.Icon.CHEVRON_DOWN,
                UiKit.MUTED).getDrawable());
        if (execution != null) {
            header.setContentDescription(statusText(execution)
                    + (expanded ? "，点击收起代码" : "，点击展开代码"));
        }
    }

    private static String statusText(CodeExecution value) {
        if (value.isCompleted()) {
            return "已完成";
        }
        if ("in_progress".equalsIgnoreCase(value.getStatus())
                || "interpreting".equalsIgnoreCase(value.getStatus())) {
            return "正在执行";
        }
        if ("failed".equalsIgnoreCase(value.getStatus())) {
            return "执行失败";
        }
        return "执行记录";
    }

    private static String imageKey(List<ImageAttachment> values) {
        StringBuilder result = new StringBuilder();
        for (ImageAttachment image : values) {
            result.append(image.getId()).append(':');
        }
        return result.toString();
    }

    private static Bitmap decode(byte[] data, int maxWidth) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            int target = Math.max(1, maxWidth);
            while (bounds.outWidth / (sample * 2) >= target) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception | OutOfMemoryError error) {
            return null;
        }
    }

    private void recycleBitmaps() {
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        bitmaps.clear();
    }
}
