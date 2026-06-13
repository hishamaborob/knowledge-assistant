package com.kbassistant.domain.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DocumentChunk {

    private ChunkId id;
    private DocumentId documentId;
    private int chunkIndex;
    private String content;
    private float[] embedding;
    private int tokenCount;
    private String pageNumber;
    private Map<String, String> metadata;
    private Instant createdAt;

    private DocumentChunk() {}

    public static DocumentChunk create(DocumentId documentId,
                                       int chunkIndex,
                                       String content,
                                       float[] embedding,
                                       int tokenCount) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(content, "content");

        var chunk = new DocumentChunk();
        chunk.id = ChunkId.generate();
        chunk.documentId = documentId;
        chunk.chunkIndex = chunkIndex;
        chunk.content = content;
        chunk.embedding = embedding != null ? Arrays.copyOf(embedding, embedding.length) : null;
        chunk.tokenCount = tokenCount;
        chunk.metadata = new HashMap<>();
        chunk.createdAt = Instant.now();
        return chunk;
    }

    public static DocumentChunk reconstitute(ChunkId id,
                                             DocumentId documentId,
                                             int chunkIndex,
                                             String content,
                                             float[] embedding,
                                             int tokenCount,
                                             String pageNumber,
                                             Map<String, String> metadata,
                                             Instant createdAt) {
        var chunk = new DocumentChunk();
        chunk.id = id;
        chunk.documentId = documentId;
        chunk.chunkIndex = chunkIndex;
        chunk.content = content;
        chunk.embedding = embedding;
        chunk.tokenCount = tokenCount;
        chunk.pageNumber = pageNumber;
        chunk.metadata = metadata != null ? metadata : new HashMap<>();
        chunk.createdAt = createdAt;
        return chunk;
    }

    public DocumentChunk withMetadata(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }

    public ChunkId id()                      { return id; }
    public DocumentId documentId()           { return documentId; }
    public int chunkIndex()                  { return chunkIndex; }
    public String content()                  { return content; }
    public float[] embedding()               { return embedding != null ? Arrays.copyOf(embedding, embedding.length) : null; }
    public int tokenCount()                  { return tokenCount; }
    public String pageNumber()               { return pageNumber; }
    public Map<String, String> metadata()    { return Map.copyOf(metadata); }
    public Instant createdAt()               { return createdAt; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof DocumentChunk that)) return false;
        return Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
