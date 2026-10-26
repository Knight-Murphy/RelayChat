package com.relaychat.app.mathtext;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.Spanned;
import android.text.style.SubscriptSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.relaychat.app.ui.MarkdownRenderer;
import com.relaychat.app.ui.MarkdownView;

import java.io.File;
import java.io.FileOutputStream;

public final class MarkdownInstrumentation extends Instrumentation {
    private static final String SAMPLE =
            "故：\n\n"
                    + "- A_0\\sim A_2 并行接到 8 片 RAM 的地址端；\n"
                    + "- 高位地址 A_13、A_14 接 74138 的输入端；\n"
                    + "- 74138 输出 Y_0\\sim Y_7 分别接 8 片 RAM 的片选端；\n\n"
                    + "| 芯片 | 片选信号 | 地址范围 |\n"
                    + "| --- | --- | --- |\n"
                    + "| RAM0 | Y_0 | 0000H\\sim1FFFH |\n"
                    + "| RAM1 | Y_1 | 2000H\\sim3FFFH |\n"
                    + "| RAM2 | Y_2 | 4000H\\sim5FFFH |\n"
                    + "| RAM3 | Y_3 | 6000H\\sim7FFFH |\n"
                    + "| RAM4 | Y_4 | 8000H\\sim9FFFH |\n"
                    + "| RAM5 | Y_5 | A000H\\sim BFFFH |\n"
                    + "| RAM6 | Y_6 | C000H\\sim DFFFH |\n"
                    + "| RAM7 | Y_7 | E000H\\sim FFFFH |\n"
                    + "| RAM8 | Y_8 | 10000H\\sim 11FFFH |\n"
                    + "| RAM9 | Y_9 | 12000H\\sim 13FFFH |\n";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            verifySubscripts();
            result.putString("stream", "PASS: numeric subscripts render as spans without tofu\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifySubscripts() throws Exception {
        int width = 1080;
        Bitmap[] rendered = new Bitmap[1];
        StringBuilder visible = new StringBuilder();
        boolean[] subscriptDigits = new boolean[10];
        runOnMainSync(() -> {
            MarkdownView view = new MarkdownView(getTargetContext());
            view.setContentWidth(width);
            view.setMarkdown(SAMPLE, MarkdownRenderer.Palette.assistant(1f));
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST));
            view.layout(0, 0, width, view.getMeasuredHeight());
            collectText(view, visible, subscriptDigits);
            rendered[0] = Bitmap.createBitmap(width, view.getMeasuredHeight(),
                    Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(rendered[0]));
        });

        require(rendered[0].getHeight() > 0, "Rendered markdown is blank");
        save(rendered[0]);
        char leftover = firstUnicodeSubscript(visible);
        require(leftover == 0, "Unicode subscript U+" + Integer.toHexString(leftover)
                + " survived rendering: " + visible);
        for (int digit = 0; digit <= 9; digit++) {
            require(visible.indexOf("Y" + digit) >= 0, "Missing rendered subscript Y" + digit);
            require(subscriptDigits[digit], "Y" + digit + " was not rendered with SubscriptSpan");
        }
        rendered[0].recycle();
    }

    private static void collectText(View view, StringBuilder text, boolean[] subscriptDigits) {
        if (view instanceof TextView) {
            CharSequence content = ((TextView) view).getText();
            text.append(content).append('\n');
            if (content instanceof Spanned) {
                Spanned spanned = (Spanned) content;
                for (SubscriptSpan span : spanned.getSpans(
                        0, content.length(), SubscriptSpan.class)) {
                    int start = spanned.getSpanStart(span);
                    int end = spanned.getSpanEnd(span);
                    for (int index = start; index < end; index++) {
                        char value = content.charAt(index);
                        if (value >= '0' && value <= '9') {
                            subscriptDigits[value - '0'] = true;
                        }
                    }
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectText(group.getChildAt(index), text, subscriptDigits);
            }
        }
    }

    private static char firstUnicodeSubscript(CharSequence text) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value >= '\u2080' && value <= '\u208e') {
                return value;
            }
        }
        return 0;
    }

    private void save(Bitmap bitmap) throws Exception {
        File directory = new File(getTargetContext().getExternalFilesDir(null), "mathtext-tests");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create screenshot directory");
        }
        try (FileOutputStream output = new FileOutputStream(
                new File(directory, "subscript-rendering.png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
