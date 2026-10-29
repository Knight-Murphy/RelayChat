package com.relaychat.app.model;

import java.util.Locale;

/** Finds the first complete fenced Python block in a model reply. */
public final class PythonCodeExtractor {
    private PythonCodeExtractor() {
    }

    public static String extractFirstBlock(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return null;
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\n", -1);
        for (int start = 0; start < lines.length; start++) {
            String opening = lines[start].trim().toLowerCase(Locale.ROOT);
            if (!"```python".equals(opening) && !"```py".equals(opening)) {
                continue;
            }
            StringBuilder source = new StringBuilder();
            for (int end = start + 1; end < lines.length; end++) {
                if ("```".equals(lines[end].trim())) {
                    String code = source.toString();
                    return code.trim().isEmpty() ? null : code;
                }
                if (source.length() > 0) {
                    source.append('\n');
                }
                source.append(lines[end]);
            }
            return null;
        }
        return null;
    }
}
