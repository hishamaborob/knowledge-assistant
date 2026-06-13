-- Document chunks: stores text segments with their vector embeddings.
--
-- The embedding column uses pgvector's vector(1536) type — matches
-- OpenAI text-embedding-3-small output dimensions.
--
-- CRITICAL: embedding model is immutable after first ingestion.
-- Changing the model requires a full rebuild via POST /embeddings/rebuild
-- because vectors from different models are NOT comparable.

CREATE TABLE document_chunks (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index   INT         NOT NULL,
    content       TEXT        NOT NULL,
    embedding     vector(1536),
    token_count   INT         NOT NULL DEFAULT 0,
    page_number   VARCHAR(50),
    metadata      JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (document_id, chunk_index)
);

-- Supports fast lookup of all chunks for a given document
CREATE INDEX idx_chunks_document_id ON document_chunks(document_id);

-- HNSW index for approximate nearest neighbour (ANN) search.
--
-- Why HNSW over IVFFlat:
--   IVFFlat requires a training phase (clustering) before it's effective.
--   At small scale (< 10k vectors) the cluster quality is poor, giving
--   worse recall than a sequential scan. HNSW builds incrementally and
--   provides good recall from the first insert.
--
-- Tuning:
--   m=16              — graph connectivity. Higher = better recall, more memory.
--                       Default is 16; 32 improves recall at ~2× memory cost.
--   ef_construction=64 — search width during index build. Higher = better
--                        quality index, slower inserts. 64 is a good default.
--
-- Query-time tuning (Phase 9):
--   SET hnsw.ef_search = 100; -- wider search for higher recall at query time

CREATE INDEX idx_chunks_embedding
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
