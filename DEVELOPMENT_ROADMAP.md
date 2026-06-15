# Development Roadmap

## Phase 1 — Project Bootstrap ✅ Complete

**Goal:** A running Spring Boot application with correct hexagonal structure, Docker Compose for local Postgres, and a health endpoint.

**Why this matters:** The hexagonal skeleton is the hardest thing to get right. Once ports/adapters are established, every subsequent phase just adds new implementations. Getting this wrong means painful refactors later.

### Deliverables
- Maven project with all dependencies declared (Spring AI, pgvector, testcontainers)
- Hexagonal package structure enforced
- Docker Compose with PostgreSQL 16 + pgvector
- `GET /actuator/health` returns UP
- Flyway baseline migration runs on startup
- First architecture decision records (ADRs)

### Coding Tasks
- [x] `pom.xml` with Spring Boot 3.x, Spring AI, Spring Data JPA, pgvector, Flyway, Testcontainers BOM
- [x] Package skeleton: `api`, `application`, `domain`, `infrastructure`
- [x] `application.yml` + `application-local.yml`
- [x] `docker-compose.yml` (postgres:16 + pgvector)
- [x] `V1__init.sql` Flyway baseline
- [x] `HealthController` (delegates to actuator)
- [x] First domain entity: `Document` aggregate root (plain Java, no JPA annotations)
- [x] First port: `DocumentStorePort` interface
- [x] `ApplicationConfig` — Spring configuration class, wire beans
- [x] Smoke test: `@SpringBootTest` that starts context and hits `/health`

### Acceptance Criteria
- [x] `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` starts without errors
- [x] `GET /actuator/health` returns `{"status":"UP"}`
- [x] Flyway migrations run automatically
- [x] Package structure passes ArchUnit test: domain has no imports from `infrastructure` or `api`

### Claude Code Prompt
```
We are in Phase 1. Generate the complete pom.xml for a Java 21 Spring Boot 3.x Maven project 
called knowledge-assistant with these dependencies: spring-boot-starter-web, 
spring-boot-starter-data-jpa, spring-boot-starter-actuator, spring-ai-bom (latest), 
spring-ai-openai-spring-boot-starter, spring-ai-pgvector-store-spring-boot-starter, 
flyway-core, postgresql driver, testcontainers BOM, testcontainers postgresql, 
springdoc-openapi-starter-webmvc-ui, micrometer-registry-prometheus, lombok.
Explain every dependency and why it's included.
```

---

## Phase 2 — PostgreSQL + pgvector ✅ Complete

**Goal:** Full data layer — JPA entities, pgvector column, Flyway migrations for all tables, repository interfaces.

**Why this matters:** The vector column type, indexing strategy, and schema decisions made here cannot be easily changed after ingestion. This is the most critical schema design phase.

### Deliverables
- Flyway migrations for `documents`, `document_chunks`, `chat_sessions`, `chat_messages`
- JPA entities with proper mappings
- pgvector `vector` column type via `pgvector-hibernate` extension
- HNSW index on `document_chunks.embedding`
- Spring Data JPA repositories with custom JPQL/native queries for similarity search
- Testcontainers integration test proving similarity search works

### Coding Tasks
- [x] `V2__documents.sql` — documents table
- [x] `V3__document_chunks.sql` — chunks table with `vector(768)` column + HNSW index
- [x] `Document` JPA entity (infrastructure layer — separate from domain entity)
- [x] `DocumentChunk` JPA entity with `float[]` mapped to pgvector
- [x] `DocumentJpaRepository` extends `JpaRepository`
- [x] `DocumentChunkJpaRepository` with native similarity search query
- [x] `PgVectorAdapter` implements `VectorStorePort`
- [x] `DocumentJpaAdapter` implements `DocumentRepository` port
- [x] Testcontainers test: insert chunks with fake vectors, assert cosine similarity search returns correct order

### Acceptance Criteria
- [x] Flyway runs all migrations cleanly on fresh DB
- [x] `VectorSimilaritySearchIT` passes with real Postgres via Testcontainers
- [x] HNSW index created on `document_chunks.embedding`
- [x] ArchUnit: JPA entities exist only in `infrastructure.persistence` package

### Claude Code Prompt
```
We are in Phase 2. Generate the Flyway SQL migration V3__document_chunks.sql for a PostgreSQL 
table that stores: id (UUID PK), document_id (UUID FK), chunk_index (int), content (text), 
embedding (vector(1536)), token_count (int), page_number (varchar), metadata (jsonb), 
created_at (timestamp). Add a HNSW index for cosine distance on the embedding column.
Explain the index choice vs IVFFlat and when you would switch.
```

