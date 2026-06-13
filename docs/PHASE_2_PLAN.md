# Phase 2 — PostgreSQL + pgvector: Implementation Plan

## What We Built

Full persistence layer: schema migrations, JPA entities, custom Hibernate type for pgvector,
repository interfaces, port adapters, and Testcontainers integration tests proving that
cosine similarity search returns results in correct order.

## Why This Phase Matters

The schema decisions made here are the hardest to reverse in the entire project.
Changing the vector column dimension (1536) after ingestion requires a full re-embed
of every document. Changing the index type requires rebuilding the index, which takes
minutes to hours at production scale. Getting these right in Phase 2 prevents a painful
data migration later.

---

## Architecture Decisions

### Our own `document_chunks` table vs Spring AI's `vector_store`

Spring AI's `PgVectorStore` creates and manages its own `vector_store` table with a fixed
schema. We chose our own `document_chunks` table because:
- We need rich metadata columns (chunk_index, page_number, token_count, document FK)
- We need control over the index strategy and query plan
- Citations require tracing chunks back to their source document — the FK and chunk_index
  make this clean; the metadata JSONB blob approach in `vector_store` does not

The tradeoff: we implement our own `VectorStorePort` rather than delegating to Spring AI's
built-in `VectorStore`. More code, more control.

### HNSW index over IVFFlat

| | IVFFlat | HNSW |
|---|---|---|
| Build | Fast, but requires training (clustering) | Slower, incremental |
| Recall at small scale | Poor (bad cluster quality < 10k vectors) | Good from first insert |
| Memory | Lower | Higher (~2× IVFFlat) |
| Query tuning | `ivfflat.probes` | `hnsw.ef_search` |

IVFFlat needs a minimum number of vectors before it outperforms sequential scan.
HNSW is the right default for a new project where data grows incrementally.

### JPA for CRUD, JdbcClient for similarity search

JPA/JPQL has no expression for `embedding <=> CAST(:v AS vector)`. Native queries in
Spring Data JPA work but offer no advantage over JdbcClient and are harder to read.
JdbcClient (Spring 6.1+) gives us named parameter binding, a clean row-mapper API,
and full SQL visibility — right for high-frequency search queries.

### `saveAllAndFlush()` over `saveAll()`

JPA auto-flushes before *JPA* queries (FlushMode.AUTO). It has no knowledge of JdbcClient
calls. Without an explicit flush, chunks saved via JPA are in the first-level cache but
not in the database tables when the JDBC similarity search runs in the same transaction.
`saveAllAndFlush()` makes the adapter predictable regardless of transaction context.

### `ddl-auto: none`

Flyway owns the schema. Hibernate's `validate` mode fails on custom column types like
`vector` because PostgreSQL's vector type is unknown to Hibernate's dialect. `none` is
correct when Flyway is the schema authority.

---

## Files Produced

### Flyway Migrations
| File | Purpose |
|---|---|
| `V2__documents.sql` | `documents` table with status indexes |
| `V3__document_chunks.sql` | `document_chunks` with `vector(1536)`, HNSW index |
| `V4__chat.sql` | `chat_sessions` + `chat_messages` for Phase 7 |

### Domain Additions
| File | Type | Notes |
|---|---|---|
| `ChunkId.java` | Value Object | UUID wrapper, same pattern as DocumentId |
| `DocumentChunk.java` | Entity | Aggregate in-context for chunk data |
| `ScoredChunk.java` | Value Object (record) | Chunk + similarity score [0.0, 1.0] |
| `SimilaritySearchRequest.java` | Value Object | Builder pattern, optional document filter |
| `VectorStorePort.java` | Port (interface) | saveChunks, similaritySearch, delete, count |

