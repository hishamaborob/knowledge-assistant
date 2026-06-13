package com.kbassistant.domain.model;

import java.util.Arrays;
import java.util.Optional;

public enum MimeType {
    PDF("application/pdf", "pdf"),
    TEXT("text/plain", "txt"),
    MARKDOWN("text/markdown", "md");

    private final String mimeString;
    private final String extension;

    MimeType(String mimeString, String extension) {
        this.mimeString = mimeString;
        this.extension = extension;
    }

    public String mimeString() {
        return mimeString;
    }

    public String extension() {
        return extension;
    }

    public static Optional<MimeType> fromMimeString(String mime) {
        return Arrays.stream(values())
                .filter(m -> m.mimeString.equalsIgnoreCase(mime))
                .findFirst();
    }

    public static Optional<MimeType> fromExtension(String ext) {
        String normalized = ext.startsWith(".") ? ext.substring(1) : ext;
        return Arrays.stream(values())
                .filter(m -> m.extension.equalsIgnoreCase(normalized))
                .findFirst();
    }
}
