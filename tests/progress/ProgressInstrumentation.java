package com.relaychat.app.progress;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.relaychat.app.MainActivity;
import com.relaychat.app.data.ConversationStore;
import com.relaychat.app.model.ChatMessage;
import com.relaychat.app.model.Conversation;
import com.relaychat.app.model.ProviderProfile;
import com.relaychat.app.network.ApiClient;
import com.relaychat.app.ui.ChatScreen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Uses in-process HTTP fixtures; never contacts a relay or requires API keys. */
public final class ProgressInstrumentation extends Instrumentation {
    private final BlockingQueue<InputStream> responses = new LinkedBlockingQueue<>();
    private final List<ByteArrayOutputStream> requests = Collections.synchronizedList(new ArrayList<>());
    private int passed;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol)
                    ? new URLStreamHandler() {
                        @Override
                        protected HttpURLConnection openConnection(URL url) throws IOException {
                            if (!"progress.invalid".equals(url.getHost())) {
                                throw new IOException("Test disallows external network access");
                            }
                            InputStream response = responses.poll();
                            if (response == null) {
                                throw new IOException("Unexpected extra request");
                            }
                            ByteArrayOutputStream body = new ByteArrayOutputStream();
                            requests.add(body);
                            return new HttpURLConnection(url) {
                                @Override public void connect() {}
                                @Override public boolean usingProxy() { return false; }
                                @Override public void disconnect() {
                                    try { response.close(); } catch (IOException ignored) {}
                                }
                                @Override public int getResponseCode() {
                                    return response instanceof FixtureStream
                                            ? ((FixtureStream) response).status : 200;
                                }
                                @Override public String getContentType() {
                                    return response instanceof FixtureStream
                                            ? ((FixtureStream) response).contentType : "text/event-stream";
                                }
                                @Override public InputStream getErrorStream() { return response; }
                                @Override public InputStream getInputStream() { return response; }
                                @Override public OutputStream getOutputStream() { return body; }
                            };
                        }
                    } : null);
            testChatReasoning();
            testResponsesReasoning();
            testFallback();
            testReasoningOnly();
            testReasoningDetails();
            testSummarySnapshots();
            testCompletedSnapshot();
            testSummaryUnsupported();
            testJsonStreamResponse();
            testPersistence();
            testLiveScreen();
            result.putString("stream", "PASS: " + passed + " progress scenarios\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private ProviderProfile provider(String mode) {
        return new ProviderProfile("progress", "测试服务", "https://progress.invalid/v1", "",
                mode, Collections.singletonList("test-model"), "test-model", "");
    }

    private static String chatEvent(String fields) {
        return "data: {\"choices\":[{\"delta\":{" + fields + "}}]}\n\n";
    }

    private void enqueue(String body) {
        responses.add(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private Capture runReply(String mode) throws Exception {
        ApiClient client = new ApiClient();
        Capture capture = new Capture();
        try {
            client.streamChat(provider(mode),
                    Collections.singletonList(new ChatMessage(ChatMessage.ROLE_USER, "你好")), capture);
            check(capture.done.await(5, TimeUnit.SECONDS), "Reply timed out");
            return capture;
        } finally {
            client.shutdown();
        }
    }

    private void testChatReasoning() throws Exception {
        enqueue(chatEvent("\"reasoning_content\":\"先分析。\",\"content\":null")
                + chatEvent("\"reasoning\":\"再检查。\"")
                + chatEvent("\"content\":\"你好！\"") + "data: [DONE]\n\n");
        Capture capture = runReply(ProviderProfile.MODE_CHAT);
        check("先分析。再检查。".equals(capture.reasoning.toString()), "Chat reasoning was lost");
        check("你好！".equals(capture.answer.toString()), "Reasoning leaked into answer");
        check(capture.error.isEmpty(), "Chat reply failed");
        check(capture.stages.equals(java.util.Arrays.asList(
                ChatMessage.STAGE_CONNECTING, ChatMessage.STAGE_WAITING)), "Wrong request stages");
        passed++;
    }

    private void testResponsesReasoning() throws Exception {
        int before = requests.size();
        enqueue("data: {\"type\":\"response.output_item.added\",\"item\":{\"type\":\"reasoning\"}}\n\n"
                + "data: {\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"思考摘要。\"}\n\n"
                + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"正式回答。\"}\n\n"
                + "data: {\"type\":\"response.completed\"}\n\n");
        Capture capture = runReply(ProviderProfile.MODE_RESPONSES);
        check("思考摘要。".equals(capture.reasoning.toString()), "Responses summary was lost");
        check("正式回答。".equals(capture.answer.toString()), "Responses answer was lost");
        check(capture.stages.contains(ChatMessage.STAGE_THINKING), "Reasoning stage missing");
        JSONObject payload = new JSONObject(requests.get(before).toString("UTF-8"));
        check("auto".equals(payload.getJSONObject("reasoning").getString("summary")),
                "Responses did not request a reasoning summary");
        passed++;
    }

    private void testReasoningDetails() throws Exception {
        enqueue(chatEvent("\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":\"公开思考\"},"
                + "{\"type\":\"reasoning.encrypted\",\"data\":\"secret\"}],\"content\":\"答案\"")
                + "data: [DONE]\n\n");
        Capture capture = runReply(ProviderProfile.MODE_CHAT);
        check("公开思考".equals(capture.reasoning.toString()), "Relay reasoning_details missing");
        check("答案".equals(capture.answer.toString()), "Relay answer mixed with reasoning");
        passed++;
    }

    private void testSummarySnapshots() throws Exception {
        enqueue("data: {\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"思考\"}\n\n"
                + "data: {\"type\":\"response.reasoning_summary_text.done\",\"text\":\"思考摘要\"}\n\n"
                + "data: {\"type\":\"response.output_item.done\",\"output_index\":0,"
                + "\"item\":{\"type\":\"reasoning\",\"summary\":[{\"text\":\"思考摘要\"}]}}\n\n"
                + "data: {\"type\":\"response.output_text.done\",\"output_index\":1,\"text\":\"答案\"}\n\n"
                + "data: {\"type\":\"response.completed\"}\n\n");
        Capture capture = runReply(ProviderProfile.MODE_RESPONSES);
        check("思考摘要".equals(capture.reasoning.toString()), "Summary snapshot duplicated or lost");
        check("答案".equals(capture.answer.toString()), "Done-only answer missing");
        passed++;
    }

    private void testCompletedSnapshot() throws Exception {
        enqueue("data: {\"type\":\"response.completed\",\"response\":{\"output\":["
                + "{\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"完成时摘要\"}]},"
                + "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"完成时答案\"}]}]}}\n\n");
        Capture capture = runReply(ProviderProfile.MODE_RESPONSES);
        check("完成时摘要".equals(capture.reasoning.toString()), "Completed-event reasoning missing");
        check("完成时答案".equals(capture.answer.toString()), "Completed-event answer missing");
        passed++;
    }

    private void testSummaryUnsupported() throws Exception {
        int before = requests.size();
        responses.add(new FixtureStream("{\"error\":{\"message\":\"Unsupported parameter: reasoning.summary\"}}",
                400, "application/json"));
        enqueue("data: {\"type\":\"response.output_text.delta\",\"delta\":\"兼容回复\"}\n\n"
                + "data: {\"type\":\"response.completed\"}\n\n");
        Capture capture = runReply(ProviderProfile.MODE_RESPONSES);
        check("兼容回复".equals(capture.answer.toString()) && capture.error.isEmpty(),
                "Unsupported summary broke response");
        check(requests.size() == before + 2, "Unexpected summary retry count");
        check(!new JSONObject(requests.get(before + 1).toString("UTF-8")).has("reasoning"),
                "Retry did not remove unsupported reasoning parameter");
        passed++;
    }

    private void testJsonStreamResponse() throws Exception {
        int before = requests.size();
        responses.add(new FixtureStream("{\"choices\":[{\"message\":"
                + "{\"reasoning_content\":\"完整思考\",\"content\":\"完整答案\"}}]}", 200, "application/json"));
        Capture capture = runReply(ProviderProfile.MODE_CHAT);
        check("完整思考".equals(capture.reasoning.toString()), "JSON stream response reasoning missing");
        check("完整答案".equals(capture.answer.toString()), "JSON stream response answer missing");
        check(requests.size() == before + 1, "JSON stream reply triggered a duplicate request");
        passed++;
    }

    private void testFallback() throws Exception {
        enqueue("data: [DONE]\n\n");
        enqueue("{\"choices\":[{\"message\":{\"reasoning_content\":\"检查后回答。\",\"content\":\"完整回答。\"}}]}");
        Capture capture = runReply(ProviderProfile.MODE_CHAT);
        check(capture.stages.contains(ChatMessage.STAGE_FALLBACK), "Fallback status missing");
        check("检查后回答。".equals(capture.reasoning.toString()), "Fallback reasoning missing");
        check("完整回答。".equals(capture.answer.toString()), "Fallback answer incorrect");
        passed++;
    }

    private void testReasoningOnly() throws Exception {
        int before = requests.size();
        enqueue(chatEvent("\"reasoning_content\":\"仅有思考。\"") + "data: [DONE]\n\n");
        Capture capture = runReply(ProviderProfile.MODE_CHAT);
        check(capture.answer.length() == 0, "Reasoning became the answer");
        check(capture.error.contains("没有返回回答正文"), "Missing answer was not explained");
        check(requests.size() == before + 1, "Reasoning-only reply triggered another request");
        passed++;
    }

    private void testPersistence() throws Exception {
        File directory = new File(getTargetContext().getCacheDir(), "progress-persistence");
        check(directory.isDirectory() || directory.mkdirs(), "Cannot create fixture storage");
        Context isolated = new ContextWrapper(getTargetContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return directory; }
        };
        ConversationStore store = new ConversationStore(isolated);
        Conversation conversation = new Conversation();
        ChatMessage completed = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "保留正文");
        completed.setReasoning("保留摘要");
        completed.restoreDurations(4200, 1200);
        completed.setStage(ChatMessage.STAGE_COMPLETED);
        completed.restoreProcessingSteps(java.util.Arrays.asList(
                new ChatMessage.ProcessingStep(ChatMessage.STAGE_WAITING, 0),
                new ChatMessage.ProcessingStep(ChatMessage.STAGE_THINKING, 2000),
                new ChatMessage.ProcessingStep(ChatMessage.STAGE_COMPLETED, 4200)));
        ChatMessage stopped = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "");
        stopped.setStage(ChatMessage.STAGE_STOPPED);
        ChatMessage interrupted = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "");
        interrupted.setPending(true);
        interrupted.setReasoning("尚未完成");
        interrupted.setStage(ChatMessage.STAGE_THINKING);
        conversation.getMessages().add(completed);
        conversation.getMessages().add(stopped);
        conversation.getMessages().add(interrupted);
        store.write(store.encode(Collections.singletonList(conversation), conversation.getId()));
        List<ChatMessage> loaded = store.load().getConversations().get(0).getMessages();
        check(loaded.size() == 3, "Empty stopped reply disappeared");
        check("保留摘要".equals(loaded.get(0).getReasoning()), "Reasoning was not restored");
        check(loaded.get(0).getProcessingDurationMs() == 4200
                && loaded.get(0).getReasoningDurationMs() == 1200, "Timing was not restored");
        check(loaded.get(0).getProcessingSteps().size() == 3
                && loaded.get(0).getProcessingSteps().get(1).getStartedMs() == 2000,
                "Processing timeline was not restored");
        check(ChatMessage.STAGE_STOPPED.equals(loaded.get(1).getStage()), "Stop status lost");
        check(ChatMessage.STAGE_INTERRUPTED.equals(loaded.get(2).getStage())
                && !loaded.get(2).isPending(), "Interrupted reply still looks active");
        new ConversationStore(getTargetContext()); // Restore the image storage location.
        passed++;
    }

    private void testLiveScreen() throws Exception {
        MainActivity activity = (MainActivity) startActivitySync(new Intent(getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ControlledStream stream = new ControlledStream();
        responses.add(stream);
        runOnMainSync(() -> {
            activity.getChatSession().createConversation();
            activity.getProviders().add(provider(ProviderProfile.MODE_CHAT));
            try {
                Field field = MainActivity.class.getDeclaredField("chatScreen");
                field.setAccessible(true);
                ((ChatScreen) field.get(activity)).refreshProviders();
            } catch (Exception error) {
                throw new AssertionError(error);
            }
            activity.getChatSession().send(provider(ProviderProfile.MODE_CHAT),
                    new ChatMessage(ChatMessage.ROLE_USER, "你好，请介绍你能提供的帮助。"));
        });
        await(() -> text(activity).contains("等待模型响应"), "Waiting status missing");
        String before = text(activity);
        Thread.sleep(1200);
        check(!before.equals(text(activity)), "Elapsed timer did not update during silence");
        screenshot("waiting");
        stream.reasoningGate.countDown();
        await(() -> text(activity).contains("正在思考") && text(activity).contains("我会先理解"),
                "Live reasoning did not appear");
        screenshot("thinking");
        Thread.sleep(1100);
        stream.answerGate.countDown();
        await(() -> text(activity).contains("正在生成回答") && text(activity).contains("已生成"),
                "Answer generation progress missing");
        check(text(activity).contains("已思考"), "Thinking completion duration missing");
        screenshot("answering");
        stream.finishGate.countDown();
        await(() -> text(activity).contains("回答完成"), "Completion status missing");
        check(!text(activity).contains("我会先理解"), "Completed reasoning was not collapsed");
        runOnMainSync(() -> {
            View header = findExpandable(activity.getWindow().getDecorView());
            check(header != null && header.performClick(), "Cannot expand completed reasoning");
        });
        check(text(activity).contains("我会先理解"), "Expanded reasoning not visible");
        screenshot("completed");
        ChatMessage saved = activity.getChatSession().getActiveConversation().getMessages().get(
                activity.getChatSession().getActiveConversation().getMessages().size() - 1);
        check(saved.getProcessingDurationMs() >= 1200 && !saved.isPending(), "Completed duration invalid");
        long frozen = saved.getProcessingDurationMs();
        Thread.sleep(50);
        check(saved.getProcessingDurationMs() == frozen, "Completed timer kept running");
        passed++;

        ControlledStream plain = new ControlledStream(false);
        responses.add(plain);
        runOnMainSync(() -> {
            activity.getChatSession().createConversation();
            activity.getChatSession().send(provider(ProviderProfile.MODE_CHAT),
                    new ChatMessage(ChatMessage.ROLE_USER, "测试接口不返回思考文本"));
        });
        await(() -> text(activity).contains("等待模型响应"), "Plain reply waiting status missing");
        check(text(activity).contains("准备请求") && text(activity).contains("正在连接"),
                "No-reasoning live processing timeline missing");
        screenshot("no-reasoning-waiting");
        plain.reasoningGate.countDown();
        plain.answerGate.countDown();
        await(() -> text(activity).contains("正在生成回答"), "Plain reply answer progress missing");
        plain.finishGate.countDown();
        await(() -> text(activity).contains("回答完成") && !activity.getChatSession().isSending(),
                "Plain reply did not complete");
        runOnMainSync(() -> {
            View header = findExpandable(activity.getWindow().getDecorView());
            check(header != null && header.performClick(), "No-reasoning reply cannot expand");
        });
        check(text(activity).contains("接口未返回可显示的思考文本")
                && text(activity).contains("等待模型响应"), "No-reasoning completion timeline missing");
        screenshot("no-reasoning-completed");
        ChatMessage plainSaved = activity.getChatSession().getActiveConversation().getMessages().get(1);
        check(plainSaved.getReasoning().isEmpty() && plainSaved.getProcessingSteps().size() >= 4,
                "Plain reply has invented reasoning or missing stages");
        passed++;

        ControlledStream cancelled = new ControlledStream();
        responses.add(cancelled);
        runOnMainSync(() -> activity.getChatSession().send(provider(ProviderProfile.MODE_CHAT),
                new ChatMessage(ChatMessage.ROLE_USER, "测试停止等待中的回答")));
        await(() -> text(activity).contains("等待模型响应"), "Second waiting status missing");
        runOnMainSync(() -> activity.getChatSession().cancelReply());
        await(() -> text(activity).contains("已停止") && !activity.getChatSession().isSending(),
                "Stop did not settle the reply");
        List<ChatMessage> messages = activity.getChatSession().getActiveConversation().getMessages();
        ChatMessage stopped = messages.get(messages.size() - 1);
        check(stopped.getContent().isEmpty() && ChatMessage.STAGE_STOPPED.equals(stopped.getStage()),
                "Empty stopped reply status incorrect");
        long stoppedTime = stopped.getProcessingDurationMs();
        Thread.sleep(50);
        check(stoppedTime == stopped.getProcessingDurationMs(), "Stopped timer kept running");
        passed++;
    }

    private void screenshot(String name) throws Exception {
        waitForIdleSync();
        Thread.sleep(250); // Allow the view tree's next draw to reach the screenshot surface.
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        check(bitmap != null, "Screenshot unavailable");
        File file = new File(getTargetContext().getExternalFilesDir(null), "progress-" + name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
    }

    private String text(MainActivity activity) {
        StringBuilder result = new StringBuilder();
        runOnMainSync(() -> collectText(activity.getWindow().getDecorView(), result));
        return result.toString();
    }

    private static void collectText(View view, StringBuilder result) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) result.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), result);
        }
    }

    private static View findExpandable(View view) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view.getContentDescription() != null
                && view.getContentDescription().toString().contains("点击展开")) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findExpandable(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void await(BooleanSupplier condition, String error) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean()) {
            check(System.nanoTime() < deadline, error);
            Thread.sleep(50);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class Capture implements ApiClient.StreamCallback {
        final StringBuilder reasoning = new StringBuilder();
        final StringBuilder answer = new StringBuilder();
        final List<String> stages = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        String error = "";
        @Override public void onStage(String stage) { stages.add(stage); }
        @Override public void onReasoningDelta(String text) { reasoning.append(text); }
        @Override public void onDelta(String text) { answer.append(text); }
        @Override public void onComplete(String text) { done.countDown(); }
        @Override public void onError(String message) { error = message; done.countDown(); }
        @Override public void onCancelled() { error = "cancelled"; done.countDown(); }
    }

    private static final class ControlledStream extends InputStream {
        ControlledStream() { this(true); }
        ControlledStream(boolean reasoning) {
            if (!reasoning) {
                parts[1] = chatEvent("\"role\":\"assistant\"").getBytes(StandardCharsets.UTF_8);
            }
        }
        final CountDownLatch reasoningGate = new CountDownLatch(1);
        final CountDownLatch answerGate = new CountDownLatch(1);
        final CountDownLatch finishGate = new CountDownLatch(1);
        final byte[][] parts = {
            chatEvent("\"role\":\"assistant\"").getBytes(StandardCharsets.UTF_8),
            chatEvent("\"reasoning_content\":\"我会先理解你的问题，再整理一份清晰的回答。\"")
                    .getBytes(StandardCharsets.UTF_8),
            chatEvent("\"content\":\"你好！我可以帮你解答问题、整理资料和编写代码。\"")
                    .getBytes(StandardCharsets.UTF_8),
            "data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8)
        };
        int part;
        int position;
        volatile boolean closed;
        @Override public int read() throws IOException {
            if (closed || part >= parts.length) return -1;
            if (position == 0 && part > 0) {
                try {
                    CountDownLatch gate = part == 1 ? reasoningGate : part == 2 ? answerGate : finishGate;
                    if (!gate.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture gate timed out");
                } catch (InterruptedException error) {
                    throw new IOException(error);
                }
            }
            int value = parts[part][position++] & 0xff;
            if (position == parts[part].length) { part++; position = 0; }
            return value;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int first = read();
            if (first == -1) return -1;
            buffer[offset] = (byte) first;
            int count = 1;
            while (count < length && position > 0 && part < parts.length) {
                buffer[offset + count++] = (byte) read();
            }
            return count;
        }
        @Override public void close() {
            closed = true;
            reasoningGate.countDown();
            answerGate.countDown();
            finishGate.countDown();
        }
    }

    private static final class FixtureStream extends ByteArrayInputStream {
        final int status;
        final String contentType;
        FixtureStream(String body, int status, String contentType) {
            super(body.getBytes(StandardCharsets.UTF_8));
            this.status = status;
            this.contentType = contentType;
        }
    }
}
