package com.kbassistant.infrastructure.persistence.entity;

import com.kbassistant.infrastructure.persistence.type.PgVectorType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Vector embedding — 1536 dimensions for text-embedding-3-small.
     *
     * PgVectorType handles float[] ↔ PostgreSQL vector(1536) conversion.
     * The columnDefinition is required so Flyway creates the right column type
     * and so tools like EXPLAIN ANALYZE report the correct column.
     */
    @Type(PgVectorType.class)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "page_number", length = 50)
    private String pageNumber;

    /**
     * JSONB metadata: embedding model name, source file page, chunking params, etc.
     *
     * Stored as JSONB (binary JSON) — supports indexing and GIN queries in Phase 7+.
     * @JdbcTypeCode(SqlTypes.JSON) tells Hibernate to use Jackson for Map serialization.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId()                  { return id; }
    public void setId(UUID id)           { this.id = id; }

    public UUID getDocumentId()                      { return documentId; }
    public void setDocumentId(UUID documentId)       { this.documentId = documentId; }

    public int getChunkIndex()                   { return chunkIndex; }
    public void setChunkIndex(int chunkIndex)     { this.chunkIndex = chunkIndex; }

    public String getContent()               { return content; }
    public void setContent(String content)   { this.content = content; }

    public float[] getEmbedding()                    { return embedding; }
    public void setEmbedding(float[] embedding)      { this.embedding = embedding; }

    public int getTokenCount()                   { return tokenCount; }
    public void setTokenCount(int tokenCount)     { this.tokenCount = tokenCount; }

    public String getPageNumber()                    { return pageNumber; }
    public void setPageNumber(String pageNumber)     { this.pageNumber = pageNumber; }

    public Map<String, String> getMetadata()             { return metadata; }
    public void setMetadata(Map<String, String> metadata){ this.metadata = metadata; }

    public Instant getCreatedAt()                    { return createdAt; }
    public void setCreatedAt(Instant createdAt)      { this.createdAt = createdAt; }
}
