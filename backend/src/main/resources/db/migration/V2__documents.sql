-- Documents table: tracks uploaded files and their ingestion status.
--
-- Status lifecycle: PENDING → STORED → PROCESSING → READY | FAILED
-- s3_key is null until the file is uploaded to S3 (STORED status)

CREATE TABLE documents (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(500) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    file_size_bytes   BIGINT       NOT NULL DEFAULT 0,
    created_by        VARCHAR(255) NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    s3_key            VARCHAR(1000),
    chunk_count       INT          NOT NULL DEFAULT 0,
    error_message     TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_status     ON documents(status);
CREATE INDEX idx_documents_created_by ON documents(created_by);
CREATE INDEX idx_documents_created_at ON documents(created_at DESC);
