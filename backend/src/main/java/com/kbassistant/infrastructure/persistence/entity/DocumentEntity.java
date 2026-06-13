package com.kbassistant.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the documents table.
 *
 * Deliberately separate from the domain Document class.
 * JPA requires a no-arg constructor and mutable state — constraints that
 * conflict with the domain model's factory-method pattern and state machine.
 * The DocumentJpaAdapter handles the mapping between the two.
 */
@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "s3_key", length = 1000)
    private String s3Key;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId()                  { return id; }
    public void setId(UUID id)           { this.id = id; }

    public String getName()              { return name; }
    public void setName(String name)     { this.name = name; }

    public String getOriginalFilename()                          { return originalFilename; }
    public void setOriginalFilename(String originalFilename)     { this.originalFilename = originalFilename; }

    public String getMimeType()              { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getFileSizeBytes()                   { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getCreatedBy()                 { return createdBy; }
    public void setCreatedBy(String createdBy)   { this.createdBy = createdBy; }

    public String getStatus()                { return status; }
    public void setStatus(String status)     { this.status = status; }

    public String getS3Key()             { return s3Key; }
    public void setS3Key(String s3Key)   { this.s3Key = s3Key; }

    public int getChunkCount()                   { return chunkCount; }
    public void setChunkCount(int chunkCount)     { this.chunkCount = chunkCount; }

    public String getErrorMessage()                      { return errorMessage; }
    public void setErrorMessage(String errorMessage)     { this.errorMessage = errorMessage; }

    public Instant getCreatedAt()                    { return createdAt; }
    public void setCreatedAt(Instant createdAt)      { this.createdAt = createdAt; }

    public Instant getUpdatedAt()                    { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt)      { this.updatedAt = updatedAt; }
}
