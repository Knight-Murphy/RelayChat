package com.relaychat.app.data;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.relaychat.app.model.CodeExecution;
import com.relaychat.app.model.ImageAttachment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Runs model-authored Python with the CPython runtime embedded in the APK. */
public final class LocalPythonExecutor {
    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;

    private final Context context;
    private final File cacheDirectory;

    public LocalPythonExecutor(Context context) {
        this.context = context.getApplicationContext();
        cacheDirectory = new File(this.context.getCacheDir(), "python");
    }

    public CodeExecution execute(String code) {
        String id = "local-" + UUID.randomUUID();
        String source = code == null ? "" : code;
        File workDirectory = new File(cacheDirectory, UUID.randomUUID().toString());
        if (!workDirectory.mkdirs() && !workDirectory.isDirectory()) {
            return failed(id, source, "无法创建 Python 临时工作目录");
        }
        try {
            synchronized (Python.class) {
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(context));
                }
            }
            PyObject module = Python.getInstance().getModule("relay_executor");
            String raw = module.callAttr(
                    "run", source, workDirectory.getAbsolutePath()).toString();
            JSONObject result = new JSONObject(raw);
            List<ImageAttachment> images = readImages(workDirectory,
                    result.optJSONArray("images"));
            return new CodeExecution(id, source, result.optString("logs", ""),
                    result.optString("status", "completed"), images);
        } catch (Throwable error) {
            String detail = error.getMessage();
            if (detail == null || detail.trim().isEmpty()) {
                detail = error.getClass().getSimpleName();
            }
            return failed(id, source, "Python 执行器启动失败：\n" + detail);
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static CodeExecution failed(String id, String code, String logs) {
        return new CodeExecution(id, code, logs, "failed",
                java.util.Collections.<ImageAttachment>emptyList());
    }

    private List<ImageAttachment> readImages(File workDirectory, JSONArray values)
            throws IOException {
        List<ImageAttachment> images = new ArrayList<>();
        if (values == null) {
            return images;
        }
        String root = workDirectory.getCanonicalPath() + File.separator;
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) {
                continue;
            }
            File image = new File(workDirectory, value.optString("name", ""));
            String path = image.getCanonicalPath();
            if (!path.startsWith(root) || !image.isFile()
                    || image.length() <= 0 || image.length() > MAX_IMAGE_BYTES) {
                continue;
            }
            byte[] data = readFile(image);
            if (data.length == 0) {
                continue;
            }
            String mime = value.optString("mime", "image/png")
                    .toLowerCase(Locale.ROOT);
            ImageAttachment attachment = new ImageAttachment(mime, data);
            ImageStore.write(attachment.getId(), data);
            images.add(attachment);
        }
        return images;
    }

    private static byte[] readFile(File source) throws IOException {
        try (InputStream input = new FileInputStream(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16_384];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_IMAGE_BYTES) {
                    return new byte[0];
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        target.delete();
    }
}
