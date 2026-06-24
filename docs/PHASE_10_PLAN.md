# Phase 10 — Production Hardening

## Goal

Security, resilience, and RAG quality improvements that make the system production-ready. Eight concerns addressed: LLM resilience, rate limiting, API key auth, history-aware retrieval, provider-aware similarity thresholds, richer LLM response (token usage), and semantic chunking.

---

## What Phase 10 Delivers

### 1. `LlmResponse` — Richer Return Type for `LlmPort`

**The carry-forward:** `LlmPort` returned a plain `String` since Phase 1. `ChatMessage.promptTokens`, `completionTokens`, and `modelUsed` have existed in the schema since Phase 2 but were always `null`.

New domain record:
```java
public record LlmResponse(String content, int promptTokens, int completionTokens, String modelUsed) {}
```

`LlmPort.complete()` now returns `LlmResponse`. All three adapters (OpenAI, Anthropic, Ollama) extract usage from Spring AI's `ChatResponse.getMetadata().getUsage()`. Token counts flow into:
- `QueryResult` (new fields: `promptTokens`, `completionTokens`, `modelUsed`)
- `ChatMessage` via the 7-arg `create()` overload — ASSISTANT messages in `ChatService` now have token data populated
- Future Micrometer token counters (wiring point ready)

This is the biggest structural change in Phase 10 — a breaking interface change that touched all 3 adapters, `QueryService`, `EvaluationService`, `ChatService`, `QueryResult`, `ChatMessage`, and their tests.

### 2. Resilience4j Circuit Breaker + Retry on All LLM Adapters

Applied via `@CircuitBreaker(name = "llm")` + `@Retry(name = "llm")` on each adapter's `complete()` method.

**Circuit breaker config:**
- Sliding window: 10 seconds
- Opens when: 50% failure rate over minimum 5 calls
- Half-open after: 30s — probes with 3 calls
- Auto-transition back to half-open: enabled

**Retry config:**
- 3 attempts with exponential backoff (2s, 4s)
- Retries only on: `ResourceAccessException`, `ConnectException`, `IOException` (transient network issues)
- Does NOT retry on: 4xx, `CallNotPermittedException` (circuit already open — no point)

**Fallback:** Returns a graceful degraded `LlmResponse` with content `"The AI service is temporarily unavailable. Please try again in a moment."` — never propagates the error to the user as a 500.

**Why on the adapter (not a wrapper):** Resilience4j's Spring AOP proxy intercepts method calls on Spring beans. The adapters are `@Component`s; wrapping them is the idiomatic approach. Putting resilience logic in a domain port wrapper would require the domain to know about Resilience4j.

**Annotation ordering:** Resilience4j Spring Boot applies aspects in priority order — CircuitBreaker (1) wraps Retry (4). The circuit breaker sees the final result after all retries, which is the correct semantic: retry transient failures first, then circuit opens on persistent failures.

### 3. Rate Limiting on Query + Chat Endpoints

`@RateLimiter(name = "queryApi")` on `QueryController.query()` and `@RateLimiter(name = "chatApi")` on `ChatController.sendMessage()`.

Config: 20 requests per 10-second window, `timeout-duration: 0s` (reject immediately, never queue).

`RequestNotPermittedException` propagates to `GlobalExceptionHandler` which returns `429 Too Many Requests` with a `retryAfter: 10` property. No fallback method on the controller — the exception handler is the right place for HTTP status mapping.

This is global-per-endpoint rate limiting. Per-user/per-key rate limiting is a natural next step once API key identity is threaded through the request context.

### 4. API Key Authentication (Spring Security)

**Why API keys, not JWT:** JWT requires a token issuance endpoint or external IdP (Keycloak, Auth0). For a RAG backend API with no UI, that means a `/auth` endpoint just to get a token to call the real API — over-engineering. API keys are pre-issued, stored in Secrets Manager, and passed as `X-API-Key: <key>` header. JWT is the documented upgrade path in the runbook.

**`ApiKeyAuthFilter`** (`OncePerRequestFilter`):
- Reads `X-API-Key` header
- Validates against `app.security.api-keys` (comma-separated, loaded from `${API_KEYS:}`)
- **Dev mode**: when `api-keys` is empty (default), all requests pass through without auth — existing tests and local dev work unchanged
- **Prod mode**: any request without a valid key gets `401 Unauthorized` (application/problem+json body)

**`SecurityConfig`:**
- CSRF disabled (stateless API)
- Session creation: STATELESS
- Permitted without auth: `/actuator/health`, `/actuator/prometheus`, `/actuator/info`, Swagger UI
- All other requests: require auth
- `ApiKeyAuthFilter` inserted before `UsernamePasswordAuthenticationFilter`

**Adding keys to prod:** add `API_KEYS` to the Secrets Manager secret `/knowledge-assistant/prod`. Comma-separate multiple keys to support key rotation without downtime (old and new key both valid during the rotation window).

