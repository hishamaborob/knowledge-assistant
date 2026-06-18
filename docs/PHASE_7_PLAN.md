# Phase 7 — Conversation History

## Goal

Multi-turn chat sessions: create a session, send messages with prior turns
injected into the LLM prompt, retrieve full history. Built on the
`chat_sessions`/`chat_messages` schema that's existed since Phase 2's
migrations (`V4__chat.sql`) but had zero application code using it.

## What Phase 7 Delivers

- `ChatSession` / `ChatMessage` domain entities, `ChatRole` enum, `ChatTurn` value object
- `ChatSessionRepository` / `ChatMessageRepository` domain ports + JPA adapters
- `LlmPort` gains a history-aware overload via a default method — zero changes to existing call sites
- `QueryService` gains a 3-arg overload that injects conversation history into the LLM call; the 2-arg version becomes a thin delegate
- `ChatService`: session lifecycle, history-windowed message sending, history retrieval
- `POST /sessions`, `POST /sessions/{id}/messages`, `GET /sessions/{id}/messages`
- `ChatSessionNotFoundException → 404`

**Not in scope:** token usage population (columns exist in the schema but `LlmPort` still returns plain `String`; deferred to the Observability phase, same scope note as Phase 6), auth-derived `userId` (no security system yet), session deletion/archival.

---

## Architecture Decisions

### `LlmPort` evolves via a default method — not a breaking change

```java
public interface LlmPort {
    default String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, List.of(), userPrompt);
    }
    String complete(String systemPrompt, List<ChatTurn> history, String userPrompt);
}
```

Each adapter (Ollama/OpenAi/Anthropic) implements only the 3-arg method,
building `List<Message>` = `SystemMessage` + history turns (alternating
`UserMessage`/`AssistantMessage`) + final `UserMessage`. The 2-arg method
requires no per-adapter implementation — it's inherited.

### `QueryService` gains a 3-arg overload; the 2-arg version delegates

```java
public QueryResult query(String question, List<DocumentId> filter) {
    return query(question, filter, List.of());
}

public QueryResult query(String question, List<DocumentId> filter, List<ChatTurn> history) {
    // ...unchanged embed → search → context-build logic...
    String answer = llmPort.complete(SYSTEM_PROMPT, history, buildUserPrompt(question, context));
    // ...
}
```

`ChatService` calls the 3-arg overload with real history; `QueryController`'s
existing stateless `/queries` endpoint is untouched — it always passes `List.of()`.

### Real gotcha: `QueryServiceTest` mocks needed updating

Because the unified method body now *always* calls the 3-arg
`llmPort.complete(...)`, even via the 2-arg `query()` overload, the existing
test stubs written as `when(llmPort.complete(anyString(), anyString()))...`
silently stopped matching — Mockito would have returned `null` for the
unstubbed 3-arg call. Fixed by updating all `QueryServiceTest` stubs/verifies
to the 3-arg form (`anyString(), anyList(), anyString()`), and adding a new
test (`query_twoArgOverload_passesEmptyHistory`) that locks in the delegation
behavior — asserting the 2-arg overload passes `List.of()` through to the
3-arg call.

### `ChatSession`/`ChatMessage` are entities, not records

Mirrors `Document`/`DocumentChunk` — private constructor + `create`/
`reconstitute` factories, identity-based `equals`/`hashCode`. Messages are
**not** loaded as part of the `ChatSession` aggregate — same precedent as
`DocumentChunk` vs. `Document` — they're queried independently via
`ChatMessageRepository.findBySessionId`. `ChatTurn` is a plain record
`(ChatRole role, String content)`; it's a transient projection used only for
prompt construction, never persisted directly.

### Citations stored via a dedicated persistence-shape record

`SourceChunk` carries a `DocumentId` value object that Jackson can't
serialize/deserialize without a custom module. JSONB storage instead uses a
plain `CitationRecord(String documentId, String documentName, String contentSnippet, double score)`
in the infrastructure layer — primitives/String only, so Hibernate's existing
`@JdbcTypeCode(SqlTypes.JSON)` pattern (already used for
`DocumentChunkEntity.metadata`) handles it with zero extra configuration.
`ChatMessageJpaAdapter` maps `SourceChunk ↔ CitationRecord`.

### `role` column stays lowercase — matching the already-shipped migration

`V4__chat.sql` documents the convention in a comment (`-- 'user' | 'assistant'`)
and `validate-on-migrate: true` means Flyway checksums applied migrations —
it cannot be edited after the fact. `ChatMessageJpaAdapter` maps
`ChatRole.USER ↔ "user"` / `ChatRole.ASSISTANT ↔ "assistant"` explicitly
(lowercase), unlike `DocumentStatus` which stores uppercase enum names. This
is a deliberate inconsistency carried from a constraint, documented inline.

### History windowing

