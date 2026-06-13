package com.kbassistant.domain.model;

import com.kbassistant.domain.exception.InvalidStatusTransitionException;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root for a user-uploaded document.
 *
 * Fields are private with no setters — logical immutability enforced by the class,
 * not the compiler. State transitions go exclusively through the domain methods below.
 *
 * Why not `final` fields? Java only allows final field assignment in constructors or
 * field initializers. The factory + private-constructor pattern requires post-construction
 * assignment, so `final` doesn't compile here. The invariant is preserved by private
 * access and the absence of any public setters.
 */
public class Document {

    private DocumentId id;
    private String name;
    private String originalFilename;
    private MimeType mimeType;
    private long fileSizeBytes;
    private String createdBy;
    private Instant createdAt;

    private DocumentStatus status;
    private String s3Key;
    private int chunkCount;
    private String errorMessage;
    private Instant updatedAt;

    private Document() {}

    // =========================================================================
    // Factory methods
    // =========================================================================

    public static Document create(String name,
                                  String originalFilename,
                                  MimeType mimeType,
                                  long fileSizeBytes,
                                  String createdBy) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(originalFilename, "originalFilename");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(createdBy, "createdBy");

        var doc = new Document();
        doc.id = DocumentId.generate();
        doc.name = name;
        doc.originalFilename = originalFilename;
        doc.mimeType = mimeType;
        doc.fileSizeBytes = fileSizeBytes;
        doc.createdBy = createdBy;
        doc.status = DocumentStatus.PENDING;
        doc.createdAt = Instant.now();
        doc.updatedAt = doc.createdAt;
        return doc;
    }

    /**
     * Rebuilds a Document from persisted state. Used only by the repository adapter.
     * Does not re-validate business rules — the data is already trusted from storage.
     */
    public static Document reconstitute(DocumentId id,
                                        String name,
                                        String originalFilename,
                                        MimeType mimeType,
                                        long fileSizeBytes,
                                        String createdBy,
                                        DocumentStatus status,
                                        String s3Key,
                                        int chunkCount,
                                        String errorMessage,
                                        Instant createdAt,
                                        Instant updatedAt) {
        var doc = new Document();
        doc.id = id;
        doc.name = name;
        doc.originalFilename = originalFilename;
        doc.mimeType = mimeType;
        doc.fileSizeBytes = fileSizeBytes;
        doc.createdBy = createdBy;
        doc.status = status;
        doc.s3Key = s3Key;
        doc.chunkCount = chunkCount;
        doc.errorMessage = errorMessage;
        doc.createdAt = createdAt;
        doc.updatedAt = updatedAt;
        return doc;
    }

    // =========================================================================
    // Domain behavior — all status transitions go through here
    // =========================================================================

    public void markStored(String s3Key) {
        transition(DocumentStatus.STORED);
        this.s3Key = Objects.requireNonNull(s3Key, "s3Key");
        this.updatedAt = Instant.now();
    }

    public void startProcessing() {
        transition(DocumentStatus.PROCESSING);
        this.updatedAt = Instant.now();
    }

    public void markReady(int chunkCount) {
        if (chunkCount < 0) throw new IllegalArgumentException("chunkCount must be >= 0");
        transition(DocumentStatus.READY);
        this.chunkCount = chunkCount;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        transition(DocumentStatus.FAILED);
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    private void transition(DocumentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(id, status, next);
        }
        this.status = next;
    }

    // =========================================================================
    // Accessors — no setters; mutation goes through domain methods above
    // =========================================================================

    public DocumentId id()             { return id; }
    public String name()               { return name; }
    public String originalFilename()   { return originalFilename; }
    public MimeType mimeType()         { return mimeType; }
    public long fileSizeBytes()        { return fileSizeBytes; }
    public String createdBy()          { return createdBy; }
    public DocumentStatus status()     { return status; }
    public String s3Key()              { return s3Key; }
    public int chunkCount()            { return chunkCount; }
    public String errorMessage()       { return errorMessage; }
    public Instant createdAt()         { return createdAt; }
    public Instant updatedAt()         { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