### 5. `QuestionCondenser` — History-Aware Retrieval

**The problem:** in a multi-turn conversation, follow-up questions like `"What does it say about chapter 3?"` embed badly — the vector search has no idea what "chapter 3" refers to without the conversation context.

**The fix:** when `conversationHistory` is non-empty, rewrite the follow-up question to a standalone form before embedding it for retrieval.

```
ChatService.sendMessage()
  ├─ history = windowedHistory(sessionId)
  ├─ questionForRetrieval = questionCondenser.condense(question, history)
  │     Calls LLM: "Rewrite the follow-up question as a self-contained question..."
  │     Returns original question unchanged if: history is empty, condenser disabled, or LLM fails
  └─ queryService.query(questionForRetrieval, filter, history)
       ↑ condensed question used for EMBEDDING + RETRIEVAL
       ↑ original question is in history → LLM sees what was actually asked
```

The condensed question is only used for the retrieval step. The original question is still in `history`, so the final LLM response feels natural to the user.

Configurable: `app.question-condenser.enabled: true`. Exceptions in condensing fall back to the original question — never blocks the query.

### 6. Provider-Aware Similarity Threshold

**The problem:** `nomic-embed-text` (Ollama) produces cosine similarity scores in a systematically lower range than `text-embedding-3-small` (OpenAI). Using a threshold of 0.75 with Ollama returns almost nothing.

Config:
```yaml
app:
  similarity:
    threshold: 0.75        # OpenAI text-embedding-3-small
    threshold-ollama: 0.5  # nomic-embed-text scores lower overall
```

`QueryService` injects `app.embedding.provider` and selects the appropriate threshold at query time. No changes to domain model or ports.

### 7. `ChunkingStrategy` Interface + `SemanticChunker`

`ChunkingStrategy` interface added to `domain/service` (pure Java). `FixedSizeChunker` now implements it. `EmbeddingService` injects `ChunkingStrategy` (not the concrete class).

`SemanticChunker` (pure Java, zero Spring annotations, wired by `ChunkingConfig`):
1. Split text into sentences on `(?<=[.!?])\s+`
2. Embed all sentences via `EmbeddingPort`
3. Find boundaries where cosine similarity between adjacent sentence embeddings drops below `app.chunking.semantic.split-threshold` (default 0.7)
4. Group sentences between boundaries into chunks
5. Fallback: oversized semantic chunks are further split by word count (same `app.chunking.chunk-size` limit)

Selected by `app.chunking.strategy: fixed|semantic` (default: `fixed`). Semantic chunking doubles ingestion embedding cost — worthwhile for dense technical documents, overkill for short plaintext files.

`ChunkingConfig` wires the correct implementation via `@ConditionalOnProperty` beans.

---

## Architecture Decisions

### Breaking `LlmPort` now vs. later

Changing `LlmPort` from `String` to `LlmResponse` touches every adapter and every caller. Phase 10 is the right time: the codebase is fully known, ArchUnit prevents accidental drift, and every affected test needs to be updated anyway for other Phase 10 changes. Deferring further would make the migration more expensive as more callers accumulate.

### Semantic chunking in domain, not infrastructure

`SemanticChunker` depends on `EmbeddingPort` (a domain interface). Domain services are allowed to use domain ports — that's the whole point of the port abstraction. The actual embedding call is behind the port; `SemanticChunker` has no knowledge of Ollama or OpenAI. Zero Spring annotations; wired externally by `ChunkingConfig` (same pattern as `FixedSizeChunker`). ArchUnit passes.

### Dev-mode pass-through for API key auth

`app.security.api-keys` defaults to empty string. The filter checks `validKeys.isEmpty()` and passes all requests through. This means:
- All existing tests continue to pass without modification
- Local dev works without setting any keys
- CI/CD pipelines don't need key configuration
- Production is secure by default once the key is set in Secrets Manager

---

## Files Created

| File | Notes |
|---|---|
| `domain/model/LlmResponse.java` | New record: content, promptTokens, completionTokens, modelUsed |
| `domain/service/ChunkingStrategy.java` | Pure Java interface |
| `domain/service/SemanticChunker.java` | Sentence embedding + cosine similarity boundary detection |
| `application/service/QuestionCondenser.java` | LLM-based question rewriter; graceful fallback |
| `infrastructure/config/SecurityConfig.java` | Spring Security filter chain |
| `infrastructure/security/ApiKeyAuthFilter.java` | `OncePerRequestFilter`; dev-mode pass-through |
| `docs/PHASE_10_RUNBOOK.md` | Production operational runbook |
| `docs/adr/ADR-006-authentication.md` | API key vs JWT decision |

## Files Modified