---

## Phase 3 — Document Ingestion ✅ Complete

**Goal:** End-to-end upload flow. File → S3 → text extraction → events. No embeddings yet.

**Why this matters:** Gets the async pattern right. The ingestion pipeline is the most operationally risky part — if it fails mid-way, you need status tracking and resumability.

### Deliverables
- `POST /documents` endpoint accepting multipart upload
- S3 upload via AWS SDK v2
- Document status state machine: `PENDING → STORED → PROCESSING → READY | FAILED`
- Text extraction: PDF (PDFBox), TXT, Markdown
- Spring `ApplicationEvent`-driven async processing
- Error handling with status updates on failure

### Coding Tasks
- [x] `DocumentController` — multipart upload, validation
- [x] `DocumentRequest` DTO + `DocumentResponse` DTO
- [x] `DocumentService` use case
- [x] `S3DocumentAdapter` implements `DocumentStorePort`
- [x] `TextExtractorAdapter` — Apache PDFBox + plain text passthrough
- [x] `DocumentUploadedEvent` domain event
- [x] `IngestionEventHandler` — `@TransactionalEventListener` + `REQUIRES_NEW` (Spring 6.2)
- [x] `DocumentStatus` enum with allowed transitions
- [x] LocalStack for S3 in local dev (`docker-compose.yml`)
- [x] Integration test: upload PDF, verify status transitions

### Acceptance Criteria
- [x] `POST /documents` returns 202 with document ID
- [x] Status transitions correctly tracked in DB (PENDING → STORED → PROCESSING → READY | FAILED)
- [x] Text extraction works for PDF, TXT, MD
- [x] Failed ingestion updates status to FAILED with error message
- [x] `GET /documents/{id}` reflects current status

### Claude Code Prompt
```
We are in Phase 3. Implement the DocumentService use case in the application layer. 
It should: validate the file type (PDF/TXT/MD only), call DocumentStorePort to save to S3, 
persist a Document entity with status PENDING, publish a DocumentUploadedEvent, return a 
DocumentResponse. Follow hexagonal architecture — DocumentService must not import any 
Spring framework classes except @Service and @Transactional. Show me the full class with 
all method signatures.
```

---

## Phase 4 — Chunking + Embeddings ✅ Complete

**Goal:** Chunk text, generate 768-dim embeddings via Ollama nomic-embed-text, persist to pgvector.

**Why this matters:** Chunking strategy directly determines retrieval quality. Embeddings must use the same model at ingest and query time — vector spaces are model-specific and are not interchangeable.

### Deliverables
- `EmbeddingPort` domain interface
- `OllamaEmbeddingAdapter` implements `EmbeddingPort` (nomic-embed-text, 768 dims)
- `OpenAiEmbeddingAdapter` implements `EmbeddingPort` (text-embedding-3-small configured to 768 dims — prod fallback)
- `FixedSizeChunker` with configurable word window and overlap
- `ChunkingStrategy` port + implementation wiring
- Ingestion pipeline integration: text extraction → chunk → embed → store
- `@ConditionalOnProperty(app.embedding.provider)` selects active adapter

### Coding Tasks
- [x] `EmbeddingPort` interface: `List<float[]> embed(List<String> texts)`
- [x] `OllamaEmbeddingAdapter` using Spring AI `OllamaEmbeddingModel`
- [x] `OpenAiEmbeddingAdapter` using Spring AI `OpenAiEmbeddingModel` (dimensions=768)
- [x] `ChunkingStrategy` interface + `FixedSizeChunker` implementation (pure Java, no framework)
- [x] Update `EmbeddingService` to chunk → embed → persist chunks
- [x] `PgVectorStoreAutoConfiguration` excluded (custom adapter; two EmbeddingModel beans cause ambiguity)
- [x] Unit test: `FixedSizeChunkerTest` — 11 cases: null, blank, shorter-than-chunk, exact, overlap, invalid args
- [x] Unit test: `EmbeddingServiceTest` — success, empty text, embed failure, doc-not-found
- [x] Integration test: `VectorSimilaritySearchIT` with real 768-dim vectors