### Infrastructure
| File | Type | Notes |
|---|---|---|
| `PgVectorType.java` | Hibernate UserType | float[] ↔ vector(1536) |
| `DocumentEntity.java` | JPA Entity | Maps `documents` table |
| `DocumentChunkEntity.java` | JPA Entity | Maps `document_chunks`, uses PgVectorType |
| `DocumentJpaRepository.java` | Spring Data | findByStatus for Phase 3 pipeline |
| `DocumentChunkJpaRepository.java` | Spring Data | findByDocumentId, delete, count |
| `DocumentJpaAdapter.java` | Adapter OUT | Implements DocumentRepository port |
| `PgVectorAdapter.java` | Adapter OUT | Implements VectorStorePort port |

### Tests
| File | Type | Validates |
|---|---|---|
| `DocumentRepositoryIT.java` | Integration | Full CRUD + status transitions persist |
| `VectorSimilaritySearchIT.java` | Integration | Similarity order, threshold, document filter |

---

## Acceptance Criteria

- [x] Flyway applies V1–V4 cleanly on fresh database
- [x] `DocumentRepositoryIT` passes: CRUD + status transitions
- [x] `VectorSimilaritySearchIT` passes: similarity order, threshold filtering, document filter
- [x] `mvn verify` green
- [x] ArchUnit rules still pass (domain classes import nothing from infrastructure)
- [x] App starts with `mvn spring-boot:run -Dspring-boot.run.profiles=local`

---

## Git Commit Message

```
feat: Phase 2 — PostgreSQL schema, pgvector, JPA data layer

Flyway migrations:
- V2: documents table with status indexes
- V3: document_chunks with vector(1536) column + HNSW index (m=16, ef_construction=64)
- V4: chat_sessions + chat_messages (used in Phase 7)

Domain additions:
- DocumentChunk entity, ChunkId value object
- ScoredChunk (record), SimilaritySearchRequest (builder VO)
- VectorStorePort: saveChunks, similaritySearch, deleteByDocumentId, count

Infrastructure:
- PgVectorType: Hibernate 6 UserType mapping float[] <-> PostgreSQL vector(1536)
- DocumentEntity + DocumentChunkEntity JPA entities
- DocumentJpaAdapter implements DocumentRepository port
- PgVectorAdapter implements VectorStorePort via JPA (save) + JdbcClient (search)

Key decisions:
- HNSW over IVFFlat: better recall from first insert, no training phase needed
- ddl-auto=none: Flyway owns the schema, Hibernate just uses it
- saveAllAndFlush(): required when JPA and JdbcClient share a transaction context
- Embedding not returned in search results: avoids 6KB/row overhead on chat queries

Tests: DocumentRepositoryIT + VectorSimilaritySearchIT via Testcontainers pgvector:pg16

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

## PR Description

```markdown
## Phase 2: PostgreSQL + pgvector Data Layer

### What
Full persistence layer for document storage and vector similarity search.

### Key Decisions

**Custom `document_chunks` table over Spring AI's `vector_store`**
We need per-chunk metadata (document FK, chunk index, page number, token count)
for the citation system in Phase 7. Spring AI's fixed `vector_store` schema
doesn't support this cleanly.

**HNSW index** (`m=16, ef_construction=64`) chosen over IVFFlat because IVFFlat
needs a minimum corpus size before its cluster-based recall beats a sequential scan.
HNSW works well from the first insert.

**`PgVectorType` custom Hibernate UserType**: Hibernate 6's dialect doesn't know
the PostgreSQL `vector` type. The custom `UserType` maps `float[]` on write using
`PGvector` (a `PGobject` the JDBC driver understands) and parses the bracket-notation
string on read.

**`saveAllAndFlush()` in `PgVectorAdapter`**: JPA auto-flush is JPA-query-aware only.
`JdbcClient` queries are invisible to Hibernate's flush decision, so without an explicit
flush the chunks aren't in the database tables when the similarity search runs.

### Test Approach
`VectorSimilaritySearchIT` uses standard basis vectors (1.0 in one dimension,
0.0 elsewhere). Cosine similarity between orthogonal unit vectors is exactly 0.0;
between identical vectors exactly 1.0. This gives deterministic assertions without
real API calls.

### Not Included
- No embedding generation (Phase 4)
- No ingestion pipeline (Phase 3)
- Document filter in similarity search deferred to Phase 5 full implementation
```
