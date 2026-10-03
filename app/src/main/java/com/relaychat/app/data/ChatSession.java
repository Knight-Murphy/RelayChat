package com.relaychat.app.data;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.relaychat.app.ReplyService;
import com.relaychat.app.model.ChatMessage;
import com.relaychat.app.model.Conversation;
import com.relaychat.app.model.ProviderProfile;
import com.relaychat.app.network.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns chat data and generation independently of any Activity. All state changes use the main thread. */
public final class ChatSession {
    public interface Listener {
        void onReplyChanged();
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConversationStore store;
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private final ApiClient replies = new ApiClient();
    private final List<Conversation> conversations = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private String activeId;
    private long nextRequestId;
    private long completedRequestId;
    private boolean replySucceeded;
    private String completedConversationId = "";
    private Request request;
    private boolean checkpointScheduled;
    private final Runnable checkpoint = () -> {
        checkpointScheduled = false;
        persist();
    };

    public ChatSession(Context context) {
        this.context = context.getApplicationContext();
        store = new ConversationStore(context);
        ConversationStore.LoadedState loaded = store.load();
        conversations.addAll(loaded.getConversations());
        activeId = loaded.getActiveId();
        if (conversations.isEmpty()) {
            createConversation();
        } else if (findConversation(activeId) == null) {
            activeId = conversations.get(0).getId();
        }
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    public String getActiveId() {
        return activeId;
    }

    public void setActiveId(String id) {
        activeId = id;
    }

    public Conversation getActiveConversation() {
        return findConversation(activeId);
    }

    public Conversation createConversation() {
        Conversation conversation = new Conversation();
        conversations.add(0, conversation);
        activeId = conversation.getId();
        return conversation;
    }

    private Conversation findConversation(String id) {
        for (Conversation conversation : conversations) {
            if (conversation.getId().equals(id)) {
                return conversation;
            }
        }
        return null;
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isSending() {
        return request != null;
    }

    public long getRequestId() {
        return request == null ? 0 : request.id;
    }

    public ChatMessage getReplyMessage() {
        return request == null ? null : request.assistant;
    }

    public boolean didReplySucceed(long id) {
        return id != 0 && completedRequestId == id && replySucceeded;
    }

    public String getCompletedConversationId() {
        return completedConversationId;
    }

    /** Starts the service while the sending Activity is visible. Secrets never enter an Intent. */
    public void send(ProviderProfile provider, ChatMessage user) {
        if (isSending()) {
            return;
        }
        Conversation conversation = getActiveConversation();
        conversation.getMessages().add(user);
        List<ChatMessage> snapshot = new ArrayList<>();
        for (ChatMessage message : conversation.getMessages()) {
            if (ChatMessage.ROLE_NOTICE.equals(message.getRole())) {
                continue;
            }
            if (!message.getContent().trim().isEmpty() || message.hasAttachments()) {
                snapshot.add(new ChatMessage(message.getRole(), message.getContent(),
                        message.getAttachments()));
            }
        }
        if (snapshot.size() > 40) {
            snapshot = new ArrayList<>(snapshot.subList(snapshot.size() - 40, snapshot.size()));
        }
        ChatMessage assistant = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "");
        assistant.setPending(true);
        assistant.startProcessing();
        conversation.getMessages().add(assistant);
        ProviderProfile frozen = new ProviderProfile(provider.getId(), provider.getName(),
                provider.getBaseUrl(), provider.getApiKey(), provider.getMode(),
                provider.getModels(), provider.getSelectedModel(), provider.getSystemPrompt());
        Request pending = new Request(++nextRequestId, conversation.getId(), frozen, snapshot, assistant);
        request = pending;
        persist();
        notifyListeners();
        Intent intent = new Intent(context, ReplyService.class)
                .putExtra(ReplyService.EXTRA_REQUEST_ID, pending.id);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException error) {
            finish(pending, null, "无法启动后台回答服务：" + error.getMessage());
        }
    }

