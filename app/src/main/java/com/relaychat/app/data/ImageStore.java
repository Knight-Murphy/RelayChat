package com.relaychat.app.data;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public final class ImageStore {
    private static final String SUFFIX = ".img";

    private static File directory;

    private ImageStore() {
    }

    public static synchronized void init(File target) {
        directory = target;
        if (target != null && !target.exists()) {
            target.mkdirs();
        }
    }

    public static synchronized boolean exists(String id) {
        return directory != null && id != null && !id.isEmpty()
                && new File(directory, id + SUFFIX).exists();
    }

    public static synchronized byte[] read(String id) {
        if (!exists(id)) {
            return null;
        }
        try (InputStream input = new FileInputStream(new File(directory, id + SUFFIX))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException error) {
            return null;
        }
    }

    public static synchronized void write(String id, byte[] data) {
        if (directory == null || id == null || id.isEmpty() || data == null || data.length == 0) {
            return;
        }
        if (!directory.exists()) {
            directory.mkdirs();
        }
        try (FileOutputStream output = new FileOutputStream(new File(directory, id + SUFFIX))) {
            output.write(data);
            output.flush();
        } catch (IOException ignored) {
            // A failed image write only costs the thumbnail, never the message text.
        }
    }

    public static synchronized void delete(String id) {
        if (directory == null || id == null || id.isEmpty()) {
            return;
        }
        File file = new File(directory, id + SUFFIX);
        if (file.exists()) {
            file.delete();
        }
    }

    public static synchronized Set<String> listIds() {
        Set<String> ids = new HashSet<>();
        if (directory == null) {
            return ids;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return ids;
        }
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(SUFFIX)) {
                ids.add(name.substring(0, name.length() - SUFFIX.length()));
            }
        }
        return ids;
    }
}
