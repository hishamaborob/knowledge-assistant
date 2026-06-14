# Phase 4 — Chunking + Embedding (Ollama nomic-embed-text)

## Goal

Complete the ingestion pipeline: `TextExtractedEvent` → chunk text → generate embeddings
via Ollama → persist vectors to pgvector → mark document READY.

## What Phase 4 Delivers

- `FixedSizeChunker`: pure Java, configurable token window + overlap
- `EmbeddingPort`: domain interface for embedding generation
- `OllamaEmbeddingAdapter`: calls Ollama's nomic-embed-text model (768 dims, runs locally)
- `EmbeddingService`: handles `TextExtractedEvent` — chunk → batch embed → save chunks → READY
- Flyway `V5` migration: changes `vector(1536)` → `vector(768)` to match nomic-embed-text output
- OpenAI embedding adapter wired in parallel via `@ConditionalOnProperty` for production use
- Updated `VectorSimilaritySearchIT`: basis vectors changed from 1536-dim to 768-dim

---

## Architecture Decisions

### Why 768 dimensions (not 1536)

`nomic-embed-text` outputs 768-dimensional vectors. The dimension is a property of the
model, not something we configure. The schema must match the model output.

For production with OpenAI, `text-embedding-3-small` and `text-embedding-3-large` both
support a `dimensions` parameter that truncates the output. Setting `dimensions=768`
on OpenAI means both local and production use the same schema without migration.

| Model | Native dims | Configured dims |
|---|---|---|
| nomic-embed-text (Ollama) | 768 | 768 (fixed) |
| text-embedding-3-small (OpenAI) | 1536 | 768 (via dimensions param) |
| text-embedding-3-large (OpenAI) | 3072 | 768 (via dimensions param) |

768 is the practical standard that works for both providers without schema divergence.

### Why Ollama for local, OpenAI for production

| | Ollama nomic-embed-text | OpenAI text-embedding-3-small |
|---|---|---|
| Cost | Free | $0.02 / 1M tokens |
| Latency | ~50ms on CPU | ~200ms network |
| Setup | `ollama pull nomic-embed-text` | API key |
| Quality | Good for general text | Slightly better for English |
| Offline | Yes | No |

For a portfolio project processed locally during development, Ollama is strictly better.
The `@ConditionalOnProperty(app.embedding.provider)` switch means zero code changes
to go to production — just set an env var and an API key.

### Why `FixedSizeChunker` in the domain layer

Chunking is a domain concern — it determines the granularity of knowledge retrieval.
The splitting logic is pure Java (no framework). It belongs in the domain layer so it
can be unit-tested without Spring context, and swapped (e.g. for semantic chunking in
Phase 10) without touching infrastructure.

### Token counting: whitespace approximation

True token counting requires a tokenizer (tiktoken for OpenAI, sentencepiece for others).
Both are JVM-unfriendly in Phase 4. We use whitespace word count as an approximation
(`text.split("\\s+").length`). 1 token ≈ 0.75 words for English text, so a 512-token
chunk is approximately 384 words.

Phase 10 can swap in a real tokenizer if token budget accuracy matters.

### Batch size: 20 embeddings per call

Ollama processes embeddings sequentially. The batch size is ignored by the adapter
(each call is single-text), but the service batches calls in groups of 20 to limit
memory pressure when chunking large documents.

### `EmbeddingService` vs extending `IngestionService`

Phase 3's `IngestionService` handles `DocumentUploadedEvent`. Phase 4 adds a handler
for `TextExtractedEvent`. Rather than putting both in one class (violating SRP and
making the class hard to test), `EmbeddingService` is a new focused service.

`IngestionService` publishes → `EmbeddingService` consumes.

---

## Ollama Setup (one-time, local only)

```bash
# Install Ollama (macOS)
brew install ollama

# Pull the embedding model (~274MB)
ollama pull nomic-embed-text

# Verify it works
ollama run nomic-embed-text "test"
```

Ollama runs as a daemon at `http://localhost:11434`. No docker-compose entry needed —
it runs natively and persists between reboots via a launchd service on macOS.

---

## Files to Create / Modify

### New dependency (pom.xml)
- `spring-ai-ollama-spring-boot-starter` version `${spring-ai.version}`

### New Flyway migration
| File | Change |
|---|---|
| `V5__change_embedding_dimension.sql` | Drop HNSW index → ALTER vector column 1536→768 → Recreate HNSW index |

### New domain
| File | Notes |
|---|---|
| `domain/port/out/EmbeddingPort.java` | `List<float[]> embed(List<String> texts)` |
| `domain/service/FixedSizeChunker.java` | Pure Java; configurable chunkSize + overlap in words |

### New application
| File | Notes |
|---|---|
| `application/service/EmbeddingService.java` | `@TransactionalEventListener(AFTER_COMMIT)` on `TextExtractedEvent`; chunk → embed → save → READY |

