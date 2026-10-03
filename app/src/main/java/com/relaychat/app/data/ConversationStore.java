package com.relaychat.app.data;

import android.content.Context;

import com.relaychat.app.model.ChatMessage;
import com.relaychat.app.model.Conversation;
import com.relaychat.app.model.ImageAttachment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ConversationStore {
    private static final String FILE_NAME = "conversations.json";
    private static final String TEMP_FILE_NAME = "conversations.json.tmp";
    private static final int VERSION = 5;

    private final File file;
    private final File tempFile;

    public ConversationStore(Context context) {
        Context appContext = context.getApplicationContext();
        ImageStore.init(new File(appContext.getFilesDir(), "images"));
        this.file = new File(appContext.getFilesDir(), FILE_NAME);
        this.tempFile = new File(appContext.getFilesDir(), TEMP_FILE_NAME);
    }

    public LoadedState load() {
        LoadedState state = new LoadedState();
        String raw = readText(file);
        if (raw == null || raw.trim().isEmpty()) {
            return state;
        }
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray array = root.optJSONArray("conversations");
            if (array != null) {
                for (int index = 0; index < array.length(); index++) {
                    JSONObject item = array.optJSONObject(index);
                    if (item == null) {
                        continue;
                    }
                    Conversation conversation = new Conversation(item.optString("id", ""));
                    conversation.setTitle(item.optString("title", ""));
                    JSONArray messages = item.optJSONArray("messages");
                    if (messages != null) {
                        for (int messageIndex = 0; messageIndex < messages.length(); messageIndex++) {
                            ChatMessage message = parseMessage(messages.optJSONObject(messageIndex));
                            if (message != null) {
                                conversation.getMessages().add(message);
                            }
                        }
                    }
                    state.conversations.add(conversation);
                }
            }
            state.activeId = root.optString("active", "");
        } catch (Exception error) {
            // Corrupt metadata should not block startup; the file is rewritten on the next save.
            state.conversations.clear();
            state.activeId = "";
        }
        return state;
    }

    public Payload encode(List<Conversation> conversations, String activeId) {
        Payload payload = new Payload();
        JSONArray array = new JSONArray();
        try {
            for (Conversation conversation : conversations) {
                JSONObject item = new JSONObject();
                item.put("id", conversation.getId());
                item.put("title", conversation.getCustomTitle());
                JSONArray messages = new JSONArray();
                for (ChatMessage message : conversation.getMessages()) {
                    JSONObject encoded = new JSONObject();
                    encoded.put("role", message.getRole());
                    encoded.put("content", message.getContent());
                    if (!message.getStage().isEmpty()) {
                        encoded.put("stage", message.getStage());
                        encoded.put("processing_ms", message.getProcessingDurationMs());
                        encoded.put("reasoning_ms", message.getReasoningDurationMs());
                    }
                    if (!message.getReasoning().isEmpty()) {
                        encoded.put("reasoning", message.getReasoning());
                    }
                    if (!message.getProcessingSteps().isEmpty()) {
                        JSONArray steps = new JSONArray();
                        for (ChatMessage.ProcessingStep step : message.getProcessingSteps()) {
                            steps.put(new JSONObject().put("stage", step.getStage())
                                    .put("started_ms", step.getStartedMs()));
                        }
                        encoded.put("processing_steps", steps);
                    }
                    if (message.isPending()) {
                        encoded.put("pending", true);
                    }
                    if (!message.getError().isEmpty()) {
                        encoded.put("error", message.getError());
                    }
                    if (message.hasAttachments()) {
                        JSONArray images = new JSONArray();
                        for (ImageAttachment attachment : message.getAttachments()) {
                            payload.images.add(attachment);
                            payload.imageIds.add(attachment.getId());
                            images.put(new JSONObject()
                                    .put("id", attachment.getId())
                                    .put("mime", attachment.getMimeType()));
                        }
                        encoded.put("images", images);
                    }
                    messages.put(encoded);
                }
                item.put("messages", messages);
                array.put(item);
            }
            JSONObject root = new JSONObject();
            root.put("version", VERSION);
            root.put("active", activeId == null ? "" : activeId);
            root.put("conversations", array);
            payload.json = root.toString();
        } catch (Exception error) {
            payload.json = null;
        }
        return payload;
    }

    public void write(Payload payload) {
        if (payload == null || payload.json == null) {
            return;
        }
        try {
            for (ImageAttachment attachment : payload.images) {
                if (!ImageStore.exists(attachment.getId())) {
                    ImageStore.write(attachment.getId(), attachment.getData());
                }
            }
            writeText(tempFile, payload.json);
            if (!tempFile.renameTo(file)) {
                writeText(file, payload.json);
                tempFile.delete();
            }
            for (String id : ImageStore.listIds()) {
                if (!payload.imageIds.contains(id)) {
                    ImageStore.delete(id);
                }
            }
        } catch (Exception ignored) {
            // A failed save must not crash the app; the next save retries.
        }
    }

    private ChatMessage parseMessage(JSONObject message) {
        if (message == null) {
            return null;
        }
        String content = message.optString("content", "");
        List<ImageAttachment> attachments = new ArrayList<>();
        JSONArray images = message.optJSONArray("images");
        if (images != null) {
            for (int index = 0; index < images.length(); index++) {
                JSONObject image = images.optJSONObject(index);
                if (image == null) {
                    continue;
                }
                String id = image.optString("id", "");
                if (id.isEmpty() || !ImageStore.exists(id)) {
                    continue;
                }
                attachments.add(new ImageAttachment(id,
                        image.optString("mime", "image/jpeg"), null));
            }
        }
        boolean interrupted = message.optBoolean("pending", false);
        String error = interrupted ? "回复因应用进程结束而中断，已保留收到的内容，请重新发送"
                : message.optString("error", "");
        String reasoning = message.optString("reasoning", "");
        if (content.trim().isEmpty() && reasoning.isEmpty() && attachments.isEmpty() && error.isEmpty()
                && message.optString("stage", "").isEmpty()) {
            return null;
        }
        ChatMessage parsed = new ChatMessage(
                message.optString("role", ChatMessage.ROLE_USER), content, attachments);
        parsed.setError(error);
        parsed.setReasoning(reasoning);
        parsed.restoreDurations(message.optLong("processing_ms", 0), message.optLong("reasoning_ms", 0));
        List<ChatMessage.ProcessingStep> steps = new ArrayList<>();
        JSONArray savedSteps = message.optJSONArray("processing_steps");
        if (savedSteps != null) {
            long lastStart = 0;
            for (int index = 0; index < savedSteps.length(); index++) {
                JSONObject step = savedSteps.optJSONObject(index);
                if (step != null && !step.optString("stage", "").isEmpty()) {
                    long start = Math.max(lastStart, Math.min(parsed.getProcessingDurationMs(),
                            step.optLong("started_ms", 0)));
                    steps.add(new ChatMessage.ProcessingStep(step.optString("stage"), start));
                    lastStart = start;
                }
            }
        }
        parsed.restoreProcessingSteps(steps);
        parsed.setStage(interrupted ? ChatMessage.STAGE_INTERRUPTED : message.optString("stage", ""));
        return parsed;
    }

    private static String readText(File source) {
        if (!source.exists()) {
            return null;
        }
        try (InputStream input = new FileInputStream(source)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return null;
        }
    }

    private static void writeText(File target, String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    public static final class Payload {
        String json;
        final List<ImageAttachment> images = new ArrayList<>();
        final Set<String> imageIds = new HashSet<>();
    }

    public static final class LoadedState {
        private final List<Conversation> conversations = new ArrayList<>();
        private String activeId = "";

        public List<Conversation> getConversations() {
            return conversations;
        }

        public String getActiveId() {
            return activeId;
        }
    }
}
