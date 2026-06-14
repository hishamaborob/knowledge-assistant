package com.kbassistant.api.dto;

import com.kbassistant.domain.model.Document;

import java.time.Instant;

public record DocumentResponse(
        String id,
        String name,
        String originalFilename,
        String mimeType,
        long fileSizeBytes,
        String status,
        int chunkCount,
        String s3Key,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.id().value().toString(),
                doc.name(),
                doc.originalFilename(),
                doc.mimeType().mimeString(),
                doc.fileSizeBytes(),
                doc.status().name(),
                doc.chunkCount(),
                doc.s3Key(),
                doc.errorMessage(),
                doc.createdAt(),
                doc.updatedAt()
        );
    }
}