### Acceptance Criteria
- [x] Document produces expected number of chunks
- [x] Embedding vectors correctly persisted as `vector(768)` in pgvector
- [x] `app.embedding.provider=ollama` uses nomic-embed-text; `openai` uses text-embedding-3-small
- [x] Embedding failure marks document FAILED with error message
- [x] `mvn verify` passes (78 tests, 0 failures)

### Claude Code Prompt
```
We are in Phase 4. Implement FixedSizeChunker in the domain layer.
It should split text into chunks of configurable word count (default 512) with configurable
overlap (default 64 words). The implementation must not depend on any framework — pure Java.
Include the ChunkingStrategy interface. Write unit tests for: empty input, text shorter than
chunk size, exact multiple of chunk size, overlap boundary conditions.
```

---

## Phase 5 — Query API (RAG Pipeline) ✅ Complete

**Goal:** Full retrieval-augmented generation loop: `POST /queries` → embed question → similarity search → context assembly → LLM call → cited answer.

**Why this matters:** This is the complete read path. Combines vector search, LLM integration, and RAG orchestration in a single phase because each component is meaningless without the others.

### Deliverables
- `LlmPort` domain interface (`String complete(systemPrompt, userPrompt)`)
- `OllamaLlmAdapter` — local llama3.2 via Ollama; `app.llm.provider=ollama`
- `OpenAiLlmAdapter` — gpt-4o in production; `app.llm.provider=openai`
- `QueryService` — embed → search → no-results guard → context assembly (12,000 char cap) → LLM → source citations
- `POST /queries` endpoint with optional document ID filter
- No-results guard: skips LLM entirely when search returns empty (anti-hallucination)
- `SourceChunk` / `QueryResult` domain value objects

### Coding Tasks
- [x] `LlmPort` — `String complete(String systemPrompt, String userPrompt)`
- [x] `SourceChunk` record: documentId, documentName, contentSnippet (≤200 chars), score
- [x] `QueryResult` record: answer, List\<SourceChunk\>, durationMs
- [x] `QueryService` — full pipeline with context budget cap and no-results short-circuit
- [x] `OllamaLlmAdapter` — `OllamaChatModel.call(Prompt)`, `getText()` (not `getContent()` — M6 API change)
- [x] `OpenAiLlmAdapter` — same interface, `OpenAiChatModel`
- [x] `QueryController` — `POST /queries`, string documentIds → `DocumentId` domain objects
- [x] `GlobalExceptionHandler` additions: `MethodArgumentNotValidException→400`, `IllegalArgumentException→400`
- [x] Unit test: `QueryServiceTest` — happy path, no-results, doc filter, snippet truncation, budget cap
- [x] `@WebMvcTest`: `QueryControllerTest` — 200, 400 on blank question, 400 on invalid UUID
- [x] Unit test: `OllamaLlmAdapterTest` — real `ChatResponse`/`Generation` objects, `doReturn+(Prompt)` cast for overload ambiguity

### Acceptance Criteria
- [x] Upload a document → READY → `POST /queries` returns a grounded answer
- [x] Response includes `sources[]` with documentName, contentSnippet, score
- [x] Blank question returns 400; invalid UUID in documentIds returns 400
- [x] Empty similarity search returns "No relevant documents found" without calling LLM
- [x] `mvn verify` green (94 tests, 0 failures)
- [x] ArchUnit: `QueryService` imports nothing from `api` or `infrastructure`

### Claude Code Prompt
```
We are in Phase 5. Implement QueryService.query(String question, List<DocumentId> filter).
It must: 1) embed the question via EmbeddingPort, 2) call VectorStorePort.similaritySearch(),
3) if results are empty return a fixed "No relevant documents found" message without calling LLM,
4) cap context at 12,000 chars (chunks sorted by score desc), 5) call LlmPort.complete(),
6) return QueryResult with answer + source citations (document name + snippet ≤200 chars).
Explain why citations come from the retrieval step not LLM output parsing.
```

---

## Phase 6 — Multi-Provider LLM + Prompt Engineering

**Goal:** Add Anthropic and Gemini adapters; advanced prompt templates; streaming support.

**Why this matters:** Portfolio demonstration of the provider-agnostic design. Shows LlmPort abstraction delivers on its promise — adding a provider is a single adapter class with zero changes to QueryService.

### Deliverables
- `AnthropicLlmAdapter` — Claude claude-sonnet-4-6 via Spring AI
- `GeminiLlmAdapter` — Gemini 1.5 Pro via Spring AI
- `app.llm.provider=anthropic|gemini` selection in config
- Advanced system prompt: structured citation format, refuse-to-speculate instruction
- Token usage tracking in `QueryResult`
- Streaming response support (`Flux<String>` variant of `LlmPort`)

