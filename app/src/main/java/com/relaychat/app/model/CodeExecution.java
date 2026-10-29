package com.relaychat.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One record of model-authored Python executed by the Android app. */
public final class CodeExecution {
    private final String id;
    private final String code;
    private final String logs;
    private final String status;
    private final List<ImageAttachment> images;

    public CodeExecution(String id, String code, String logs, String status,
                         List<ImageAttachment> images) {
        this.id = id == null || id.trim().isEmpty() ? "python" : id.trim();
        this.code = code == null ? "" : code;
        this.logs = logs == null ? "" : logs;
        this.status = status == null ? "" : status.trim();
        this.images = images == null || images.isEmpty()
                ? Collections.<ImageAttachment>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(images));
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLogs() {
        return logs;
    }

    public String getStatus() {
        return status;
    }

    public List<ImageAttachment> getImages() {
        return images;
    }

    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }
}
