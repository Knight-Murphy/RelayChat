package com.relaychat.app.network;

import com.relaychat.app.model.ChatMessage;
import com.relaychat.app.model.ImageAttachment;
import com.relaychat.app.model.ProviderProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiClient {
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int MODEL_READ_TIMEOUT_MS = 30_000;
    private static final int REPLY_READ_TIMEOUT_MS = 15 * 60_000;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "relaychat-network");
        thread.setDaemon(true);
        return thread;
    });

    private volatile HttpURLConnection activeConnection;
    private volatile boolean cancelled;
    private final Set<String> summaryUnsupported = Collections.synchronizedSet(new HashSet<>());

    public interface ModelCallback {
        void onSuccess(List<String> models);

        void onError(String message);
    }

    public interface StreamCallback {
        void onStage(String stage);

        void onReasoningDelta(String text);

        void onDelta(String text);

        void onComplete(String fullText);

        void onError(String message);

        void onCancelled();
    }

    private interface DeltaSink {
        void onDelta(String text);

        void onReasoning(String text);

        void onStage(String stage);
    }

    public void fetchModels(ProviderProfile provider, ModelCallback callback) {
        executor.execute(() -> {
            try {
                callback.onSuccess(performFetchModels(provider));
            } catch (Exception error) {
                callback.onError(messageFor(error));
            }
        });
    }

    public void streamChat(ProviderProfile provider, List<ChatMessage> messages, StreamCallback callback) {
        cancelled = false;
        executor.execute(() -> {
            boolean[] receivedText = {false};
            boolean[] receivedReasoning = {false};
            StringBuilder result = new StringBuilder();
            try {
                if (cancelled) {
                    callback.onCancelled();
                    return;
                }
                validateProvider(provider);
                callback.onStage(ChatMessage.STAGE_CONNECTING);
                DeltaSink sink = new DeltaSink() {
                    @Override
                    public void onDelta(String delta) {
                        if (delta != null && !delta.isEmpty()) {
                            result.append(delta);
                            receivedText[0] = true;
                            callback.onDelta(delta);
                        }
                    }

                    @Override
                    public void onReasoning(String text) {
                        if (text != null && !text.isEmpty()) {
                            receivedReasoning[0] = true;
                            callback.onReasoningDelta(text);
                        }
                    }

                    @Override
                    public void onStage(String stage) {
                        callback.onStage(stage);
                    }
                };

                if (ProviderProfile.MODE_RESPONSES.equals(provider.getMode())) {
                    performResponsesStream(provider, messages, sink);
                } else {
                    performChatStream(provider, messages, sink);
                }
                if (!cancelled && !receivedText[0] && !receivedReasoning[0]) {
                    callback.onStage(ChatMessage.STAGE_FALLBACK);
                    String fallback = performNonStreaming(provider, messages, callback);
                    if (!fallback.isEmpty()) {
                        result.append(fallback);
                        receivedText[0] = true;
                        callback.onDelta(fallback);
                    }
                }
                if (cancelled) {
                    callback.onCancelled();
                } else if (result.length() == 0) {
                    callback.onError(receivedReasoning[0]
                            ? "模型只返回了思考内容，没有返回回答正文" : "模型没有返回可见文本");
                } else {
                    callback.onComplete(result.toString());
                }
            } catch (ApiHttpException error) {
                if (!cancelled && !receivedText[0] && !receivedReasoning[0]
                        && shouldRetryWithoutStreaming(error.statusCode)) {
                    try {
                        callback.onStage(ChatMessage.STAGE_FALLBACK);
                        String fallback = performNonStreaming(provider, messages, callback);
                        if (!cancelled && !fallback.isEmpty()) {
                            callback.onDelta(fallback);
                            callback.onComplete(fallback);
                            return;
                        }
                    } catch (Exception fallbackError) {
                        if (cancelled) {
                            callback.onCancelled();
                        } else {
                            callback.onError(messageFor(fallbackError));
                        }
                        return;
                    }
                }
                if (cancelled) {
                    callback.onCancelled();
                } else {
                    callback.onError(error.getMessage());
                }
            } catch (Exception error) {
                if (cancelled) {
                    callback.onCancelled();
                } else {
                    callback.onError(messageFor(error));
                }
            } finally {
                activeConnection = null;
            }
        });
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private List<String> performFetchModels(ProviderProfile provider) throws Exception {
        validateBaseUrl(provider);
        HttpURLConnection connection = openConnection(provider, "/models", "GET", "application/json");
        try {
            int statusCode = connection.getResponseCode();
            String body = readBody(statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (statusCode < 200 || statusCode >= 300) {
                throw new ApiHttpException(statusCode, errorMessage(statusCode, body));
            }
            JSONObject root = new JSONObject(body);
            JSONArray data = root.optJSONArray("data");
            List<String> models = new ArrayList<>();
            if (data != null) {
                for (int index = 0; index < data.length(); index++) {
                    JSONObject item = data.optJSONObject(index);
                    if (item != null) {
                        String id = item.optString("id", "").trim();
                        if (!id.isEmpty() && !models.contains(id)) {
                            models.add(id);
                        }
                    }
                }
            }
            if (models.isEmpty()) {
                throw new IOException("接口未返回模型列表");
            }
            return models;
        } finally {
            connection.disconnect();
        }
    }

    private void performChatStream(ProviderProfile provider, List<ChatMessage> messages,
                                   DeltaSink sink) throws Exception {
        JSONObject payload = buildChatPayload(provider, messages, true);
        HttpURLConnection connection = openConnection(
                provider, "/chat/completions", "POST", "text/event-stream");
        activeConnection = connection;
        try {
            writeBody(connection, payload);
            sink.onStage(ChatMessage.STAGE_WAITING);
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new ApiHttpException(statusCode, errorMessage(statusCode, readBody(connection.getErrorStream())));
            }
            if (isJsonResponse(connection)) {
                emitJsonResponse(new JSONObject(readBody(connection.getInputStream())), false, sink);
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!cancelled && (line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    if (data.isEmpty()) {
                        continue;
                    }
                    JSONObject event = new JSONObject(data);
                    JSONObject error = event.optJSONObject("error");
                    if (error != null) {
                        throw new IOException(error.optString("message", "接口返回错误"));
                    }
                    JSONArray choices = event.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) {
                        continue;
                    }
                    JSONObject choice = choices.optJSONObject(0);
                    if (choice == null) {
                        continue;
                    }
                    JSONObject delta = choice.optJSONObject("delta");
                    if (delta == null) {
                        delta = choice.optJSONObject("message");
                    }
                    if (delta != null) {
                        sink.onReasoning(extractReasoning(delta));
                        String content = extractContent(delta.opt("content"));
                        if (!content.isEmpty()) {
                            sink.onDelta(content);
                        }
                    }
                }
            }
        } finally {
            connection.disconnect();
            activeConnection = null;
        }
    }

    private void performResponsesStream(ProviderProfile provider, List<ChatMessage> messages,
                                        DeltaSink sink) throws Exception {
        try {
            performResponsesStreamRequest(provider, messages, sink);
        } catch (ApiHttpException error) {
            if (!cancelled && retryWithoutSummary(provider, error)) {
                sink.onStage(ChatMessage.STAGE_CONNECTING);
                performResponsesStreamRequest(provider, messages, sink);
            } else {
                throw error;
            }
        }
    }

    private void performResponsesStreamRequest(ProviderProfile provider, List<ChatMessage> messages,
                                               DeltaSink sink) throws Exception {
        JSONObject payload = buildResponsesPayload(provider, messages, true);
        HttpURLConnection connection = openConnection(provider, "/responses", "POST", "text/event-stream");
        activeConnection = connection;
        try {
            writeBody(connection, payload);
            sink.onStage(ChatMessage.STAGE_WAITING);
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new ApiHttpException(statusCode, errorMessage(statusCode, readBody(connection.getErrorStream())));
            }
            if (isJsonResponse(connection)) {
                emitJsonResponse(new JSONObject(readBody(connection.getInputStream())), true, sink);
                return;
            }
            Map<String, String> reasoningParts = new HashMap<>();
            Map<String, String> answerParts = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!cancelled && (line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    if (data.isEmpty()) {
                        continue;
                    }
                    JSONObject event = new JSONObject(data);
                    String type = event.optString("type", "");
                    if ("response.output_text.delta".equals(type)) {
                        String delta = extractContent(event.opt("delta"));
                        if (!delta.isEmpty()) {
                            String key = outputKey(event);
                            answerParts.put(key, answerParts.getOrDefault(key, "") + delta);
                            sink.onDelta(delta);
                        }
                    } else if ("response.output_text.done".equals(type)) {
                        emitSnapshot(answerParts, outputKey(event), extractContent(event.opt("text")), sink, false);
                    } else if ("response.reasoning_summary_text.delta".equals(type)
                            || "response.reasoning_text.delta".equals(type)) {
                        String delta = extractContent(event.opt("delta"));
                        String key = reasoningKey(event);
                        reasoningParts.put(key, reasoningParts.getOrDefault(key, "") + delta);
                        sink.onReasoning(delta);
                    } else if ("response.reasoning_summary_text.done".equals(type)
                            || "response.reasoning_text.done".equals(type)) {
                        emitSnapshot(reasoningParts, reasoningKey(event),
                                extractContent(event.opt("text")), sink, true);
                    } else if ("response.output_item.added".equals(type)) {
                        JSONObject item = event.optJSONObject("item");
                        if (item != null && "reasoning".equals(item.optString("type"))) {
                            sink.onStage(ChatMessage.STAGE_THINKING);
                        }
                    } else if ("response.reasoning_summary_part.added".equals(type)) {
                        JSONObject part = event.optJSONObject("part");
                        if (part != null && !extractContent(part.opt("text")).isEmpty()) {
                            emitSnapshot(reasoningParts, reasoningKey(event),
                                    extractContent(part.opt("text")), sink, true);
                        }
                    } else if ("response.output_item.done".equals(type)) {
                        JSONObject item = event.optJSONObject("item");
                        if (item != null && "reasoning".equals(item.optString("type"))) {
                            JSONArray summary = item.optJSONArray("summary");
                            if (summary != null) {
                                for (int index = 0; index < summary.length(); index++) {
                                    JSONObject part = summary.optJSONObject(index);
                                    if (part != null) {
                                        emitSnapshot(reasoningParts, event.optInt("output_index", 0) + ":" + index,
                                                extractContent(part.opt("text")), sink, true);
                                    }
                                }
                            }
                        }
                    } else if ("response.failed".equals(type) || "error".equals(type)) {
                        JSONObject error = event.optJSONObject("error");
                        throw new IOException(error == null
                                ? event.optString("message", "Responses API 返回错误")
                                : error.optString("message", "Responses API 返回错误"));
                    } else if ("response.completed".equals(type)) {
                        JSONObject response = event.optJSONObject("response");
                        if (response != null) {
                            if (reasoningParts.isEmpty()) {
                                sink.onReasoning(extractResponsesReasoning(response));
                            }
                            if (answerParts.isEmpty()) {
                                sink.onDelta(extractResponsesText(response));
                            }
                        }
                        break;
                    }
                }
            }
        } finally {
            connection.disconnect();
            activeConnection = null;
        }
    }

    private String performNonStreaming(ProviderProfile provider, List<ChatMessage> messages,
                                       StreamCallback callback) throws Exception {
        if (ProviderProfile.MODE_RESPONSES.equals(provider.getMode())) {
            return performResponsesNonStreaming(provider, messages, callback);
        }
        return performChatNonStreaming(provider, messages, callback);
    }

    private String performChatNonStreaming(ProviderProfile provider, List<ChatMessage> messages,
                                          StreamCallback callback) throws Exception {
        JSONObject payload = buildChatPayload(provider, messages, false);
        HttpURLConnection connection = openConnection(provider, "/chat/completions", "POST", "application/json");
        activeConnection = connection;
        try {
            writeBody(connection, payload);
            int statusCode = connection.getResponseCode();
            String body = readBody(statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (statusCode < 200 || statusCode >= 300) {
                throw new ApiHttpException(statusCode, errorMessage(statusCode, body));
            }
            JSONObject root = new JSONObject(body);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return "";
            }
            JSONObject choice = choices.optJSONObject(0);
            JSONObject message = choice == null ? null : choice.optJSONObject("message");
            String text = message == null ? "" : extractContent(message.opt("content"));
            if (message != null) {
                String reasoning = extractReasoning(message);
                if (!reasoning.isEmpty()) {
                    callback.onReasoningDelta(reasoning);
                }
            }
            return text;
        } finally {
            connection.disconnect();
            activeConnection = null;
        }
    }

    private String performResponsesNonStreaming(ProviderProfile provider, List<ChatMessage> messages,
                                               StreamCallback callback) throws Exception {
        JSONObject payload = buildResponsesPayload(provider, messages, false);
        HttpURLConnection connection = openConnection(provider, "/responses", "POST", "application/json");
        activeConnection = connection;
        try {
            writeBody(connection, payload);
            int statusCode = connection.getResponseCode();
            String body = readBody(statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (statusCode < 200 || statusCode >= 300) {
                throw new ApiHttpException(statusCode, errorMessage(statusCode, body));
            }
            JSONObject root = new JSONObject(body);
            String reasoning = extractResponsesReasoning(root);
            if (!reasoning.isEmpty()) {
                callback.onReasoningDelta(reasoning);
            }
            return extractResponsesText(root);
        } catch (ApiHttpException error) {
            if (!cancelled && retryWithoutSummary(provider, error)) {
                return performResponsesNonStreaming(provider, messages, callback);
            }
            throw error;
        } finally {
            connection.disconnect();
            activeConnection = null;
        }
    }

    private JSONObject buildChatPayload(ProviderProfile provider, List<ChatMessage> messages,
                                        boolean streaming) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", provider.getSelectedModel());
        payload.put("stream", streaming);
        JSONArray input = new JSONArray();
        if (!provider.getSystemPrompt().trim().isEmpty()) {
            input.put(new JSONObject()
                    .put("role", ChatMessage.ROLE_SYSTEM)
                    .put("content", provider.getSystemPrompt().trim()));
        }
        appendMessages(input, messages, false);
        payload.put("messages", input);
        return payload;
    }

    private JSONObject buildResponsesPayload(ProviderProfile provider, List<ChatMessage> messages,
                                             boolean streaming) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", provider.getSelectedModel());
        payload.put("stream", streaming);
        if (!summaryUnsupported.contains(summaryKey(provider))) {
            payload.put("reasoning", new JSONObject().put("summary", "auto"));
        }
        if (!provider.getSystemPrompt().trim().isEmpty()) {
            payload.put("instructions", provider.getSystemPrompt().trim());
        }
        JSONArray input = new JSONArray();
        appendMessages(input, messages, true);
        payload.put("input", input);
        return payload;
    }

    private void appendMessages(JSONArray input, List<ChatMessage> messages, boolean responsesApi)
            throws Exception {
        for (ChatMessage message : messages) {
            if (ChatMessage.ROLE_SYSTEM.equals(message.getRole())
                    || ChatMessage.ROLE_NOTICE.equals(message.getRole())) {
                continue;
            }
            if (!message.hasAttachments()) {
                if (message.getContent().trim().isEmpty()) {
                    continue;
                }
                input.put(new JSONObject()
                        .put("role", message.getRole())
                        .put("content", message.getContent()));
                continue;
            }
            JSONArray parts = new JSONArray();
            if (!message.getContent().trim().isEmpty()) {
                parts.put(buildTextPart(message.getContent(), responsesApi));
            }
            for (ImageAttachment attachment : message.getAttachments()) {
                parts.put(buildImagePart(attachment, responsesApi));
            }
            input.put(new JSONObject()
                    .put("role", message.getRole())
                    .put("content", parts));
        }
    }

    private JSONObject buildTextPart(String text, boolean responsesApi) throws Exception {
        return responsesApi
                ? new JSONObject().put("type", "input_text").put("text", text)
                : new JSONObject().put("type", "text").put("text", text);
    }

    private JSONObject buildImagePart(ImageAttachment attachment, boolean responsesApi)
            throws Exception {
        String dataUrl = attachment.dataUrl();
        if (responsesApi) {
            return new JSONObject()
                    .put("type", "input_image")
                    .put("image_url", dataUrl);
        }
        return new JSONObject()
                .put("type", "image_url")
                .put("image_url", new JSONObject().put("url", dataUrl));
    }

    private HttpURLConnection openConnection(ProviderProfile provider, String leaf,
                                             String method, String accept) throws Exception {
        validateBaseUrl(provider);
        URL url = new URL(provider.endpoint(leaf));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout("GET".equals(method) ? MODEL_READ_TIMEOUT_MS : REPLY_READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", "RelayChat/1.0 Android");
        if (!provider.getApiKey().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
        }
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        return connection;
    }

    private void validateProvider(ProviderProfile provider) throws IOException {
        validateBaseUrl(provider);
        if (provider.getSelectedModel().trim().isEmpty()) {
            throw new IOException("请先选择或填写模型名称");
        }
    }

    private void validateBaseUrl(ProviderProfile provider) throws IOException {
        if (provider.getBaseUrl().trim().isEmpty()) {
            throw new IOException("请先填写 API 地址");
        }
        try {
            URI uri = new URI(provider.getBaseUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IOException("API 地址必须是有效的 HTTPS 地址");
            }
        } catch (Exception error) {
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("API 地址格式不正确");
        }
    }

    private void writeBody(HttpURLConnection connection, JSONObject payload) throws IOException {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
    }

    private String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String errorMessage(int statusCode, String body) {
        String detail = "";
        try {
            JSONObject root = new JSONObject(body == null ? "" : body);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                detail = error.optString("message", "");
            } else {
                detail = root.optString("message", "");
            }
        } catch (Exception ignored) {
            detail = body == null ? "" : body.trim();
        }
        if (detail.length() > 240) {
            detail = detail.substring(0, 240) + "...";
        }
        return detail.isEmpty() ? "请求失败（HTTP " + statusCode + "）" : "HTTP " + statusCode + "：" + detail;
    }

    private String extractResponsesText(JSONObject root) {
        String direct = extractContent(root.opt("output_text"));
        if (!direct.isEmpty()) {
            return direct;
        }
        JSONArray output = root.optJSONArray("output");
        if (output == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < output.length(); index++) {
            JSONObject item = output.optJSONObject(index);
            if (item == null) {
                continue;
            }
            JSONArray content = item.optJSONArray("content");
            if (content == null) {
                continue;
            }
            for (int contentIndex = 0; contentIndex < content.length(); contentIndex++) {
                JSONObject part = content.optJSONObject(contentIndex);
                if (part != null && "output_text".equals(part.optString("type", "output_text"))) {
                    String text = extractContent(part.opt("text"));
                    if (!text.isEmpty()) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }
                        result.append(text);
                    }
                }
            }
        }
        return result.toString();
    }

    private String extractReasoning(JSONObject message) {
        String text = extractContent(message.opt("reasoning_content"));
        if (text.isEmpty()) {
            text = extractContent(message.opt("reasoning"));
        }
        if (!text.isEmpty()) {
            return text;
        }
        JSONArray details = message.optJSONArray("reasoning_details");
        StringBuilder result = new StringBuilder();
        if (details != null) {
            for (int index = 0; index < details.length(); index++) {
                JSONObject detail = details.optJSONObject(index);
                if (detail == null) continue;
                String type = detail.optString("type", "");
                if ("reasoning.text".equals(type)) {
                    result.append(extractContent(detail.opt("text")));
                } else if ("reasoning.summary".equals(type)) {
                    result.append(extractContent(detail.opt("summary")));
                }
            }
        }
        return result.toString();
    }

    private static String summaryKey(ProviderProfile provider) {
        return provider.getBaseUrl() + "\n" + provider.getSelectedModel();
    }

    private boolean retryWithoutSummary(ProviderProfile provider, ApiHttpException error) {
        String message = error.getMessage().toLowerCase(java.util.Locale.ROOT);
        if ((error.statusCode == 400 || error.statusCode == 422)
                && (message.contains("reasoning") || message.contains("summary"))
                && (message.contains("unsupported") || message.contains("not supported")
                || message.contains("unknown") || message.contains("unrecognized")
                || message.contains("not allowed") || message.contains("不支持"))) {
            return summaryUnsupported.add(summaryKey(provider));
        }
        return false;
    }

    private static boolean isJsonResponse(HttpURLConnection connection) {
        String type = connection.getContentType();
        return type != null && type.toLowerCase(java.util.Locale.ROOT).contains("application/json");
    }

    private void emitJsonResponse(JSONObject root, boolean responsesApi, DeltaSink sink) {
        if (responsesApi) {
            sink.onReasoning(extractResponsesReasoning(root));
            sink.onDelta(extractResponsesText(root));
        } else {
            JSONArray choices = root.optJSONArray("choices");
            JSONObject choice = choices == null ? null : choices.optJSONObject(0);
            JSONObject message = choice == null ? null : choice.optJSONObject("message");
            if (message != null) {
                sink.onReasoning(extractReasoning(message));
                sink.onDelta(extractContent(message.opt("content")));
            }
        }
    }

    private String extractResponsesReasoning(JSONObject root) {
        StringBuilder result = new StringBuilder();
        JSONArray output = root.optJSONArray("output");
        if (output != null) {
            for (int index = 0; index < output.length(); index++) {
                JSONObject item = output.optJSONObject(index);
                if (item != null && "reasoning".equals(item.optString("type"))) {
                    String text = extractContent(item.opt("summary"));
                    if (text.isEmpty()) text = extractContent(item.opt("content"));
                    result.append(text);
                }
            }
        }
        return result.toString();
    }

    private static String outputKey(JSONObject event) {
        return event.optInt("output_index", 0) + ":" + event.optInt("content_index", 0);
    }

    private static String reasoningKey(JSONObject event) {
        return event.optInt("output_index", 0) + ":" + event.optInt("summary_index",
                event.optInt("content_index", 0));
    }

    private static void emitSnapshot(Map<String, String> parts, String key, String text,
                                     DeltaSink sink, boolean reasoning) {
        String existing = parts.getOrDefault(key, "");
        if (text.isEmpty() || !text.startsWith(existing)) return;
        String suffix = text.substring(existing.length());
        parts.put(key, text);
        if (reasoning) sink.onReasoning(suffix); else sink.onDelta(suffix);
    }

    private String extractContent(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < array.length(); index++) {
                Object item = array.opt(index);
                if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    String text = object.optString("text", "");
                    if (text.isEmpty()) {
                        text = object.optString("content", "");
                    }
                    result.append(text);
                } else {
                    result.append(extractContent(item));
                }
            }
            return result.toString();
        }
        return String.valueOf(value);
    }

    private boolean shouldRetryWithoutStreaming(int statusCode) {
        return statusCode == 400 || statusCode == 404 || statusCode == 405
                || statusCode == 415 || statusCode == 422 || statusCode == 500
                || statusCode == 501 || statusCode == 502 || statusCode == 503;
    }

    private String messageFor(Exception error) {
        if (error instanceof SocketTimeoutException
                || "timeout".equalsIgnoreCase(error.getMessage())
                || "Read timed out".equalsIgnoreCase(error.getMessage())) {
            return "网络请求超时。若仅退出或锁屏后出现，请点击聊天页的后台运行提示，"
                    + "允许后台运行并取消本应用的省电限制；已收到的回答会保留。";
        }
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "网络请求失败";
        }
        return message;
    }

    private static final class ApiHttpException extends IOException {
        private final int statusCode;

        private ApiHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
