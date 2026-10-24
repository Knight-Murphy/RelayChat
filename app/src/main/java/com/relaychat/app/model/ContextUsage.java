package com.relaychat.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Estimates the request context without assuming a provider-specific tokenizer or window size. */
public final class ContextUsage {
    public static final int MAX_MESSAGES = 40;
    private static final int IMAGE_TOKENS = 1024;
    private static final int MESSAGE_OVERHEAD = 4;
    private static final int SYSTEM_OVERHEAD = 4;

    private ContextUsage() {
    }

    public static int estimate(ProviderProfile provider, List<ChatMessage> messages) {
        if (provider == null) {
            return 0;
        }
        String systemPrompt = provider.getSystemPrompt();
        int total = SYSTEM_OVERHEAD + estimateText(systemPrompt);
        List<ChatMessage> included = new ArrayList<>();
        if (messages != null) {
            for (ChatMessage message : messages) {
                if (message == null || message.isPending()
                        || ChatMessage.ROLE_NOTICE.equals(message.getRole())) {
                    continue;
                }
                if (message.getContent().trim().isEmpty() && !message.hasAttachments()) {
                    continue;
                }
                included.add(message);
            }
        }

        int start = Math.max(0, included.size() - MAX_MESSAGES);
        for (int index = start; index < included.size(); index++) {
            ChatMessage message = included.get(index);
            total += MESSAGE_OVERHEAD + estimateText(message.getContent());
            total += message.getAttachments().size() * IMAGE_TOKENS;
        }
        return total;
    }

    public static String format(int tokens) {
        int safe = Math.max(0, tokens);
        if (safe < 1000) {
            return String.valueOf(safe);
        }
        return String.format(Locale.ROOT, "%.1fK", safe / 1000.0);
    }

    private static int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint >= 128) {
                tokens += 1;
                if (asciiRun > 0) {
                    tokens += (asciiRun + 3) / 4;
                    asciiRun = 0;
                }
            } else if (Character.isWhitespace(codePoint)) {
                if (asciiRun > 0) {
                    tokens += (asciiRun + 3) / 4;
                    asciiRun = 0;
                }
                tokens += 1;
            } else {
                asciiRun++;
            }
        }
        if (asciiRun > 0) {
            tokens += (asciiRun + 3) / 4;
        }
        return tokens;
    }
}
