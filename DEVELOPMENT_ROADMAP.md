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

## Phase 6 — Multi-Provider LLM + Prompt Engineering ✅ Complete

**Goal:** Add an Anthropic adapter and tighten the RAG system prompt's citation/refusal behavior.

**Why this matters:** Portfolio demonstration of the provider-agnostic design. Shows LlmPort abstraction delivers on its promise — adding a provider is a single adapter class with zero changes to QueryService.

**Scope note:** Gemini adapter, streaming responses, and token usage tracking were planned but deferred — see Architecture Decisions in `docs/PHASE_6_PLAN.md` for why (Vertex AI GCP setup overhead, streaming requiring a separate SseEmitter/WebFlux design decision, token tracking better scoped with Micrometer in the Observability phase).

### Deliverables
- `AnthropicLlmAdapter` — Claude claude-sonnet-4-6 via Spring AI
- `app.llm.provider=anthropic` selection in config (already unblocked — base `application.yml` never excluded `AnthropicAutoConfiguration`)
- Improved system prompt: fixed quotable refusal string, multi-source citation format `[1][3]`, no-speculate instruction separated from citation rules

### Coding Tasks
- [x] `AnthropicLlmAdapter` implementing `LlmPort` — `AnthropicChatModel.call(Prompt)`, same `getText()` pattern as Ollama/OpenAI adapters
- [x] Improved system prompt with structured multi-source citation format `[1][3]`
- [x] Unit test: `AnthropicLlmAdapterTest` — returns content, passes 2 messages, empty response

### Acceptance Criteria
- [x] `app.llm.provider=anthropic` calls Claude without code changes
- [x] `AnthropicLlmAdapter` lives in `infrastructure.llm`, ArchUnit passes
- [x] Full unit suite green: 83 tests, 0 failures

---

## Phase 7 — Conversation History ✅ Complete

**Goal:** Multi-turn chat sessions. Users can ask follow-up questions with prior context preserved.

**Why this matters:** `V4__chat.sql` shipped back in Phase 2 but had zero application code using it until now — this phase builds the domain/application/api/infrastructure layers on top of that pre-existing schema.

### Deliverables
- `ChatSession` entity, `ChatMessage` entity, `ChatRole` enum, `ChatTurn` value object
- `LlmPort` gains a history-aware 3-arg overload via a default method (2-arg stays, zero breaking change)
- `QueryService` gains a matching 3-arg `query()` overload; `ChatService` reuses it rather than duplicating RAG logic
- `POST /sessions` — create session
- `POST /sessions/{id}/messages` — send message with conversation context
- Session context injected into LLM prompt as structured messages (last N turns, configurable via `app.chat.history-window`, default 10)
- `GET /sessions/{id}/messages` — retrieve history with citations
- Citations persisted as JSONB via a plain `CitationRecord` (avoids custom Jackson handling for the `DocumentId` value object)

### Coding Tasks
- [x] `ChatSessionId`, `ChatMessageId`, `ChatRole`, `ChatTurn`, `ChatSession`, `ChatMessage` domain types
- [x] `ChatSessionRepository`, `ChatMessageRepository` ports + JPA adapters (lowercase role mapping to match the checksummed V4 migration)
- [x] `LlmPort` 3-arg `complete()`; Ollama/OpenAi/Anthropic adapters implement it
- [x] `QueryService` 3-arg `query()` overload; 2-arg delegates with `List.of()`
- [x] `ChatService` — session lifecycle, history windowing, message persistence
- [x] `ChatController` — 3 endpoints; `GlobalExceptionHandler` — `ChatSessionNotFoundException → 404`
- [x] `ChatSessionTest`, `ChatMessageTest`, `ChatServiceTest`, `ChatControllerTest`, `ChatPersistenceIT` (JSONB round-trip)
- [x] `QueryServiceTest` updated — unifying the two `query()` overloads meant existing 2-arg `LlmPort` mocks silently stopped matching

### Acceptance Criteria
- [x] First message in a session uses empty history; later messages include prior turns
- [x] History window caps at `app.chat.history-window`, oldest-first within the window
- [x] Unknown session ID returns 404 on send and history endpoints
- [x] `/queries` stateless endpoint unaffected
- [x] `mvn verify` green: 109 unit tests + 20 integration tests

### Known Limitations (deferred to Phase 10)

Two retrieval issues surfaced during manual testing that are not code bugs but informed design gaps requiring targeted work:

1. **Similarity threshold miscalibrated for `nomic-embed-text`** — The default threshold of 0.75 is too strict for Ollama's `nomic-embed-text` embedding model, which produces cosine similarity scores in a lower range than OpenAI's embeddings. Even queries with strong lexical overlap (e.g. "What is Paris known for?" vs "Paris is known for its art, culture, and cuisine.") can fall below the cutoff and return no results. Additionally, short test documents produce single-chunk embeddings that represent a blend of multiple facts, diluting scores further. The threshold should be tuned per embedding provider, with a lower default for nomic-embed-text (~0.5).

2. **Retrieval is not history-aware for multi-turn sessions** — `QueryService` embeds the raw follow-up question alone. History is only injected at the LLM *generation* step, not the *retrieval* step. Follow-up questions using pronouns or implicit references ("When was its most famous tower completed?") produce embeddings with weak semantic signal against the document chunks and regularly return empty results before the LLM is ever called. A "condense-question" step is needed: before embedding, either (a) prepend recent turns to the query text, or (b) use an LLM call to rewrite the follow-up into a fully self-contained question, then embed the rewritten form.

