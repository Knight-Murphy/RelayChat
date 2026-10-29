package com.relaychat.app.diagrams;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import com.relaychat.app.data.ConversationStore;
import com.relaychat.app.MainActivity;
import com.relaychat.app.model.ChatMessage;
import com.relaychat.app.model.Conversation;
import com.relaychat.app.model.DrawingProtocol;
import com.relaychat.app.ui.DiagramBlockView;
import com.relaychat.app.ui.DiagramViewerDialog;
import com.relaychat.app.ui.LocalDiagramView;
import com.relaychat.app.ui.MarkdownRenderer;
import com.relaychat.app.ui.MarkdownView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class DiagramInstrumentation extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            verifyDrawingProtocol();
            verifyDiagrams();
            seedConversation();
            verifyNarrowWaveform();
            verifyFullScreenWaveform();
            result.putString("stream",
                    "PASS: SVG, Mermaid, responsive waveform and full-screen rendering\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static void verifyDrawingProtocol() {
        String prompt = DrawingProtocol.appendTo("Keep answers concise.");
        require(prompt.startsWith("Keep answers concise."),
                "The provider prompt was not preserved");
        require(prompt.contains("```svg") && prompt.contains("```mermaid")
                        && prompt.contains("```waveform"),
                "The drawing protocol is missing a supported format");
    }

    private void verifyDiagrams() throws Exception {
        int width = 900;
        Bitmap[] rendered = new Bitmap[1];
        List<DiagramBlockView> diagrams = new ArrayList<>();
        runOnMainSync(() -> {
            MarkdownView view = new MarkdownView(getTargetContext());
            view.setContentWidth(width);
            view.setMarkdown(DiagramSample.MARKDOWN,
                    MarkdownRenderer.Palette.assistant(
                            getTargetContext().getResources().getDisplayMetrics().density));
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST));
            view.layout(0, 0, width, view.getMeasuredHeight());
            collect(view, diagrams);
            rendered[0] = Bitmap.createBitmap(width, view.getMeasuredHeight(),
                    Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(rendered[0]));
        });

        require(diagrams.size() == 3, "Expected 3 diagram views, found " + diagrams.size());
        require(diagrams.get(0).getDiagramView().getKind() == LocalDiagramView.Kind.SVG,
                "First diagram was not SVG");
        require(diagrams.get(1).getDiagramView().getKind() == LocalDiagramView.Kind.MERMAID,
                "Second diagram was not Mermaid");
        require(diagrams.get(2).getDiagramView().getKind() == LocalDiagramView.Kind.WAVEFORM,
                "Third diagram was not a waveform");
        require(rendered[0].getHeight() > 0, "Diagram markdown measured as blank");
        save(rendered[0]);

        waitFor(
                diagrams.get(0).getDiagramView(),
                "document.querySelector('#fallback').style.display !== 'block' && "
                        + "!!document.querySelector('#diagram svg')",
                "SVG did not render");
        waitFor(
                diagrams.get(1).getDiagramView(),
                "document.querySelector('#fallback').style.display !== 'block' && "
                        + "!!document.querySelector('#diagram svg')",
                "Mermaid did not render");
        waitFor(
                diagrams.get(2).getDiagramView(),
                "document.querySelector('#fallback').style.display !== 'block' && "
                        + "!!window.__relaychatChartMetrics",
                "Waveform did not render");
        JSONObject metrics = chartMetrics(diagrams.get(2).getDiagramView());
        require(metrics.getDouble("legendTop") > metrics.getDouble("xTickLabelY") + 14,
                "Waveform legend overlaps the x-axis labels");
        verifyXLabelsDoNotOverlap(metrics);
        verifyInvalidSvgFallback();
        rendered[0].recycle();
    }

    private void verifyNarrowWaveform() throws Exception {
        float density = getTargetContext().getResources().getDisplayMetrics().density;
        int width = Math.round(330 * density);
        List<DiagramBlockView> diagrams = new ArrayList<>();
        runOnMainSync(() -> {
            MarkdownView view = new MarkdownView(getTargetContext());
            view.setContentWidth(width);
            view.setMarkdown("```waveform\n" + DiagramSample.ARCTAN_WAVEFORM + "\n```",
                    MarkdownRenderer.Palette.assistant(density));
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST));
            view.layout(0, 0, width, view.getMeasuredHeight());
            collect(view, diagrams);
        });
        require(diagrams.size() == 1, "Narrow arctangent chart was not created");
        LocalDiagramView chart = diagrams.get(0).getDiagramView();
        waitFor(chart, "!!window.__relaychatChartMetrics",
                "Narrow arctangent chart did not render");
        JSONObject metrics = chartMetrics(chart);
        require(metrics.getDouble("canvasHeight") >= 180,
                "Narrow chart collapsed vertically");
        require(metrics.getDouble("plotHeight") > 80,
                "Narrow chart plot area is too short");
        require(metrics.getDouble("xLabelY") <
                        metrics.getDouble("canvasHeight") - 4,
                "Narrow chart x-axis title is clipped");
        verifyXLabelsDoNotOverlap(metrics);
    }

    private void verifyFullScreenWaveform() throws Exception {
        MainActivity activity = (MainActivity) startActivitySync(new Intent(
                getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        DiagramViewerDialog[] holder = new DiagramViewerDialog[1];
        runOnMainSync(() -> holder[0] = DiagramViewerDialog.show(
                activity, LocalDiagramView.Kind.WAVEFORM, DiagramSample.ARCTAN_WAVEFORM,
                MarkdownRenderer.Palette.assistant(
                        getTargetContext().getResources().getDisplayMetrics().density)));
        require(holder[0] != null, "Full-screen waveform dialog did not open");

        List<LocalDiagramView> views = new ArrayList<>();
        runOnMainSync(() -> collectLocalDiagrams(
                holder[0].getWindow().getDecorView(), views));
        require(!views.isEmpty(), "Full-screen waveform WebView is missing");
        LocalDiagramView chart = views.get(0);
        waitFor(chart, "!!window.__relaychatChartMetrics",
                "Full-screen waveform did not render");
        JSONObject metrics = chartMetrics(chart);
        require(metrics.getDouble("canvasHeight") > 500,
                "Full-screen waveform did not fill the screen");
        require(metrics.getDouble("plotHeight") >
                        metrics.getDouble("canvasHeight") * 0.7,
                "Full-screen plot area is too short");
        require(metrics.getDouble("xLabelY") <
                        metrics.getDouble("canvasHeight") - 4,
                "Full-screen x-axis title is clipped");
        verifyXLabelsDoNotOverlap(metrics);
        waitForIdleSync();
        SystemClock.sleep(350);
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        require(screenshot != null, "Full-screen screenshot unavailable");
        save(screenshot, "diagram-fullscreen.png");
        screenshot.recycle();

        runOnMainSync(() -> {
            holder[0].dismiss();
            activity.finish();
        });
    }

    private static void verifyXLabelsDoNotOverlap(JSONObject metrics) throws Exception {
        JSONArray labels = metrics.getJSONArray("xTickLabels");
        require(labels.length() >= 3, "Waveform has too few x-axis labels");
        double previousRight = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < labels.length(); index++) {
            JSONObject label = labels.getJSONObject(index);
            double center = label.getDouble("center");
            double halfWidth = label.getDouble("width") / 2;
            require(center - halfWidth > previousRight + 2,
                    "Waveform x-axis labels overlap at " + label.getString("label"));
            previousRight = center + halfWidth;
        }
    }

    private JSONObject chartMetrics(LocalDiagramView view) throws Exception {
        String result = evaluate(
                view, "JSON.stringify(window.__relaychatChartMetrics)");
        Object decoded = new JSONTokener(result).nextValue();
        return new JSONObject(decoded instanceof String ? (String) decoded : result);
    }

    private void verifyInvalidSvgFallback() throws Exception {
        List<DiagramBlockView> diagrams = new ArrayList<>();
        runOnMainSync(() -> {
            MarkdownView view = new MarkdownView(getTargetContext());
            view.setContentWidth(900);
            view.setMarkdown("```svg\n<svg><g></svg>\n```",
                    MarkdownRenderer.Palette.assistant(
                            getTargetContext().getResources().getDisplayMetrics().density));
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST));
            view.layout(0, 0, 900, view.getMeasuredHeight());
            collect(view, diagrams);
        });
        require(diagrams.size() == 1, "Invalid SVG did not create a fallback container");
        waitFor(
                diagrams.get(0).getDiagramView(),
                "document.querySelector('#fallback').style.display === 'block' && "
                        + "document.querySelector('#fallback').textContent.includes('<svg>')",
                "Invalid SVG did not fall back to its source");
    }

    private void seedConversation() {
        Conversation conversation = new Conversation("diagram-preview");
        conversation.setTitle("本地绘图测试");
        conversation.getMessages().add(new ChatMessage(
                ChatMessage.ROLE_USER, "请画出单相可控整流电路、流程和波形。"));
        conversation.getMessages().add(new ChatMessage(
                ChatMessage.ROLE_ASSISTANT, DiagramSample.MARKDOWN));
        ConversationStore store = new ConversationStore(getTargetContext());
        store.write(store.encode(Collections.singletonList(conversation), conversation.getId()));
    }

    private void waitFor(LocalDiagramView view, String expression, String failure)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            String[] value = new String[1];
            CountDownLatch latch = new CountDownLatch(1);
            runOnMainSync(() -> view.evaluateJavascript(expression, result -> {
                value[0] = result;
                latch.countDown();
            }));
            if (latch.await(5, TimeUnit.SECONDS) && "true".equals(value[0])) {
                return;
            }
            SystemClock.sleep(250);
        }
        throw new AssertionError(failure + ": " + evaluate(
                view, "document.getElementById('fallback').textContent"));
    }

    private String evaluate(LocalDiagramView view, String expression) throws Exception {
        String[] value = new String[1];
        CountDownLatch latch = new CountDownLatch(1);
        runOnMainSync(() -> view.evaluateJavascript(expression, result -> {
            value[0] = result;
            latch.countDown();
        }));
        latch.await(5, TimeUnit.SECONDS);
        return value[0] == null ? "<no result>" : value[0];
    }

    private static void collect(View view, List<DiagramBlockView> diagrams) {
        if (view instanceof DiagramBlockView) {
            diagrams.add((DiagramBlockView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collect(group.getChildAt(index), diagrams);
            }
        }
    }

    private void save(Bitmap bitmap) throws Exception {
        save(bitmap, "diagram-rendering.png");
    }

    private void save(Bitmap bitmap, String name) throws Exception {
        File directory = new File(getTargetContext().getExternalFilesDir(null), "diagram-tests");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create screenshot directory");
        }
        try (FileOutputStream output = new FileOutputStream(
                new File(directory, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }

    private static void collectLocalDiagrams(View view, List<LocalDiagramView> diagrams) {
        if (view instanceof LocalDiagramView) {
            diagrams.add((LocalDiagramView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectLocalDiagrams(group.getChildAt(index), diagrams);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
