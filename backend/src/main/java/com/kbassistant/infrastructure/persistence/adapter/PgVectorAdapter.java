package com.kbassistant.infrastructure.persistence.adapter;

import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.VectorStorePort;
import com.kbassistant.infrastructure.persistence.entity.DocumentChunkEntity;
import com.kbassistant.infrastructure.persistence.repository.DocumentChunkJpaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;

/**
 * Implements VectorStorePort against PostgreSQL + pgvector.
 *
 * Save path: JPA — Hibernate handles batch inserts and the PgVectorType
 *            maps float[] to the wire protocol via PGvector.
 *
 * Search path: JdbcClient with native SQL — JPA cannot express the <=> operator
 *              or the CAST(:param AS vector) idiom. Native SQL gives us full
 *              control over the query plan and lets HNSW index be used.
 */
@Component
@Transactional
public class PgVectorAdapter implements VectorStorePort {

    // Similarity search without document filter
    private static final String SEARCH_SQL = """
            SELECT
                c.id,
                c.document_id,
                c.chunk_index,
                c.content,
                c.token_count,
                c.page_number,
                c.metadata::text,
                c.created_at,
                1 - (c.embedding <=> CAST(:queryVector AS vector)) AS score
            FROM document_chunks c
            WHERE 1 - (c.embedding <=> CAST(:queryVector AS vector)) >= :threshold
            ORDER BY c.embedding <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """;

    // Similarity search scoped to specific document IDs
    private static final String SEARCH_WITH_FILTER_SQL = """
            SELECT
                c.id,
                c.document_id,
                c.chunk_index,
                c.content,
                c.token_count,
                c.page_number,
                c.metadata::text,
                c.created_at,
                1 - (c.embedding <=> CAST(:queryVector AS vector)) AS score
            FROM document_chunks c
            WHERE c.document_id = ANY(CAST(:documentIds AS uuid[]))
            AND 1 - (c.embedding <=> CAST(:queryVector AS vector)) >= :threshold
            ORDER BY c.embedding <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """;

    private final DocumentChunkJpaRepository chunkRepository;
    private final JdbcClient jdbcClient;

    public PgVectorAdapter(DocumentChunkJpaRepository chunkRepository, JdbcClient jdbcClient) {
        this.chunkRepository = chunkRepository;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void saveChunks(List<DocumentChunk> chunks) {
        List<DocumentChunkEntity> entities = chunks.stream()
                .map(this::toEntity)
                .toList();
        // saveAllAndFlush: forces immediate write to DB tables before returning.
        // Required because JPA's auto-flush only triggers before *JPA* queries —
        // the JdbcClient similarity search is invisible to Hibernate's flush decision.
        chunkRepository.saveAllAndFlush(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoredChunk> similaritySearch(SimilaritySearchRequest request) {
        String vectorStr = toVectorString(request.queryVector());

        if (request.hasDocumentFilter()) {
            String docIds = toPostgresArray(request.documentIds());
            return jdbcClient.sql(SEARCH_WITH_FILTER_SQL)
                    .param("queryVector", vectorStr)
                    .param("threshold", request.similarityThreshold())
                    .param("topK", request.topK())
                    .param("documentIds", docIds)
                    .query(this::mapRow)
                    .list();
        }

        return jdbcClient.sql(SEARCH_SQL)
                .param("queryVector", vectorStr)
                .param("threshold", request.similarityThreshold())
                .param("topK", request.topK())
                .query(this::mapRow)
                .list();
    }

    @Override
    public void deleteByDocumentId(DocumentId documentId) {
        chunkRepository.deleteByDocumentId(documentId.value());
    }

    @Override
    @Transactional(readOnly = true)
    public int countByDocumentId(DocumentId documentId) {
        return chunkRepository.countByDocumentId(documentId.value());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ScoredChunk mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        ChunkId chunkId = ChunkId.of(UUID.fromString(rs.getString("id")));
        DocumentId documentId = DocumentId.of(UUID.fromString(rs.getString("document_id")));

        DocumentChunk chunk = DocumentChunk.reconstitute(
                chunkId,
                documentId,
                rs.getInt("chunk_index"),
                rs.getString("content"),
                null, // embedding not returned in search results — avoids sending 6KB per row
                rs.getInt("token_count"),
                rs.getString("page_number"),
                Map.of(), // metadata parsed lazily in Phase 7 if needed
                rs.getTimestamp("created_at").toInstant()
        );

        double score = rs.getDouble("score");
        return new ScoredChunk(chunk, Math.min(1.0, Math.max(0.0, score)));
    }

    private DocumentChunkEntity toEntity(DocumentChunk chunk) {
        var e = new DocumentChunkEntity();
        e.setId(chunk.id().value());
        e.setDocumentId(chunk.documentId().value());
        e.setChunkIndex(chunk.chunkIndex());
        e.setContent(chunk.content());
        e.setEmbedding(chunk.embedding());
        e.setTokenCount(chunk.tokenCount());
        e.setPageNumber(chunk.pageNumber());
        e.setMetadata(chunk.metadata().isEmpty() ? null : new HashMap<>(chunk.metadata()));
        e.setCreatedAt(chunk.createdAt());
        return e;
    }

    /**
     * Converts float[] to pgvector string format: [1.0,2.0,3.0]
     * pgvector accepts both with and without spaces; we omit them for brevity.
     */
    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    /**
     * Converts List<DocumentId> to PostgreSQL array literal: {uuid1,uuid2,...}
     * Used with CAST(:documentIds AS uuid[]) in the SQL filter.
     */
    private String toPostgresArray(List<DocumentId> ids) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        ids.forEach(id -> joiner.add(id.value().toString()));
        return joiner.toString();
    }
}