---

## Phase 8 — AWS Deployment ✅ Complete

**Goal:** Production-ready Terraform + ECS Fargate deployment.

### Deliverables
- Terraform modules: `vpc`, `security`, `ecr`, `rds`, `s3`, `secrets`, `iam`, `alb`, `ecs`
- Bootstrap module for Terraform remote state (S3 + DynamoDB)
- Production `Dockerfile` (multi-stage Maven → JRE Alpine, non-root user)
- GitHub Actions CD: OIDC auth → ECR push → ECS force-deploy (`.github/workflows/cd.yml`)
- Spring Cloud AWS Secrets Manager integration (`spring.config.import`)
- `application-prod.yml` — SM import, Swagger disabled
- LocalStack Secrets Manager seed script (`02-create-secrets.sh`)
- `infrastructure/README.md` with deploy guide, OIDC setup, cost breakdown, teardown

**Split implementation note:** Dockerfile + Spring Cloud AWS SM are locally testable against LocalStack. Terraform and CD pipeline are code-complete; apply when an AWS account is available (see `infrastructure/README.md`).

### Key Decisions (see `docs/PHASE_8_PLAN.md` and `docs/adr/ADR-004-secrets-management.md`)
- Spring Cloud AWS SM over ECS-native secrets injection — explicit startup failure if SM is unreachable
- `optional:` prefix in local profile for graceful fallback when LocalStack is down
- `MaxRAMPercentage=75.0` JVM flag — scales with ECS task memory resizes automatically
- `lifecycle { ignore_changes = [task_definition] }` — Terraform manages service config, CI/CD manages image
- Single NAT Gateway — acceptable for portfolio; add per-AZ for production multi-AZ
- HTTPS optional (`enable_https = false`) — enable when Route53 domain is available
- OIDC for GitHub Actions — no long-lived IAM keys stored in secrets

---

## Phase 9 — Observability ✅ Complete

**Goal:** Operational observability (metrics, dashboards, alarms) + answer quality observability (LLM-as-a-judge evaluation).

### Deliverables
- 6 custom Micrometer metrics across `QueryService`, `EmbeddingService`, `IngestionService`: timers, distribution summaries, counter
- 2 RAG evaluation metrics via `EvaluationService`: `rag.evaluation.faithfulness`, `rag.evaluation.answer_relevance`
- Auto-provisioned Grafana dashboard (8 panels) under `docker compose --profile monitoring`
- Structured JSON logging for prod profile (`logging.structured.format.console: ecs`)
- CloudWatch alarms Terraform module (ECS CPU, task count, ALB 5xx)

### Key Decisions (see `docs/PHASE_9_PLAN.md` and `docs/adr/ADR-005-rag-evaluation.md`)
- **Timer** for latency metrics; **DistributionSummary** for chunk counts and quality scores; **Counter** for ingestion totals — see plan for full rationale
- **LLM-as-a-judge over RAGAS**: single-prompt 0–1 scoring (no ground truth needed); full RAGAS decomposition deferred until eval dataset is available
- **Async + 20% sampling** on evaluation: zero user latency impact; cost bounded by `app.evaluation.sample-rate`
- **MeterRegistry in application layer only**: domain stays pure Java; ArchUnit passes
- **Spring Boot 3.4 built-in structured logging** (`ecs` format): no extra library needed

---

## Phase 10 — Production Hardening ✅ Complete

**Goal:** Security, resilience, and RAG quality improvements that make the system production-ready.

### Deliverables
- `LlmResponse` domain record — richer LLM return type; token usage now populates `ChatMessage` (carry-forward from Phase 7)
- Resilience4j `@CircuitBreaker` + `@Retry` on all LLM adapters — graceful degraded response on provider outage
- Resilience4j `@RateLimiter` on query + chat endpoints — 429 with `retryAfter` property
- API key authentication via Spring Security `ApiKeyAuthFilter` — dev-mode pass-through when `api-keys` empty
- `QuestionCondenser` — rewrites follow-up questions to standalone form before embedding (carry-forward from Phase 7)
- Provider-aware similarity threshold — `threshold-ollama: 0.5` for nomic-embed-text (carry-forward from Phase 7)
- `ChunkingStrategy` port + `SemanticChunker` — sentence embedding boundary detection, selected by `app.chunking.strategy`
- `docs/PHASE_10_RUNBOOK.md` — operational runbook (deploy, scaling, failures, backup/restore, secret rotation, teardown)

### Deferred
- Hybrid search (vector + BM25) — own phase
- Reranking (Cohere) — own phase
- JWT with external IdP — upgrade path documented in runbook; API keys are correct for this access pattern now

### Key Decisions (see `docs/PHASE_10_PLAN.md` and `docs/adr/ADR-006-authentication.md`)
- API keys over JWT: no UI, no user sessions, no IdP infrastructure needed; rotation via comma-separated list
- Circuit breaker on adapters (not a wrapper): Resilience4j AOP requires Spring bean method calls; adapters are the right boundary
- `LlmPort` breaking change now: better to do it while codebase is small; deferred cost grows with each new caller
- `SemanticChunker` in domain/service: depends only on `EmbeddingPort` (a domain interface); zero Spring annotations
