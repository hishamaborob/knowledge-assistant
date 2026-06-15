# Phase 5 — Query API (RAG Pipeline)

## Goal

Wire the retrieval-augmented generation loop: `POST /queries` → embed question →
similarity search → build context prompt → call LLM → return answer with source citations.

## What Phase 5 Delivers

- `LlmPort`: domain port for chat completion — single method, no Spring AI types leaked
- `QueryService`: orchestrates the full RAG pipeline end-to-end
- `OllamaLlmAdapter`: calls llama3.2 locally via Ollama; active when `app.llm.provider=ollama`
- `OpenAiLlmAdapter`: calls gpt-4o in production; active when `app.llm.provider=openai`
- `POST /queries`: question + optional document ID filter → answer + source citations
- No-results guard: returns explicit message without calling LLM when search is empty
- Source citations in response: document name, content snippet, similarity score per chunk
- `MethodArgumentNotValidException → 400` added to `GlobalExceptionHandler`
- `IllegalArgumentException → 400` added to `GlobalExceptionHandler` (invalid UUID in filter)

**Not in scope:** streaming responses, conversation history, query persistence, re-ranking (later phases).

---

## Architecture Decisions

### Embedding model must match at query time

The RAG pipeline calls `EmbeddingPort` twice: once at ingestion (to store chunk vectors)
and once at query time (to embed the question before similarity search). Both calls must
use the same model because similarity is only meaningful within a single vector space.

`LlmPort` is entirely independent — the chat LLM never sees vectors. It only receives
plain text assembled from chunk `.content()` fields. You can mix providers freely:
`app.embedding.provider=ollama` + `app.llm.provider=openai` is a valid combination.

### No-results short-circuit

If similarity search returns zero chunks above threshold, `QueryService` returns immediately
with a fixed "No relevant documents found" message. The LLM is never called — avoids
hallucination on an empty context and saves the latency of a model call.

### Context budget cap (12,000 characters)

Total context fed to the LLM is capped at ~12,000 characters (~3,000 tokens). Chunks
are added in descending score order; the loop breaks when the budget is exceeded. Prevents
hitting model context limits when `topK` is high and chunks are long.

### LlmPort signature

```java
String complete(String systemPrompt, String userPrompt);
```

Single-turn, synchronous. The narrow interface keeps the domain free of Spring AI types
and makes `QueryService` trivially unit-testable with a Mockito mock.

Streaming is deferred to a later phase — it requires a different response model
(`Flux<String>` vs `String`) and is a separate concern from the core RAG logic.

### AssistantMessage.getText() not .getContent()

Spring AI 1.0.0-M6's `AbstractMessage` exposes the text content via `getText()`.
`getContent()` does not exist in this version despite appearing in older documentation.
Discovered during compilation and fixed in both LLM adapters.

### OllamaChatModel overload ambiguity in tests

`OllamaChatModel.call()` has multiple overloads (`call(Prompt)`, `call(Message...)`).
Mockito's `any(Prompt.class)` cannot resolve the overload at compile time. Fixed by:
- Using `doReturn(...).when(chatModel).call((Prompt) any())` with explicit cast
- Using real `ChatResponse`/`Generation`/`AssistantMessage` objects instead of mocking the chain

### Queries are stateless

No DB write per query. Keeps Phase 5 simple. Phase 7 can add query history if needed.

---

## Files Created

### Domain — new

| File | Notes |
|---|---|
| `domain/port/out/LlmPort.java` | `String complete(String systemPrompt, String userPrompt)` |
| `domain/model/QueryResult.java` | Record: answer, List\<SourceChunk\>, durationMs |
| `domain/model/SourceChunk.java` | Record: documentId, documentName, contentSnippet, score |

### Application — new

| File | Notes |
|---|---|
| `application/service/QueryService.java` | embed → search → no-results guard → load names → build context → LLM → sources |

### API — new

| File | Notes |
|---|---|
| `api/controller/QueryController.java` | `POST /queries`; converts documentId strings to `DocumentId` domain objects |
| `api/dto/QueryRequest.java` | Record: `@NotBlank question`, `List<String> documentIds` (nullable) |
| `api/dto/QueryResponse.java` | Record: answer, List\<SourceChunkResponse\>, durationMs |
| `api/dto/SourceChunkResponse.java` | Record: documentId (String), documentName, contentSnippet, score |

### Infrastructure — new

| File | Notes |
|---|---|
| `infrastructure/llm/OllamaLlmAdapter.java` | `@ConditionalOnProperty(app.llm.provider=ollama)`; `OllamaChatModel` |
| `infrastructure/llm/OpenAiLlmAdapter.java` | `@ConditionalOnProperty(app.llm.provider=openai)`; `OpenAiChatModel` |

### Modified files

| File | Change |
|---|---|
| `application-local.yml` | Add `app.llm.provider=ollama`, `spring.ai.ollama.chat.model=llama3.2` |
| `api/exception/GlobalExceptionHandler.java` | Add `MethodArgumentNotValidException→400`, `IllegalArgumentException→400` |

### Tests — new

