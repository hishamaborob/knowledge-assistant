-- Change embedding column from vector(1536) to vector(768).
--
-- Why 768: nomic-embed-text (Ollama) natively outputs 768 dimensions.
-- OpenAI text-embedding-3-small/large both accept a `dimensions` parameter
-- and can be configured to output 768 dims — so the same schema works for
-- both local (Ollama) and production (OpenAI) without a further migration.
--
-- Approach: drop + re-add the column rather than ALTER TYPE.
-- pgvector does not register an implicit cast between vector dimensions, so
-- ALTER COLUMN ... TYPE vector(768) would require a USING clause that
-- pgvector does not provide. Drop/add is always safe here because no
-- documents have been embedded yet (Phase 4 is the first embedding phase).

-- 1. Drop the dimension-specific HNSW index (can't exist without the column)
DROP INDEX IF EXISTS idx_chunks_embedding;

-- 2. Drop and recreate the embedding column at the new dimension
ALTER TABLE document_chunks DROP COLUMN IF EXISTS embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(768);

-- 3. Recreate the HNSW index at 768 dimensions
--    Parameters unchanged — m=16, ef_construction=64 are dimension-independent.
CREATE INDEX idx_chunks_embedding
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