| File | Change |
|---|---|
| `domain/port/out/LlmPort.java` | Return type `String` → `LlmResponse` |
| `domain/model/QueryResult.java` | Added `promptTokens`, `completionTokens`, `modelUsed` |
| `domain/model/ChatMessage.java` | 4-arg `create` delegates to new 7-arg overload |
| `domain/service/FixedSizeChunker.java` | `implements ChunkingStrategy` |
| `application/service/QueryService.java` | Provider-aware threshold; populates `QueryResult` from `LlmResponse` |
| `application/service/ChatService.java` | Injects `QuestionCondenser`; uses condensed question for retrieval; passes token usage to `ChatMessage` |
| `application/service/EvaluationService.java` | `.content()` on `LlmResponse` |
| `infrastructure/llm/OpenAiLlmAdapter.java` | `@CircuitBreaker`, `@Retry`, returns `LlmResponse` with usage |
| `infrastructure/llm/AnthropicLlmAdapter.java` | Same |
| `infrastructure/llm/OllamaLlmAdapter.java` | Same |
| `infrastructure/config/ChunkingConfig.java` | Two conditional beans returning `ChunkingStrategy` |
| `infrastructure/persistence/adapter/EmbeddingService.java` | `FixedSizeChunker` field → `ChunkingStrategy` |
| `api/controller/QueryController.java` | `@RateLimiter(name = "queryApi")` |
| `api/controller/ChatController.java` | `@RateLimiter(name = "chatApi")` |
| `api/exception/GlobalExceptionHandler.java` | 429 handler for `RequestNotPermittedException` |
| `pom.xml` | `spring-boot-starter-security` |
| `application.yml` | `resilience4j.*`, `app.question-condenser`, `app.chunking.strategy/semantic`, `app.similarity.threshold-ollama`, `app.security.api-keys` |
| `application-prod.yml` | Note on `API_KEYS` in Secrets Manager |

## Acceptance Criteria

- [x] `mvn verify` green: 126 unit + 20 integration tests, 0 failures
- [x] ArchUnit passes — Resilience4j and Spring Security not in blocked packages
- [x] `app.security.api-keys` empty → all requests pass (dev mode)
- [x] `app.security.api-keys=secret123` → requests without `X-API-Key: secret123` get 401
- [x] Circuit breaker fallback returns 200 with degraded message (not 500)
- [x] `>20 requests/10s` to `/queries` or `/sessions/*/messages` → 429
- [x] `app.question-condenser.enabled: false` disables LLM rewrite call
- [x] `app.chunking.strategy: semantic` activates `SemanticChunker` (requires `EmbeddingPort` available)
- [x] `ChatMessage` ASSISTANT entries now have `promptTokens`, `completionTokens`, `modelUsed` populated

## Known Limitations / Deferred

- **JWT with external IdP:** documented in `PHASE_10_RUNBOOK.md` as upgrade path from API keys
- **Per-user rate limiting:** requires API key identity to be threaded into the rate limiter key; straightforward once auth is working
- **Token metric counters:** `llm.tokens.prompt` / `llm.tokens.completion` Micrometer counters — data is now available via `LlmResponse`, wiring to Micrometer is Phase 11 scope
- **Hybrid search (vector + BM25):** requires `pg_trgm` + `tsvector` + scoring combination — own phase
- **Reranking (Cohere):** new port + adapter + API key — own phase
- **Tiktoken-based chunk token counting:** word-count proxy (`1 token ≈ 0.75 words`) is good enough for most documents; exact token counting deferred

---

## Git Commit Message

```
feat: Phase 10 — production hardening (resilience, auth, RAG quality)

LlmPort → LlmResponse (breaking change):
- New domain record: content, promptTokens, completionTokens, modelUsed
- All 3 adapters return LlmResponse; Spring AI usage metadata extracted
- ChatMessage ASSISTANT entries now carry token data (schema had columns
  since Phase 2; always null until this phase)
- QueryResult extended with token fields; flows to API response

Resilience (Resilience4j):
- @CircuitBreaker(name="llm"): opens at 50% failure rate in 10s window,
  half-open after 30s; fallback returns graceful degraded message
- @Retry(name="llm"): 3 attempts, exponential backoff 2s/4s,
  only on transient network errors
- @RateLimiter on /queries and /sessions/*/messages: 20 req/10s → 429

Security (Spring Security):
- ApiKeyAuthFilter: X-API-Key header validation; dev-mode pass-through
  when app.security.api-keys is empty (tests + local dev unaffected)
- SecurityConfig: STATELESS, CSRF disabled, actuator + Swagger permitted

RAG quality:
- QuestionCondenser: rewrites follow-up questions to standalone form
  before embedding; fallback to original on error or empty history
- Provider-aware similarity threshold: threshold-ollama=0.5 for
  nomic-embed-text (vs 0.75 for OpenAI text-embedding-3-small)
- ChunkingStrategy port + SemanticChunker: sentence-level embedding
  similarity boundaries; selected by app.chunking.strategy=semantic

Tests: 126 unit (17 new/updated) + 20 integration — BUILD SUCCESS

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```
