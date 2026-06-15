package com.kbassistant.domain.model;

public record SourceChunk(
        DocumentId documentId,
        String documentName,
        String contentSnippet,
        double score
) {}