    /** Called only after the service has entered the foreground. */
    public void beginReply(long id) {
        Request pending = request;
        if (pending == null || pending.id != id || pending.started || pending.finishing) {
            return;
        }
        pending.started = true;
        replies.streamChat(pending.provider, pending.messages, new ApiClient.StreamCallback() {
            @Override
            public void onStage(String stage) {
                main.post(() -> {
                    if (request != pending || pending.finishing) {
                        return;
                    }
                    if (ChatMessage.STAGE_THINKING.equals(stage)) {
                        pending.assistant.beginReasoning();
                    } else {
                        pending.assistant.setStage(stage);
                    }
                    scheduleCheckpoint();
                    notifyListeners();
                });
            }

            @Override
            public void onReasoningDelta(String text) {
                main.post(() -> {
                    if (request != pending || pending.finishing) {
                        return;
                    }
                    pending.assistant.beginReasoning();
                    pending.reasoning.append(text);
                    pending.assistant.setReasoning(pending.reasoning.toString());
                    scheduleCheckpoint();
                    notifyListeners();
                });
            }

            @Override
            public void onDelta(String text) {
                main.post(() -> {
                    if (request != pending || pending.finishing) {
                        return;
                    }
                    pending.text.append(text);
                    pending.assistant.beginAnswer();
                    pending.assistant.setContent(pending.text.toString());
                    scheduleCheckpoint();
                    notifyListeners();
                });
            }

            @Override
            public void onComplete(String text) {
                main.post(() -> finish(pending, text, ""));
            }

            @Override
            public void onError(String error) {
                main.post(() -> finish(pending, null, error));
            }

            @Override
            public void onCancelled() {
                main.post(() -> finish(pending, null, "", ChatMessage.STAGE_STOPPED));
            }
        });
    }

    public void cancelReply() {
        if (request == null || request.finishing) {
            return;
        }
        if (!request.started) {
            finish(request, null, "", ChatMessage.STAGE_STOPPED);
        } else {
            replies.cancel();
        }
    }

    public void abortReply(String reason) {
        if (request != null && !request.finishing) {
            replies.cancel();
            finish(request, null, reason);
        }
    }

    private void finish(Request pending, String fullText, String error) {
        finish(pending, fullText, error, fullText != null
                ? ChatMessage.STAGE_COMPLETED : ChatMessage.STAGE_FAILED);
    }

    private void finish(Request pending, String fullText, String error, String finalStage) {
        if (request != pending || pending.finishing) {
            return;
        }
        pending.finishing = true;
        main.removeCallbacks(checkpoint);
        checkpointScheduled = false;
        if (fullText != null) {
            pending.assistant.setContent(fullText);
        }
        pending.assistant.setPending(false);
        pending.assistant.setError(error);
        pending.assistant.finishProcessing(finalStage);
        // Keep the foreground service alive until the final snapshot has been written.
        persist(() -> {
            if (request == pending) {
                completedRequestId = pending.id;
                replySucceeded = fullText != null;
                completedConversationId = pending.conversationId;
                request = null;
                notifyListeners();
            }
        });
    }

    public void persist() {
        persist(null);
    }

    private void scheduleCheckpoint() {
        if (!checkpointScheduled) {
            checkpointScheduled = true;
            main.postDelayed(checkpoint, 1000);
        }
    }

    private void persist(Runnable afterWrite) {
        ConversationStore.Payload payload = store.encode(conversations, activeId);
        storage.execute(() -> {
            store.write(payload);
            if (afterWrite != null) {
                main.post(afterWrite);
            }
        });
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onReplyChanged();
        }
    }

    private static final class Request {
        final long id;
        final String conversationId;
        final ProviderProfile provider;
        final List<ChatMessage> messages;
        final ChatMessage assistant;
        final StringBuilder text = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        boolean started;
        boolean finishing;

        Request(long id, String conversationId, ProviderProfile provider, List<ChatMessage> messages,
                ChatMessage assistant) {
            this.id = id;
            this.conversationId = conversationId;
            this.provider = provider;
            this.messages = messages;
            this.assistant = assistant;
        }
    }
}
