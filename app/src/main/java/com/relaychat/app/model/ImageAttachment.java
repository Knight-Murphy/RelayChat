package com.relaychat.app.model;

import android.util.Base64;

import com.relaychat.app.data.ImageStore;

import java.util.UUID;

public final class ImageAttachment {
    private final String id;
    private final String mimeType;
    private byte[] data;
    private String dataUrlCache;

    public ImageAttachment(String mimeType, byte[] data) {
        this(UUID.randomUUID().toString(), mimeType, data);
    }

    public ImageAttachment(String id, String mimeType, byte[] data) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.mimeType = mimeType == null || mimeType.trim().isEmpty()
                ? "image/jpeg" : mimeType.trim();
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public String getMimeType() {
        return mimeType;
    }

    public byte[] getData() {
        if (data == null) {
            byte[] stored = ImageStore.read(id);
            data = stored == null ? new byte[0] : stored;
        }
        return data;
    }

    public String dataUrl() {
        if (dataUrlCache == null) {
            dataUrlCache = "data:" + mimeType + ";base64,"
                    + Base64.encodeToString(getData(), Base64.NO_WRAP);
        }
        return dataUrlCache;
    }

    public ImageAttachment copyWithData(String newMimeType, byte[] newData) {
        return new ImageAttachment(newMimeType, newData);
    }
}
