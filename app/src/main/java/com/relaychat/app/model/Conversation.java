package com.relaychat.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Conversation {
    private static final int TITLE_LENGTH = 16;
    private static final int MAX_TITLE_LENGTH = 40;

    private final String id;
    private final List<ChatMessage> messages = new ArrayList<>();
    private String customTitle = "";

    public Conversation() {
        this(UUID.randomUUID().toString());
    }

    public Conversation(String id) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
    }

    public String getId() {
        return id;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public boolean hasMessages() {
        for (ChatMessage message : messages) {
            if (!ChatMessage.ROLE_NOTICE.equals(message.getRole())) {
                return true;
            }
        }
        return false;
    }

    /** The user-defined name, or an empty string while the automatic title is used. */
    public String getCustomTitle() {
        return customTitle;
    }

    /** Applies a user-defined name; a blank value restores the automatic title. */
    public void setTitle(String title) {
        String text = title == null ? "" : title.replace('\n', ' ').replace('\r', ' ').trim();
        customTitle = text.length() > MAX_TITLE_LENGTH ? text.substring(0, MAX_TITLE_LENGTH) : text;
    }

    public String getTitle() {
        if (!customTitle.isEmpty()) {
            return customTitle;
        }
        for (ChatMessage message : messages) {
            if (!ChatMessage.ROLE_USER.equals(message.getRole())) {
                continue;
            }
            String text = message.getContent().trim().replace('\n', ' ');
            if (!text.isEmpty()) {
                return text.length() > TITLE_LENGTH
                        ? text.substring(0, TITLE_LENGTH) + "…" : text;
            }
            if (message.hasAttachments()) {
                return "图片消息";
            }
        }
        return "新对话";
    }

    public String getSummary() {
        int count = 0;
        for (ChatMessage message : messages) {
            if (!ChatMessage.ROLE_NOTICE.equals(message.getRole())) {
                count++;
            }
        }
        return count == 0 ? "还没有消息" : "共 " + count + " 条消息";
    }
}