| File | Type | What it proves |
|---|---|---|
| `application/service/QueryServiceTest.java` | Unit (Mockito) | Happy path, no-results, document filter, snippet truncation, context budget, fallback name |
| `api/controller/QueryControllerTest.java` | `@WebMvcTest` | 200 with answer+sources, 400 on blank/null question, 400 on invalid UUID |
| `infrastructure/llm/OllamaLlmAdapterTest.java` | Unit | Delegates to model, returns text, passes 2 messages in prompt |

---

## Sequence: Query Flow

```
Client
  │
  ▼
QueryController.query(QueryRequest)
  │  parse documentIds strings → List<DocumentId>
  ▼
QueryService.query(question, documentFilter)
  ├─ EmbeddingPort.embed([question])         → OllamaEmbeddingAdapter → nomic-embed-text
  │     → float[768] query vector
  │
  ├─ VectorStorePort.similaritySearch(...)   → PgVectorAdapter → pgvector HNSW
  │     → List<ScoredChunk> (sorted by cosine similarity desc)
  │
  ├─ [empty results] → return "No relevant documents found"
  │
  ├─ DocumentRepository.findById(each unique documentId)
  │     → Map<DocumentId, String> docNames
  │
  ├─ buildContext(results, docNames)          budget cap at 12,000 chars
  │     → "[1] (Guide, score: 0.92)\n<chunk text>\n\n[2] ..."
  │
  ├─ LlmPort.complete(SYSTEM_PROMPT, userPrompt)  → OllamaLlmAdapter → llama3.2
  │     → answer String
  │
  └─ buildSources(results, docNames)
        → List<SourceChunk> (snippet ≤ 200 chars)
  │
  ▼
QueryResponse { answer, sources, durationMs }
```

---

## Similarity Threshold Calibration

The default threshold is `0.75`. For nomic-embed-text, semantically related sentences
score in the 0.7–0.95 range. If queries return no results unexpectedly, lower to `0.5`
in `application-local.yml`:

```yaml
app:
  similarity:
    threshold: 0.5
```

This does not require a restart of the embedding pipeline — only affects query-time filtering.

---

## Acceptance Criteria

- [ ] Upload a document → status READY → `POST /queries` returns a grounded answer
- [ ] Response includes `sources` array with documentName, contentSnippet, score
- [ ] `POST /queries` with blank question returns 400
- [ ] `POST /queries` with invalid UUID in `documentIds` returns 400
- [ ] Query with no matching content returns `"No relevant documents found"` without calling LLM
- [ ] `mvn verify` green (94 tests, 0 failures)
- [ ] ArchUnit: `QueryService` imports nothing from `api` or `infrastructure`

---

## Git Commit Message

```
feat: Phase 5 — RAG query API with Ollama llama3.2 and source citations

Domain:
- LlmPort: String complete(systemPrompt, userPrompt)
- QueryResult: answer + List<SourceChunk> + durationMs
- SourceChunk: documentId, documentName, contentSnippet, score

Application:
- QueryService: embed question → similarity search → context assembly → LLM call
  No-results guard: skips LLM when search returns empty (anti-hallucination)
  Context budget: caps at 12,000 chars to avoid LLM context limit
  Document names batch-loaded from DocumentRepository for source citations

API:
- POST /queries: { question, documentIds? } → { answer, sources, durationMs }
- GlobalExceptionHandler: MethodArgumentNotValidException→400, IllegalArgumentException→400

Infrastructure:
- OllamaLlmAdapter: OllamaChatModel.call(Prompt), getText() (not getContent() — M6 API)
  doReturn + (Prompt) cast required in tests to resolve call() overload ambiguity
- OpenAiLlmAdapter: OpenAiChatModel, same interface

Config:
- application-local.yml: app.llm.provider=ollama, spring.ai.ollama.chat.model=llama3.2

Tests:
- QueryServiceTest: happy path, no-results, document filter, budget cap, unknown doc fallback
- QueryControllerTest (@WebMvcTest): 200, 400 on blank/null question, 400 on invalid UUID
- OllamaLlmAdapterTest: real ChatResponse objects, doReturn to avoid overload ambiguity

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## PR Description

```markdown
## Phase 5: RAG Query API

### What
Completes the read path of the RAG pipeline. Users can now submit questions and receive
LLM-generated answers grounded in their uploaded documents.

### Pipeline
POST /queries → embed question (nomic-embed-text) → pgvector similarity search →
context assembly → llama3.2 (local) / gpt-4o (prod) → answer + source citations

### Key decisions
- **Embedding model reused at query time**: `EmbeddingPort` is called with the question
  before similarity search. Must match the ingestion-time model — vector spaces are not
  interchangeable.
- **LLM is independent of embedding model**: `LlmPort` receives plain text only.
  Provider can differ from embedding provider (e.g. Ollama embed + OpenAI chat).
- **No-results guard**: empty similarity search returns immediately without calling the LLM.
  Prevents hallucination and saves latency.
- **Context budget cap**: 12,000 chars max fed to the LLM. Chunks added in score order,
  loop breaks at budget. Prevents hitting model context limits on large document sets.
- **Stateless queries**: no DB write per query. Keeps the read path fast and simple.

### Spring AI M6 gotchas
- `AssistantMessage.getText()` not `.getContent()` (renamed in M6)
- `OllamaChatModel.call()` overload ambiguity requires `(Prompt)` cast in Mockito stubs

### Not included
- Streaming responses (later phase)
- Conversation history / multi-turn (later phase)
- Query analytics / persistence (later phase)
```