### Coding Tasks
- [ ] `AnthropicLlmAdapter` implementing `LlmPort`
- [ ] `GeminiLlmAdapter` implementing `LlmPort`
- [ ] `application-prod.yml` with `app.llm.provider=openai` (default prod)
- [ ] Improved system prompt with structured citation format `[1]`
- [ ] `StreamingLlmPort` — `Flux<String> stream(String systemPrompt, String userPrompt)`
- [ ] `GET /queries/stream` SSE endpoint using `StreamingLlmPort`
- [ ] Integration test: actual LLM call (`@Tag("slow")`, skipped in CI without keys)

### Acceptance Criteria
- [ ] `app.llm.provider=anthropic` calls Claude without code changes
- [ ] `app.llm.provider=gemini` calls Gemini without code changes
- [ ] `GET /queries/stream` streams tokens as SSE events
- [ ] Invalid provider config fails fast at startup with clear error

---

## Phase 7 — Conversation History

**Goal:** Multi-turn chat sessions. Users can ask follow-up questions with prior context preserved.

### Deliverables
- `ChatSession` aggregate root, `ChatMessage` entity
- `V4__chat.sql` Flyway migration — sessions + messages tables
- `POST /sessions` — create session
- `POST /sessions/{id}/messages` — send message with conversation context
- Session context injected into LLM prompt (last N turns, configurable window)
- `GET /sessions/{id}/messages` — retrieve history

### Claude Code Prompt
```
We are in Phase 7. Implement ChatSession and ChatMessage domain models.
A session contains an ordered list of messages (role: USER | ASSISTANT), each with content and
citations. When querying, inject the last 5 messages as conversation history in the LLM prompt.
Explain the tradeoff between conversation context window size and LLM cost.
```

---

## Phase 8 — AWS Deployment

**Goal:** Production-ready Terraform + ECS Fargate deployment.

### Deliverables
- Terraform modules: `security`, `rds`, `ecs`, `s3`, `iam`, `monitoring`
- Production `Dockerfile` (multi-stage, non-root, minimal image)
- GitHub Actions: build → test → push ECR → deploy ECS
- Secrets Manager integration via Spring Cloud AWS
- RDS PostgreSQL with pgvector extension
- ALB with HTTPS termination

### Claude Code Prompt
```
We are in Phase 8. Generate the Terraform module for ECS Fargate that deploys the 
knowledge-assistant container. Requirements: private subnet, task role with S3 + Secrets 
Manager permissions, environment variables from Secrets Manager (not plaintext), 
health check against /actuator/health, autoscaling 1-4 tasks based on CPU > 70%, 
CloudWatch log group. Explain the IAM least-privilege design.
```

---

## Phase 9 — Observability

**Goal:** Full production observability — metrics, dashboards, structured logging, distributed tracing.

### Deliverables
- Micrometer custom metrics: ingestion duration, embedding latency, LLM latency, similarity scores
- Prometheus scrape endpoint
- Grafana dashboard JSON
- Structured logging with trace IDs
- CloudWatch alarms for error rates and latency

### Claude Code Prompt
```
We are in Phase 9. Add Micrometer instrumentation to ChatService and IngestionService. 
Metrics to capture: chat.query.duration (timer, tag: provider), 
embedding.generation.duration (timer, tag: batch_size), 
similarity.search.results (distribution summary, tag: above/below threshold), 
ingestion.pipeline.duration (timer, tag: status success/failure). 
Show me the instrumentation pattern and explain gauge vs counter vs timer choice for each.
```

---

## Phase 10 — Production Hardening

**Goal:** Security, resilience, performance optimization, and operational runbook.

### Deliverables
- JWT authentication + API key support
- Rate limiting (token bucket per API key)
- Circuit breaker for LLM providers (Resilience4j)
- Semantic chunking implementation
- Hybrid search (vector + full-text)
- Reranking integration (Cohere)
- Connection pool tuning
- Production runbook

### Claude Code Prompt
```
We are in Phase 10. Implement Resilience4j circuit breaker for LlmPort. 
Requirements: open after 5 failures in 10s, half-open after 30s, 
fallback returns a degraded response explaining the LLM is temporarily unavailable 
(do not propagate the error to the user). Show the configuration and explain 
circuit breaker state transitions and why we need this for an external LLM API dependency.
```
