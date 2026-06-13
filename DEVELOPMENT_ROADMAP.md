# Development Roadmap

## Phase 1 — Project Bootstrap

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
- [ ] `pom.xml` with Spring Boot 3.x, Spring AI, Spring Data JPA, pgvector, Flyway, Testcontainers BOM
- [ ] Package skeleton: `api`, `application`, `domain`, `infrastructure`
- [ ] `application.yml` + `application-local.yml`
- [ ] `docker-compose.yml` (postgres:16 + pgvector)
- [ ] `V1__init.sql` Flyway baseline
- [ ] `HealthController` (delegates to actuator)
- [ ] First domain entity: `Document` aggregate root (plain Java, no JPA annotations)
- [ ] First port: `DocumentStorePort` interface
- [ ] `ApplicationConfig` — Spring configuration class, wire beans
- [ ] Smoke test: `@SpringBootTest` that starts context and hits `/health`

### Acceptance Criteria
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` starts without errors
- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] Flyway migrations run automatically
- [ ] Package structure passes ArchUnit test: domain has no imports from `infrastructure` or `api`

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

## Phase 2 — PostgreSQL + pgvector

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
- [ ] `V2__documents.sql` — documents table
- [ ] `V3__document_chunks.sql` — chunks table with `vector(1536)` column + HNSW index
- [ ] `V4__chat.sql` — sessions + messages tables
- [ ] `Document` JPA entity (infrastructure layer — separate from domain entity)
- [ ] `DocumentChunk` JPA entity with `float[]` mapped to pgvector
- [ ] `DocumentJpaRepository` extends `JpaRepository`
- [ ] `DocumentChunkJpaRepository` with native similarity search query
- [ ] `PgVectorAdapter` implements `VectorStorePort`
- [ ] `DocumentJpaAdapter` implements `DocumentRepository` port
- [ ] Testcontainers test: insert chunks with fake vectors, assert cosine similarity search returns correct order

### Acceptance Criteria
- [ ] Flyway runs all migrations cleanly on fresh DB
- [ ] `DocumentChunkJpaRepositoryTest` passes with real Postgres via Testcontainers
- [ ] EXPLAIN ANALYZE shows HNSW index being used for vector queries
- [ ] ArchUnit: JPA entities exist only in `infrastructure.persistence` package

### Claude Code Prompt
```
We are in Phase 2. Generate the Flyway SQL migration V3__document_chunks.sql for a PostgreSQL 
table that stores: id (UUID PK), document_id (UUID FK), chunk_index (int), content (text), 
embedding (vector(1536)), token_count (int), page_number (varchar), metadata (jsonb), 
created_at (timestamp). Add a HNSW index for cosine distance on the embedding column.
Explain the index choice vs IVFFlat and when you would switch.
```

---

## Phase 3 — Document Ingestion

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
- [ ] `DocumentController` — multipart upload, validation
- [ ] `DocumentRequest` DTO + `DocumentResponse` DTO
- [ ] `DocumentService` use case
- [ ] `S3DocumentAdapter` implements `DocumentStorePort`
- [ ] `TextExtractorAdapter` — Apache PDFBox + plain text passthrough
- [ ] `DocumentUploadedEvent` domain event
- [ ] `IngestionEventHandler` — `@EventListener` + `@Async`
- [ ] `DocumentStatus` enum with allowed transitions
- [ ] `docker-compose.yml` addition: LocalStack for S3 in local dev
- [ ] Integration test: upload PDF, verify status transitions

### Acceptance Criteria
- [ ] `POST /documents` returns 202 with document ID within 200ms
- [ ] Status transitions correctly tracked in DB
- [ ] Text extraction works for PDF, TXT, MD
- [ ] Failed ingestion updates status to FAILED with error message
- [ ] `GET /documents/{id}` reflects current status

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

## Phase 4 — Embedding Generation

**Goal:** Chunk text, generate embeddings via OpenAI, persist to pgvector.

**Why this matters:** Chunking strategy directly determines retrieval quality. The token budget for chunks + prompt must fit in the LLM's context window. Over-chunking wastes tokens; under-chunking loses semantic boundaries.

### Deliverables
- `EmbeddingPort` domain interface
- `OpenAiEmbeddingAdapter` implements `EmbeddingPort`
- `FixedSizeChunker` with configurable token window and overlap
- `ChunkingStrategy` port + implementation wiring
- Batch embedding calls (OpenAI allows up to 2048 inputs per call)
- Ingestion pipeline integration: after text extraction → chunk → embed → store
- Retry logic for OpenAI rate limits (exponential backoff)

### Coding Tasks
- [ ] `EmbeddingPort` interface: `List<float[]> embed(List<String> texts)`
- [ ] `OpenAiEmbeddingAdapter` using Spring AI `EmbeddingModel`
- [ ] `ChunkingStrategy` interface + `FixedSizeChunker` implementation
- [ ] `ChunkingConfig` — configurable chunk size and overlap
- [ ] Update `IngestionService` to chunk → batch embed → persist
- [ ] `EmbeddingRetryConfig` — Spring Retry with exponential backoff
- [ ] Unit test: `FixedSizeChunkerTest` — verify chunk count, overlap, edge cases
- [ ] Integration test: end-to-end ingestion with real embeddings via Testcontainers

### Acceptance Criteria
- [ ] 1000-word document produces expected number of chunks
- [ ] Embedding vectors are correctly persisted as `vector(1536)` in pgvector
- [ ] Batch size respects OpenAI API limits
- [ ] Transient API failures retry with backoff; permanent failures mark doc FAILED

### Claude Code Prompt
```
We are in Phase 4. Implement FixedSizeChunker in the domain layer. 
It should split text into chunks of configurable token count (default 512) with configurable 
overlap (default 64 tokens). Use a simple whitespace tokenizer for now (we'll swap to tiktoken 
in Phase 7). The implementation must not depend on any framework — pure Java. Include the 
ChunkingStrategy interface. Write unit tests for: empty input, text shorter than chunk size, 
exact multiple of chunk size, overlap boundary conditions.
```

---

## Phase 5 — Vector Search

**Goal:** Similarity search with filtering, scoring, and threshold pruning.

**Why this matters:** Vector search quality is the multiplier on everything downstream. Retrieving irrelevant chunks = bad answers regardless of LLM quality.

### Deliverables
- `VectorStorePort` with rich search interface (topK, threshold, document filter)
- `PgVectorAdapter` with native SQL similarity query
- Cosine distance with configurable similarity threshold
- Optional document ID filter (search within a specific document)
- Scored results with chunk metadata for citations
- Performance test: verify HNSW index reduces query time vs sequential scan

### Coding Tasks
- [ ] `SimilaritySearchRequest` value object: vector, topK, threshold, documentIds
- [ ] `ScoredChunk` value object: chunk content, score, document metadata
- [ ] `VectorStorePort.search(SimilaritySearchRequest)` → `List<ScoredChunk>`
- [ ] `PgVectorAdapter` with native query using `<=>` cosine operator
- [ ] `DocumentChunkJpaRepository.findSimilar()` native SQL method
- [ ] `@Query` with `CAST(:vector AS vector)` parameter binding
- [ ] Testcontainers test: insert known vectors, assert search order is correct

### Acceptance Criteria
- [ ] Cosine similarity search returns chunks in correct order
- [ ] Score threshold correctly filters low-relevance results
- [ ] Document filter restricts search to specified documents
- [ ] EXPLAIN ANALYZE confirms index usage

### Claude Code Prompt
```
We are in Phase 5. Write the native SQL query for PgVectorAdapter.search() using pgvector's 
cosine distance operator (<=>). The query must: filter by optional list of document_ids, 
apply a similarity threshold (1 - distance >= threshold), return top-K results ordered by 
distance ascending, include document metadata in the result set. Show me the Spring Data JPA 
repository method with @Query annotation and explain every part of the SQL.
```

---

## Phase 6 — LLM Integration

**Goal:** Provider abstraction + prompt engineering. Working LLM completions from all three providers.

**Why this matters:** Prompt design determines answer quality. System prompt must instruct the LLM to cite sources and refuse to answer from parametric knowledge. Provider abstraction must be transparent to the application layer.

### Deliverables
- `LlmPort` domain interface
- `OpenAiAdapter`, `AnthropicAdapter`, `GeminiAdapter` via Spring AI
- `@ConditionalOnProperty` provider selection
- System prompt with strict citation instructions
- Token usage tracking
- `LlmRequest`/`LlmResponse` value objects
- Streaming support stub (Phase 10 implementation)

### Coding Tasks
- [ ] `LlmPort.complete(LlmRequest)` → `LlmResponse`
- [ ] `LlmRequest`: systemPrompt, userPrompt, maxTokens, temperature
- [ ] `LlmResponse`: content, promptTokens, completionTokens, model
- [ ] `OpenAiChatAdapter` using Spring AI `ChatModel`
- [ ] `AnthropicChatAdapter` using Spring AI Anthropic client
- [ ] `GeminiChatAdapter` using Spring AI Google client
- [ ] `LlmConfig` — `@ConditionalOnProperty` wiring
- [ ] `PromptTemplates.properties` — externalized prompt templates
- [ ] Integration test: actual LLM call (tagged @SlowTest, skipped in CI without keys)

### Acceptance Criteria
- [ ] Changing `app.llm.provider` to any of the three values works without code changes
- [ ] All providers return citations in their response
- [ ] Token usage is captured in `LlmResponse`
- [ ] Invalid provider config fails fast at startup with clear error

### Claude Code Prompt
```
We are in Phase 6. Design the LlmPort interface and the PromptBuilder for the RAG use case.
The system prompt must: instruct the model to answer ONLY from the provided context, 
format citations as [SOURCE: documentName, chunk N], refuse to speculate beyond context, 
and respond with "I don't have enough information" when context is insufficient. 
Show me the interface, the PromptBuilder class, and the system prompt text. 
Explain the prompt engineering decisions.
```

---

## Phase 7 — RAG Orchestration

**Goal:** Full end-to-end RAG pipeline with citations, hybrid search, and conversation context.

### Deliverables
- `ChatService` orchestrates: embed → search → prompt → LLM → cite
- `Citation` value object with documentName, excerpt, score
- `QueryResult` value object: answer + citations + token usage
- `POST /chat` endpoint
- Chat history persistence
- Similarity threshold tuning
- Metadata filter support

### Claude Code Prompt
```
We are in Phase 7. Implement ChatService.query(ChatRequest) → QueryResult.
It must: 1) embed the question, 2) search pgvector for topK=10 chunks above threshold 0.75, 
3) if no chunks found return "insufficient information" without calling LLM, 
4) build prompt with context, 5) call LlmPort, 6) extract citations from retrieved chunks 
(not parsed from LLM output), 7) persist chat message with citations as JSON. 
Explain why citations come from the retrieval step, not LLM output parsing.
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
