package com.kbassistant.infrastructure.persistence.entity;

/**
 * Plain JSONB-storable shape for a citation. Deliberately not the domain
 * SourceChunk — SourceChunk carries a DocumentId value object that Jackson
 * cannot serialize/deserialize without a custom module. This record uses
 * only primitives/String so Hibernate's built-in JSON support (Jackson
 * under the hood) handles it with zero extra configuration.
 */
public record CitationRecord(
        String documentId,
        String documentName,
        String contentSnippet,
        double score
) {}