### New infrastructure
| File | Notes |
|---|---|
| `infrastructure/embedding/OllamaEmbeddingAdapter.java` | Calls Spring AI `EmbeddingModel`; active when `app.embedding.provider=ollama` |
| `infrastructure/embedding/OpenAiEmbeddingAdapter.java` | Calls Spring AI `EmbeddingModel`; active when `app.embedding.provider=openai` |

### Config changes
| File | Change |
|---|---|
| `application.yml` | Add `app.embedding.provider`, `app.embedding.dimensions=768`; update `spring.ai.vectorstore.pgvector.dimensions=768` |
| `application-local.yml` | Add `app.embedding.provider=ollama`, `spring.ai.ollama.base-url`, `spring.ai.ollama.embedding.model=nomic-embed-text` |

### Modified files
| File | Change |
|---|---|
| `VectorSimilaritySearchIT.java` | Change `new float[1536]` → `new float[768]` throughout |

### New tests
| File | Type | What it proves |
|---|---|---|
| `domain/service/FixedSizeChunkerTest.java` | Unit | Empty input, shorter than chunk, exact multiple, overlap boundaries |
| `application/service/EmbeddingServiceTest.java` | Unit (Mockito) | Chunks text, calls embed, saves chunks, marks READY; marks FAILED on embed error |
| `infrastructure/embedding/OllamaEmbeddingAdapterTest.java` | Unit (mocked Spring AI) | Delegates to EmbeddingModel, returns correct float[] |

---

## Sequence: Embedding Flow

```
[async — triggered by TextExtractedEvent after IngestionService commits]

EmbeddingService.handleTextExtracted(TextExtractedEvent)   REQUIRES_NEW transaction
  ├─ FixedSizeChunker.chunk(extractedText)
  │     → List<String> chunks (e.g. 10 chunks from a 5-page PDF)
  │
  ├─ EmbeddingPort.embed(chunks)                           → OllamaEmbeddingAdapter
  │     → calls Ollama nomic-embed-text
  │     → returns List<float[]> (each float[768])
  │
  ├─ Build List<DocumentChunk> (chunk content + embedding + metadata)
  ├─ VectorStorePort.saveChunks(chunks)                    → PgVectorAdapter → pgvector
  │
  ├─ document.markReady(chunks.size())
  ├─ DocumentRepository.save(document)                     → DB: status = READY
  │
  └─ [catch] document.markFailed(message)                  → DB: status = FAILED
```

---

## Chunking Parameters (configurable)

| Parameter | Default | Notes |
|---|---|---|
| `app.chunking.chunk-size` | 512 | Target words per chunk (already in application.yml) |
| `app.chunking.overlap` | 64 | Words repeated between adjacent chunks (already in application.yml) |

A 1000-word document with chunk-size=512, overlap=64 produces 2 chunks:
- Chunk 0: words 0–511
- Chunk 1: words 448–959 (starts at 512-64=448)

---

## Acceptance Criteria

- [ ] Upload a PDF → after ingestion, `GET /documents/{id}` returns `status: READY`, `chunkCount > 0`
- [ ] `document_chunks` table contains rows with non-null `embedding` column (768 dims)
- [ ] `FixedSizeChunkerTest` covers: empty string, text < chunk size, overlap correctness
- [ ] `EmbeddingServiceTest` covers: success path + FAILED on embed error
- [ ] `VectorSimilaritySearchIT` still passes (updated to 768 dims)
- [ ] `mvn verify` green

---

## Git Commit Message

```
feat: Phase 4 — chunking, Ollama embeddings, vector persistence

Schema:
- V5 migration: vector column 1536→768 (nomic-embed-text native output)
- HNSW index dropped and recreated at 768 dims
- app.embedding.dimensions=768 in config (OpenAI 3-small/3-large support
  dimensions param so same schema works in production)

Domain:
- EmbeddingPort: List<float[]> embed(List<String> texts)
- FixedSizeChunker: pure Java, word-count approximation, configurable size+overlap

Application:
- EmbeddingService: TextExtractedEvent → chunk → embed → saveChunks → READY
  Uses @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)

Infrastructure:
- OllamaEmbeddingAdapter: Spring AI OllamaEmbeddingModel, active on provider=ollama
- OpenAiEmbeddingAdapter: Spring AI OpenAiEmbeddingModel with dimensions=768, active on provider=openai

Tests:
- FixedSizeChunkerTest: empty, short, overlap boundary cases
- EmbeddingServiceTest: success + failure paths (Mockito)
- OllamaEmbeddingAdapterTest: delegates to Spring AI EmbeddingModel
- VectorSimilaritySearchIT: updated to 768-dim basis vectors

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```
