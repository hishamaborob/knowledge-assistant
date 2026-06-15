package com.kbassistant.api.dto;

import com.kbassistant.domain.model.SourceChunk;

public record SourceChunkResponse(
        String documentId,
        String documentName,
        String contentSnippet,
        double score
) {
    public static SourceChunkResponse from(SourceChunk source) {
        return new SourceChunkResponse(
                source.documentId().value().toString(),
                source.documentName(),
                source.contentSnippet(),
                source.score()
        );
    }
}
