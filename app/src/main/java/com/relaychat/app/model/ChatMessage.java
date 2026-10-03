package com.relaychat.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatMessage {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_NOTICE = "notice";
    public static final String STAGE_PREPARING = "preparing";
    public static final String STAGE_CONNECTING = "connecting";
    public static final String STAGE_WAITING = "waiting";
    public static final String STAGE_THINKING = "thinking";
    public static final String STAGE_ANSWERING = "answering";
    public static final String STAGE_FALLBACK = "fallback";
    public static final String STAGE_COMPLETED = "completed";
    public static final String STAGE_STOPPED = "stopped";
    public static final String STAGE_FAILED = "failed";
    public static final String STAGE_INTERRUPTED = "interrupted";

    private final String role;
    private final List<ImageAttachment> attachments;
    private String content;
    private boolean pending;
    private String error = "";
    private String reasoning = "";
    private String stage = "";
    private long processingDurationMs;
    private long reasoningDurationMs;
    private long processingStartedNanos;
    private long reasoningStartedNanos;
    private final List<ProcessingStep> processingSteps = new ArrayList<>();

    public static final class ProcessingStep {
        private final String stage;
        private final long startedMs;

        public ProcessingStep(String stage, long startedMs) {
            this.stage = stage;
            this.startedMs = Math.max(0, startedMs);
        }

        public String getStage() { return stage; }
        public long getStartedMs() { return startedMs; }
    }

    public List<ProcessingStep> getProcessingSteps() {
        return Collections.unmodifiableList(processingSteps);
    }

    public void restoreProcessingSteps(List<ProcessingStep> steps) {
        processingSteps.clear();
        processingSteps.addAll(steps);
    }

    public void startProcessing() {
        processingStartedNanos = System.nanoTime();
        processingSteps.clear();
        setStage(STAGE_PREPARING);
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage == null ? "" : stage;
        if (!this.stage.isEmpty() && (processingSteps.isEmpty()
                || !this.stage.equals(processingSteps.get(processingSteps.size() - 1).getStage()))) {
            processingSteps.add(new ProcessingStep(this.stage, getProcessingDurationMs()));
        }
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning == null ? "" : reasoning;
    }

    public void beginReasoning() {
        if (reasoningStartedNanos == 0) {
            reasoningStartedNanos = System.nanoTime();
        }
        setStage(STAGE_THINKING);
    }

    public void beginAnswer() {
        finishReasoning();
        setStage(STAGE_ANSWERING);
    }

    private void finishReasoning() {
        reasoningDurationMs = getReasoningDurationMs();
        reasoningStartedNanos = 0;
    }

    public long getProcessingDurationMs() {
        return processingDurationMs + elapsedMs(processingStartedNanos);
    }

    public long getReasoningDurationMs() {
        return reasoningDurationMs + elapsedMs(reasoningStartedNanos);
    }

    public void restoreDurations(long processingMs, long reasoningMs) {
        processingDurationMs = Math.max(0, processingMs);
        reasoningDurationMs = Math.max(0, reasoningMs);
    }

    public void finishProcessing(String finalStage) {
        processingDurationMs = getProcessingDurationMs();
        processingStartedNanos = 0;
        finishReasoning();
        setStage(finalStage);
    }

    private static long elapsedMs(long startedNanos) {
        return startedNanos == 0 ? 0 : Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    public ChatMessage(String role, String content) {
        this(role, content, Collections.<ImageAttachment>emptyList());
    }

    public ChatMessage(String role, String content, List<ImageAttachment> attachments) {
        this.role = role;
        this.content = content == null ? "" : content;
        this.attachments = attachments == null || attachments.isEmpty()
                ? Collections.<ImageAttachment>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(attachments));
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content;
    }

    public boolean isPending() {
        return pending;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error == null ? "" : error;
    }

    public List<ImageAttachment> getAttachments() {
        return attachments;
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }
}