`app.chat.history-window` (default `10` messages = 5 exchanges) caps how
many prior messages get mapped to `ChatTurn`s and injected into the prompt.
`ChatService.windowedHistory` loads all messages (ascending order), then
takes the last N via `subList`, preserving chronological order within the
window.

---

## Files Created

### Domain — new

| File | Notes |
|---|---|
| `domain/model/ChatSessionId.java` | Mirrors `DocumentId` |
| `domain/model/ChatMessageId.java` | Mirrors `ChunkId` |
| `domain/model/ChatRole.java` | `USER`, `ASSISTANT` |
| `domain/model/ChatTurn.java` | Record: `(ChatRole role, String content)` |
| `domain/model/ChatSession.java` | Entity: id, userId, createdAt |
| `domain/model/ChatMessage.java` | Entity: id, sessionId, role, content, citations, promptTokens/completionTokens/modelUsed (always null this phase), createdAt |
| `domain/exception/ChatSessionNotFoundException.java` | Mirrors `DocumentNotFoundException` |
| `domain/port/out/ChatSessionRepository.java` | `save`, `findById` |
| `domain/port/out/ChatMessageRepository.java` | `save`, `findBySessionId` (ascending) |

### Domain/Application — modified

| File | Change |
|---|---|
| `domain/port/out/LlmPort.java` | Add 3-arg abstract method; 2-arg becomes `default` |
| `application/service/QueryService.java` | Add 3-arg `query()` overload; 2-arg delegates with `List.of()` |

### Application — new

| File | Notes |
|---|---|
| `application/service/ChatService.java` | `startSession`, `sendMessage` (load session → window history → save user msg → `queryService.query(..., history)` → save assistant msg), `getHistory` |

### Infrastructure — new/modified

| File | Notes |
|---|---|
| `infrastructure/llm/OllamaLlmAdapter.java` | **modified** — implements 3-arg `complete()` |
| `infrastructure/llm/OpenAiLlmAdapter.java` | **modified** — implements 3-arg `complete()` |
| `infrastructure/llm/AnthropicLlmAdapter.java` | **modified** — implements 3-arg `complete()` |
| `infrastructure/persistence/entity/ChatSessionEntity.java` | `chat_sessions` mapping |
| `infrastructure/persistence/entity/ChatMessageEntity.java` | `chat_messages` mapping; `citations` via `@JdbcTypeCode(SqlTypes.JSON)` |
| `infrastructure/persistence/entity/CitationRecord.java` | Plain record for JSONB citations |
| `infrastructure/persistence/repository/ChatSessionJpaRepository.java` | `extends JpaRepository<ChatSessionEntity, UUID>` |
| `infrastructure/persistence/repository/ChatMessageJpaRepository.java` | + `findBySessionIdOrderByCreatedAtAsc` |
| `infrastructure/persistence/adapter/ChatSessionJpaAdapter.java` | implements `ChatSessionRepository` |
| `infrastructure/persistence/adapter/ChatMessageJpaAdapter.java` | implements `ChatMessageRepository`; lowercase role mapping |

### API — new/modified

| File | Notes |
|---|---|
| `api/controller/ChatController.java` | `POST /sessions`, `POST /sessions/{id}/messages`, `GET /sessions/{id}/messages` |
| `api/dto/CreateSessionRequest.java` | `@NotBlank userId` |
| `api/dto/SessionResponse.java` | id, userId, createdAt |
| `api/dto/ChatMessageRequest.java` | `@NotBlank question`, nullable `documentIds` |
| `api/dto/ChatMessageResponse.java` | answer, sources, durationMs |
| `api/dto/ChatMessageHistoryItem.java` | id, role, content, citations, createdAt — reuses `SourceChunkResponse` |
| `api/exception/GlobalExceptionHandler.java` | **modified** — add `ChatSessionNotFoundException → 404` |

### Config — modified

| File | Change |
|---|---|
| `application.yml` | Add `app.chat.history-window: 10` |

### Tests — new/modified

| File | What it proves |
|---|---|
| `ChatSessionTest`, `ChatMessageTest` | create/reconstitute invariants |
| `ChatServiceTest` | session-not-found exception, happy path (saves both messages with correct roles/citations), history windowing (verified with a window of 2 against 5 prior messages), empty history on first message, `getHistory` delegation |
| `QueryServiceTest` (**modified**) | stubs/verifies updated to 3-arg `complete`; new test locks in 2-arg→3-arg delegation with empty history |
| `OllamaLlmAdapterTest` / `AnthropicLlmAdapterTest` (**modified**) | new test: message ordering for 3-arg `complete()` — system, history turns (alternating types via `MessageType`), final user message |
| `ChatControllerTest` (`@WebMvcTest`) | 201 on create, 400 on blank userId, 200 on message send, 404 on unknown session (send + history), 400 on blank question, 200 on history fetch |
| `ChatPersistenceIT` (Testcontainers) | session round-trip, **JSONB citations round-trip** (the highest-risk part of this phase), lowercase role storage, chronological ordering, empty-list behavior for unknown session |

