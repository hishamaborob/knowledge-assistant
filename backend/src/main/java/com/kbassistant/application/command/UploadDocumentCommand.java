package com.kbassistant.application.command;

import com.kbassistant.domain.model.MimeType;

public record UploadDocumentCommand(
        String name,
        String originalFilename,
        byte[] content,
        MimeType mimeType,
        long fileSizeBytes,
        String uploadedBy
) {}
