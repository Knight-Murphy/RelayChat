package com.relaychat.app.python;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.relaychat.app.MainActivity;
import com.relaychat.app.data.ConversationStore;
import com.relaychat.app.data.LocalPythonExecutor;
import com.relaychat.app.model.ChatMessage;
import com.relaychat.app.model.CodeExecution;
import com.relaychat.app.model.Conversation;
import com.relaychat.app.model.ImageAttachment;
import com.relaychat.app.model.ProviderProfile;
import com.relaychat.app.model.PythonCodeExtractor;
import com.relaychat.app.ui.ChatScreen;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Verifies Chaquopy execution, reply integration, persistence and collapsed code blocks. */
public final class PythonInstrumentation extends Instrumentation {
    private final BlockingQueue<FixtureStream> responses = new LinkedBlockingQueue<>();
    private volatile ByteArrayOutputStream requestBody;
    private volatile String requestPath = "";
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
            installNetworkFixture();
            testCodeExtractor();
            testLocalPython();
            testPersistence();
            MainActivity activity = (MainActivity) startActivitySync(new Intent(
                    getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            testEndToEndExecution(activity);
            testUi(activity);
            result.putString("stream", "PASS: " + passed
                    + " embedded Python execution scenarios\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: "
                    + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void installNetworkFixture() {
        URL.setURLStreamHandlerFactory(protocol -> {
            if (!"https".equals(protocol)) {
                return null;
            }
            return new URLStreamHandler() {
                @Override
                protected HttpURLConnection openConnection(URL url) throws IOException {
                    if (!"python.invalid".equals(url.getHost())) {
                        throw new IOException("Test disallows external network access");
                    }
                    FixtureStream response = responses.poll();
                    if (response == null) {
                        throw new IOException("Unexpected network request");
                    }
                    requestPath = url.getPath();
                    requestBody = new ByteArrayOutputStream();
                    return new HttpURLConnection(url) {
                        @Override public void connect() {}
                        @Override public boolean usingProxy() { return false; }
                        @Override public void disconnect() {}
                        @Override public int getResponseCode() { return 200; }
                        @Override public String getContentType() { return response.contentType; }
                        @Override public InputStream getErrorStream() {
                            return new ByteArrayInputStream(
                                    response.body.getBytes(StandardCharsets.UTF_8));
                        }
                        @Override public InputStream getInputStream() {
                            return new ByteArrayInputStream(
                                    response.body.getBytes(StandardCharsets.UTF_8));
                        }
                        @Override public OutputStream getOutputStream() { return requestBody; }
                    };
                }
            };
        });
    }

    private void testCodeExtractor() {
        String markdown = "说明\r\n\r\n```Python\r\nprint('a')\r\nx = 2\r\n```\r\n"
                + "```python\nprint('ignored')\n```\n";
        String code = PythonCodeExtractor.extractFirstBlock(markdown);
        check("print('a')\nx = 2".equals(code), "First Python block was not extracted");
        check(PythonCodeExtractor.extractFirstBlock("```python\nprint('open')") == null,
                "Unclosed Python block was accepted");
        check(PythonCodeExtractor.extractFirstBlock("```javascript\nprint('no')\n```") == null,
                "Non-Python block was accepted");
        passed++;
    }

    private void testLocalPython() {
        LocalPythonExecutor executor = new LocalPythonExecutor(getTargetContext());

        CodeExecution printed = executor.execute("print('本机 stdout')");
        check(printed.isCompleted(), "print-only program failed: " + printed.getLogs());
        check(printed.getLogs().contains("本机 stdout"), "stdout was not captured");
        check(printed.getImages().isEmpty(), "print-only program returned an image");

        CodeExecution shown = executor.execute(
                "import numpy as np\n"
                        + "import matplotlib.pyplot as plt\n"
                        + "x = np.linspace(0, 6.28, 240)\n"
                        + "plt.figure(figsize=(7, 3.6), constrained_layout=True)\n"
                        + "plt.plot(x, np.sin(x), color='#246b8a', linewidth=2)\n"
                        + "plt.title('Chaquopy local plot')\n"
                        + "plt.xlabel('x')\n"
                        + "plt.ylabel('sin(x)')\n"
                        + "print('plot shown')\n"
                        + "plt.show()\n");
        check(shown.isCompleted(), "plt.show program failed: " + shown.getLogs());
        check(shown.getLogs().contains("plot shown"), "plot logs were not captured");
        check(shown.getImages().size() == 1, "plt.show did not produce one image");
        checkPlot(shown.getImages().get(0), "plt.show");

        CodeExecution chinese = executor.execute(
                "import matplotlib.pyplot as plt\n"
                        + "from matplotlib import font_manager\n"
                        + "plt.figure(figsize=(5, 3), constrained_layout=True)\n"
                        + "plt.plot([0, 1], [0, 1])\n"
                        + "plt.title('中文标题测试')\n"
                        + "plt.xlabel('横轴')\n"
                        + "plt.ylabel('纵轴')\n"
                        + "family = plt.rcParams['font.sans-serif'][0]\n"
                        + "font = font_manager.get_font(font_manager.findfont(family))\n"
                        + "assert font.get_char_index(ord('中'))\n"
                        + "assert font.get_char_index(ord('文'))\n"
                        + "print('font=' + family)\n"
                        + "plt.show()\n");
        check(chinese.isCompleted(), "Chinese plot failed: " + chinese.getLogs());
        check(chinese.getImages().size() == 1, "Chinese plot produced no image");
        check(chinese.getLogs().contains("font="), "Selected font was not reported");
        checkPlot(chinese.getImages().get(0), "Chinese font");

        CodeExecution latexDelimiters = executor.execute(
                "import matplotlib.pyplot as plt\n"
                        + "plt.figure(figsize=(7, 4), constrained_layout=True)\n"
                        + "plt.plot([1, 2], [3, 4], label=r'\\(y = \\arctan(x)\\)')\n"
                        + "plt.axhline(1.57, linestyle='--', label=r'渐近线 \\(y = \\frac{\\pi}{2}\\)')\n"
                        + "plt.text(0.5, 0.5, r'\\(\\frac{\\pi}{2}\\)')\n"
                        + "plt.title(r'反正切函数 \\(y = \\arctan(x)\\) 图像')\n"
                        + "plt.yticks([-1.57, -0.785, 1.57],\n"
                        + "           [r'\\(-\\frac{\\pi}{2}\\)', r'\\(-\\frac{\\pi}{4}\\)',\n"
                        + "            r'\\(\\frac{\\pi}{2}\\)'])\n"
                        + "plt.legend()\n"
                        + "plt.show()\n");
        check(latexDelimiters.isCompleted(),
                "LaTeX-delimited plot failed: " + latexDelimiters.getLogs());
        check(latexDelimiters.getImages().size() == 1,
                "LaTeX-delimited plot produced no image");
        checkPlot(latexDelimiters.getImages().get(0), "LaTeX delimiters");

        CodeExecution formattedTicks = executor.execute(
                "import matplotlib.pyplot as plt\n"
                        + "from matplotlib.ticker import FuncFormatter\n"
                        + "plt.figure(figsize=(5, 3), constrained_layout=True)\n"
                        + "plt.plot([0, 1], [-1, 1])\n"
                        + "plt.gca().yaxis.set_major_formatter(FuncFormatter(\n"
                        + "    lambda value, position: r'\\(\\frac{\\pi}{2}\\)'))\n"
                        + "plt.title(r'\\(y = \\arctan(x)\\)')\n"
                        + "plt.show()\n");
        check(formattedTicks.isCompleted(),
                "Dynamic tick formatter failed: " + formattedTicks.getLogs());
        check(formattedTicks.getImages().size() == 1,
                "Dynamic tick formatter produced no image");
        checkPlot(formattedTicks.getImages().get(0), "dynamic tick formatter");

        CodeExecution overridden = executor.execute(
                "import matplotlib.pyplot as plt\n"
                        + "plt.rcParams['font.sans-serif'] = ['Missing Chinese Font']\n"
                        + "print('font override')\n");
        check(overridden.isCompleted(), "Font override fixture failed");
        CodeExecution recovered = executor.execute(
                "import matplotlib.pyplot as plt\n"
                        + "print(plt.rcParams['font.sans-serif'][0])\n");
        check(recovered.isCompleted(), "Font recovery fixture failed");
        check(!recovered.getLogs().contains("Missing Chinese Font"),
                "A previous execution overwrote the Chinese font");

        CodeExecution saved = executor.execute(
                "import matplotlib.pyplot as plt\n"
                        + "plt.figure(figsize=(5, 3), constrained_layout=True)\n"
                        + "plt.plot([0, 1, 2], [0, 2, 1])\n"
                        + "plt.savefig('saved_curve.jpg', dpi=110)\n"
                        + "print('saved locally')\n");
        check(saved.isCompleted(), "savefig program failed: " + saved.getLogs());
        check(saved.getImages().size() == 1, "savefig output was not found");
        checkPlot(saved.getImages().get(0), "savefig");

        CodeExecution failed = executor.execute(
                "print('before error')\nraise RuntimeError('local boom')\n");
        check("failed".equals(failed.getStatus()), "Python error did not fail the execution");
        check(failed.getLogs().contains("before error")
                        && failed.getLogs().contains("RuntimeError")
                        && failed.getLogs().contains("local boom"),
                "Python traceback was not captured");
        passed += 8;
    }

    private void testPersistence() throws Exception {
        ConversationStore store = new ConversationStore(getTargetContext());
        CodeExecution execution = new LocalPythonExecutor(getTargetContext()).execute(
                "import matplotlib.pyplot as plt\n"
                        + "plt.figure(figsize=(4, 2), constrained_layout=True)\n"
                        + "plt.plot([0, 1], [1, 0])\n"
                        + "plt.show()\n"
                        + "print('persist me')\n");
        check(execution.getImages().size() == 1,
                "Persistence fixture did not generate an image");
        ChatMessage assistant = new ChatMessage(ChatMessage.ROLE_ASSISTANT,
                "本机执行完成。\n\n```python\nprint('persist me')\n```");
        assistant.upsertCodeExecution(execution);
        assistant.setStage(ChatMessage.STAGE_COMPLETED);
        Conversation conversation = new Conversation();
        conversation.getMessages().add(assistant);
        store.write(store.encode(Collections.singletonList(conversation),
                conversation.getId()));

        List<ChatMessage> loaded = store.load().getConversations().get(0).getMessages();
        check(loaded.size() == 1, "Local execution message disappeared");
        CodeExecution restored = loaded.get(0).getCodeExecutions().get(0);
        check(restored.getLogs().contains("persist me"),
                "Execution logs were not restored");
        check(restored.getImages().size() == 1
                        && restored.getImages().get(0).getData().length > 0,
                "Execution image was not restored");
        passed++;
    }

    private void testEndToEndExecution(MainActivity activity) throws Exception {
        String code = "import numpy as np\n"
                + "import matplotlib.pyplot as plt\n"
                + "x = np.linspace(-10, 10, 500)\n"
                + "plt.figure(figsize=(7, 4.5), constrained_layout=True)\n"
                + "plt.plot(x, np.arctan(x), color='#1a56db', linewidth=2.5,\n"
                + "         label=r'\\(y = \\arctan(x)\\)')\n"
                + "plt.axhline(np.pi / 2, color='#e55353', linestyle='--',\n"
                + "            label=r'渐近线 \\(y = \\frac{\\pi}{2}\\)')\n"
                + "plt.axhline(-np.pi / 2, color='#e55353', linestyle='--',\n"
                + "            label=r'渐近线 \\(y = -\\frac{\\pi}{2}\\)')\n"
                + "plt.yticks([-np.pi / 2, -np.pi / 4, 0, np.pi / 4, np.pi / 2],\n"
                + "           [r'\\(-\\frac{\\pi}{2}\\)', r'\\(-\\frac{\\pi}{4}\\)', '0',\n"
                + "            r'\\(\\frac{\\pi}{4}\\)', r'\\(\\frac{\\pi}{2}\\)'])\n"
                + "plt.title(r'反正切函数 \\(y = \\arctan(x)\\) 图像')\n"
                + "plt.legend(loc='lower right')\n"
                + "print('end to end local execution')\n"
                + "plt.show()\n";
        String answer = "代码已交给本机 Python 执行。\n\n```python\n" + code + "```";
        JSONObject delta = new JSONObject().put("choices", new JSONArray().put(
                new JSONObject().put("delta", new JSONObject().put("content", answer))));
        responses.add(new FixtureStream("data: " + delta + "\n\ndata: [DONE]\n\n",
                "text/event-stream"));

        ProviderProfile provider = new ProviderProfile("local-python", "测试服务",
                "https://python.invalid/v1", "",
                ProviderProfile.MODE_CHAT,
                Collections.singletonList("relay-model"), "relay-model", "");
        final Conversation[] conversation = new Conversation[1];
        runOnMainSync(() -> {
            conversation[0] = activity.getChatSession().createConversation();
            activity.getChatSession().send(provider,
                    new ChatMessage(ChatMessage.ROLE_USER, "用 Python 画平方曲线"));
            activity.getChatSession().beginReply(activity.getChatSession().getRequestId());
        });

        awaitCondition(() -> {
            if (activity.getChatSession().isSending()
                    || conversation[0].getMessages().size() < 2) {
                return false;
            }
            ChatMessage reply = conversation[0].getMessages().get(1);
            return !reply.isPending() && !reply.getCodeExecutions().isEmpty();
        }, "End-to-end local execution timed out", 120);

        ChatMessage reply = conversation[0].getMessages().get(1);
        check(reply.getContent().contains("```python"), "Model source block was lost");
        CodeExecution execution = reply.getCodeExecutions().get(0);
        check(execution.isCompleted(), "Reply Python failed: " + execution.getLogs());
        check(execution.getLogs().contains("end to end local execution"),
                "Reply Python output was not captured");
        check(execution.getImages().size() == 1,
                "Reply Python did not generate an image");
        checkPlot(execution.getImages().get(0), "reply");

        JSONObject request = new JSONObject(requestBody.toString("UTF-8"));
        check(requestPath.endsWith("/chat/completions"),
                "Unexpected endpoint: " + requestPath);
        check(!request.has("tools") && !request.has("tool_choice"),
                "Local execution still sends the remote tool request");
        JSONArray messages = request.getJSONArray("messages");
        String system = messages.getJSONObject(0).getString("content");
        check(system.contains("do not use \\(...\\)"),
                "Local Python instruction does not prohibit LaTeX delimiters");
        check(system.contains("Local Python execution")
                        && system.contains("NumPy")
                        && system.contains("$...$")
                        && system.contains("mathtext")
                        && !system.contains("code_interpreter"),
                "Local Python instruction was not sent to the model");

        awaitCondition(() -> text(activity).contains("执行 Python 代码"),
                "Execution card did not appear", 15);
        check(findViewByDescription(activity.getWindow().getDecorView(),
                        "打开 Python 输出图片") instanceof ImageView,
                "Generated plot was not displayed");
        screenshot("local-python-execution");
        passed++;
    }

    private void testUi(MainActivity activity) throws Exception {
        runOnMainSync(() -> {
            activity.getChatSession().createConversation();
            ChatMessage assistant = new ChatMessage(ChatMessage.ROLE_ASSISTANT,
                    "下面是模型返回的 Python 代码：\n\n"
                            + "```python\n"
                            + "secret_marker = 42\n"
                            + "print(secret_marker)\n"
                            + "```");
            assistant.setStage(ChatMessage.STAGE_COMPLETED);
            assistant.setCompletedAtMillis(System.currentTimeMillis());
            activity.getChatSession().getActiveConversation().getMessages().add(assistant);
            try {
                java.lang.reflect.Field field =
                        MainActivity.class.getDeclaredField("chatScreen");
                field.setAccessible(true);
                ((ChatScreen) field.get(activity)).onReplyChanged();
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
        awaitCondition(() -> text(activity).contains("Python 代码")
                        && text(activity).contains("2 行"),
                "Fenced Python code header missing", 10);
        check(!text(activity).contains("secret_marker"),
                "Fenced Python code was expanded by default");
        runOnMainSync(() -> {
            View header = findViewByDescription(activity.getWindow().getDecorView(),
                    "Python 代码");
            check(header != null && header.performClick(), "Code block cannot expand");
        });
        awaitCondition(() -> text(activity).contains("secret_marker"),
                "Expanded fenced Python code is missing", 10);
        screenshot("python-code-expanded");
        passed++;
    }

    private static void checkPlot(ImageAttachment image, String source) {
        check(image.getData().length > 1024, source + " image is too small");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image.getData(), 0, image.getData().length, bounds);
        check(bounds.outWidth >= 400 && bounds.outHeight >= 200,
                source + " image has invalid dimensions");
    }

    private String text(MainActivity activity) {
        StringBuilder result = new StringBuilder();
        runOnMainSync(() -> collectText(activity.getWindow().getDecorView(), result));
        return result.toString();
    }

    private static void collectText(View view, StringBuilder result) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof TextView) {
            result.append(((TextView) view).getText()).append('\n');
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectText(group.getChildAt(index), result);
            }
        }
    }

    private static View findViewByDescription(View view, String prefix) {
        if (view.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (view.getContentDescription() != null
                && view.getContentDescription().toString().contains(prefix)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findViewByDescription(group.getChildAt(index), prefix);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void awaitCondition(BooleanSupplier condition, String error, int seconds)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (true) {
            if (condition.getAsBoolean()) {
                return;
            }
            check(System.nanoTime() < deadline, error);
            Thread.sleep(100);
        }
    }

    private void screenshot(String name) throws Exception {
        waitForIdleSync();
        Thread.sleep(300);
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        check(bitmap != null, "Screenshot unavailable");
        File directory = new File(getTargetContext().getExternalFilesDir(null),
                "python-tests");
        check(directory.isDirectory() || directory.mkdirs(),
                "Cannot create screenshot directory");
        File file = new File(directory, "python-" + name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static final class FixtureStream {
        final String body;
        final String contentType;

        FixtureStream(String body, String contentType) {
            this.body = body;
            this.contentType = contentType;
        }
    }
}