---

## Sequence: Send Message

```
Client
  │
  ▼
ChatController.sendMessage(sessionId, request)
  │
  ▼
ChatService.sendMessage(sessionId, question, documentFilter)
  ├─ ChatSessionRepository.findById(sessionId) → ChatSessionNotFoundException if absent
  ├─ ChatMessageRepository.findBySessionId(sessionId)
  │     → window to last N messages → map to List<ChatTurn>
  ├─ ChatMessageRepository.save(ChatMessage.create(USER, question))
  ├─ QueryService.query(question, documentFilter, history)
  │     ├─ EmbeddingPort.embed([question]) → vector
  │     ├─ VectorStorePort.similaritySearch(...)
  │     ├─ buildContext(...)
  │     └─ LlmPort.complete(SYSTEM_PROMPT, history, userPrompt)
  │           → adapter builds [System, ...history turns, User] → model call
  ├─ ChatMessageRepository.save(ChatMessage.create(ASSISTANT, answer, sources))
  └─ return QueryResult
  ▼
ChatMessageResponse { answer, sources, durationMs }
```

---

## Acceptance Criteria

- [x] `POST /sessions` creates and persists a session, returns 201
- [x] `POST /sessions/{id}/messages` on first message uses empty history; on subsequent messages includes prior turns in the LLM prompt
- [x] History window caps at `app.chat.history-window`, oldest-first within the window
- [x] `GET /sessions/{id}/messages` returns full history in chronological order with citations
- [x] Unknown session ID returns 404 on both message-send and history endpoints
- [x] `QueryController`'s existing `/queries` stateless flow is unaffected (no history, same behavior as Phase 5/6)
- [x] `mvn verify` green: 109 unit tests + 20 integration tests, 0 failures
- [x] ArchUnit passes with no new rules needed (existing layer rules are package-generic)

---

## Git Commit Message

```
feat: Phase 7 — conversation history (chat sessions + multi-turn context)

Domain:
- ChatSession, ChatMessage entities; ChatRole, ChatTurn value types
- ChatSessionRepository, ChatMessageRepository ports
- ChatSessionNotFoundException

LlmPort:
- Add 3-arg complete(systemPrompt, history, userPrompt); 2-arg becomes
  a default method delegating with empty history — no breaking change
  to existing adapters' public contract

QueryService:
- Add 3-arg query() overload injecting conversation history into the
  LLM call; 2-arg overload delegates with List.of()

Application:
- ChatService: session lifecycle, history-windowed message sending,
  history retrieval

Infrastructure:
- Ollama/OpenAi/Anthropic adapters implement the 3-arg complete()
- ChatSession/ChatMessageEntity + JPA adapters on the pre-existing
  V4__chat.sql schema (built in Phase 2, unused until now)
- Citations persisted as JSONB via CitationRecord (plain record,
  avoids needing custom Jackson handling for DocumentId)
- role column mapped to lowercase "user"/"assistant" — matches the
  already-shipped, checksum-locked V4 migration's documented convention

API:
- POST /sessions, POST /sessions/{id}/messages, GET /sessions/{id}/messages
- GlobalExceptionHandler: ChatSessionNotFoundException → 404

Tests:
- ChatServiceTest: session lifecycle, history windowing, empty-history
  first message
- QueryServiceTest: updated stubs for the 3-arg LlmPort call (existing
  2-arg stubs no longer matched after the overload unification)
- Adapter tests: new case for 3-arg complete() message ordering
- ChatControllerTest, ChatPersistenceIT (JSONB round-trip)

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## PR Description

```markdown
## Phase 7: Conversation History

### What
Adds multi-turn chat sessions on top of the chat_sessions/chat_messages
schema that's existed since Phase 2's migrations but had no application
code using it. Users can now hold a conversation — prior turns are injected
into the LLM prompt as proper structured messages, not string-concatenated.

### Key decisions
- **LlmPort evolves via a default method**: the 2-arg complete() becomes a
  default delegating to a new 3-arg overload. Zero changes required to
  existing adapter call sites' public contracts — only new method bodies.
- **QueryService is shared, not duplicated**: ChatService calls the same
  embed → search → context pipeline via a new 3-arg query() overload,
  rather than reimplementing RAG logic for the chat path.
- **Real subtlety caught and fixed**: unifying QueryService's two overloads
  into one method body meant the existing 2-arg test mocks on LlmPort
  silently stopped matching. Fixed in QueryServiceTest, not papered over.
- **Citations persist as a plain record**, not the domain SourceChunk —
  avoids teaching Jackson how to serialize the DocumentId value object.
- **role column stays lowercase** to match the already-shipped V4 migration
  (Flyway checksums migrations; it cannot be edited after the fact).

### Not included
- Token usage population (columns exist, but require a richer LlmPort
  response type — deferred to the Observability phase)
- Auth-derived userId (no security system yet — Phase 10)
- Session deletion/archival
```
