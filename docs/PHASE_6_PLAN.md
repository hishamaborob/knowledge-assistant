# Phase 6 — Anthropic LLM Adapter + Improved Prompt Engineering

## Goal

Prove the `LlmPort` abstraction delivers on its promise: add Claude as a third
production-grade LLM provider with zero changes to `QueryService`. Improve the
system prompt for more precise, parseable citation and refusal behavior.

## What Phase 6 Delivers

- `AnthropicLlmAdapter`: calls Claude (claude-sonnet-4-6) via Spring AI; active when `app.llm.provider=anthropic`
- Improved `SYSTEM_PROMPT` in `QueryService`: explicit refusal phrase, multi-source citation format, clearer separation of citation rules from the no-speculate instruction
- No `pom.xml` or auto-configuration changes required — `spring-ai-anthropic-spring-boot-starter` and `spring.ai.anthropic.*` config were already in place from earlier work

**Not in scope:** Gemini adapter, streaming responses, token usage tracking, conversation history (later phases).

---

## Architecture Decisions

### Anthropic auto-configuration was already unblocked

Unlike Phase 5's OpenAI/Ollama setup, the base `application.yml` does **not**
exclude `AnthropicAutoConfiguration` and already declares
`spring.ai.anthropic.api-key` and `spring.ai.anthropic.chat.options.model`
(defaulting to `claude-sonnet-4-6`). This means `AnthropicChatModel` has been
available in the Spring context since Phase 5 — Phase 6 only needed to add the
adapter that consumes it.

`application-local.yml` excludes `AnthropicAutoConfiguration` because local
development uses Ollama for both embedding and LLM — there's no reason to
construct an `AnthropicChatModel` bean when it's never selected.

### `@ConditionalOnProperty` is the only activation gate

`AnthropicLlmAdapter` is annotated `@ConditionalOnProperty(name = "app.llm.provider", havingValue = "anthropic")`.
Even with `AnthropicChatModel` present in the context, the adapter bean is
only created when explicitly selected. `QueryService` depends on `LlmPort`
only — it has no awareness of which adapter is wired in.

### Why an invalid/missing API key doesn't fail startup

Spring AI's Anthropic auto-configuration does not validate the API key format
or reachability at bean-creation time — the same pattern already used for
OpenAI (`sk-placeholder-...`). A blank or placeholder key only fails when the
adapter actually calls `chatModel.call(prompt)`, surfacing as a runtime
exception at query time, not a startup crash.

### Improved system prompt

The Phase 5 prompt worked but left the refusal message unconstrained ("say so
explicitly") and didn't address multi-source citations. The Phase 6 version:

```
You are a knowledge assistant. Answer questions using ONLY the information provided in the numbered context below.

Citation rules:
- Every factual claim must cite its source using [N] where N is the context entry number
- If multiple sources support the same claim, cite all of them: [1][3]

If the context does not contain sufficient information to answer the question, respond with exactly:
"I don't have enough information in the provided documents to answer this question."

Do not speculate, infer, or draw on knowledge outside the provided context.
```

The exact refusal string is now fixed and quotable — useful if a caller later
wants to detect "no answer" responses programmatically without parsing
arbitrary LLM phrasing. No test asserts on the literal prompt text, so this
was a safe drop-in change.

### Same test pattern as Ollama/OpenAI adapters

`AnthropicChatModel.call(Prompt)` does not exhibit the `call(Message...)`
overload ambiguity that `OllamaChatModel` has, but `AnthropicLlmAdapterTest`
still uses the `doReturn(...).when(chatModel).call((Prompt) any())` pattern
with real `ChatResponse`/`Generation`/`AssistantMessage` objects, for
consistency across all three adapter test suites.

---

## Files Created

### Infrastructure — new

| File | Notes |
|---|---|
| `infrastructure/llm/AnthropicLlmAdapter.java` | `@ConditionalOnProperty(app.llm.provider=anthropic)`; wraps `AnthropicChatModel` |

### Tests — new

| File | Type | What it proves |
|---|---|---|
| `infrastructure/llm/AnthropicLlmAdapterTest.java` | Unit | Returns LLM content, passes 2 messages in prompt, empty response — mirrors `OllamaLlmAdapterTest` |

### Modified files

| File | Change |
|---|---|
| `application/service/QueryService.java` | Rewrote `SYSTEM_PROMPT`: fixed refusal string, multi-source citation rule, clearer structure |

No `pom.xml`, `application.yml`, or `application-local.yml` changes were needed — the dependency and config were already in place.

---

## Sequence: Provider Selection at Startup

```
Spring Boot startup
  │
  ├─ AnthropicAutoConfiguration runs (not excluded in base application.yml)
  │     AnthropicChatModel bean created using spring.ai.anthropic.api-key
  │     (placeholder/blank key does not fail bean creation — only the first call)
  │
  ├─ @ConditionalOnProperty(app.llm.provider) evaluated
  │     ollama    → OllamaLlmAdapter @Bean created
  │     openai    → OpenAiLlmAdapter @Bean created
  │     anthropic → AnthropicLlmAdapter @Bean created (injects AnthropicChatModel)
  │
  └─ QueryService autowired with whichever LlmPort impl was created
       (constructor injection; never references a concrete adapter class)
```

---

## Acceptance Criteria

- [x] `AnthropicLlmAdapter` implements `LlmPort`, lives in `infrastructure.llm`
- [x] `app.llm.provider=anthropic` + valid `ANTHROPIC_API_KEY` → adapter calls Claude and returns cited answers (architecture verified; live call requires a real key, not exercised in CI)
- [x] Switching `ollama` → `openai` → `anthropic` requires only a config/env change, no code change
- [x] `AnthropicLlmAdapterTest`: 3/3 passing
- [x] Full unit suite green: 83 tests, 0 failures (was 80 before Phase 6 — +3 from `AnthropicLlmAdapterTest`)
- [x] ArchUnit passes: `AnthropicLlmAdapter` imports nothing from `api` or `application`

---

## Git Commit Message

```
feat: Phase 6 — Anthropic LLM adapter + improved system prompt

Infrastructure:
- AnthropicLlmAdapter: @ConditionalOnProperty(app.llm.provider=anthropic)
  Wraps AnthropicChatModel.call(Prompt); getText() on the result, same
  pattern as OllamaLlmAdapter/OpenAiLlmAdapter
- No pom.xml or auto-configuration changes needed — spring-ai-anthropic
  starter and spring.ai.anthropic.* config were already present from
  earlier work; AnthropicAutoConfiguration was never excluded in the
  base application.yml

Application:
- QueryService: rewrote SYSTEM_PROMPT — fixed, quotable refusal string
  ("I don't have enough information in the provided documents..."),
  multi-source citation format [1][3], clearer separation of citation
  rules from the no-speculate instruction

Tests:
- AnthropicLlmAdapterTest: 3 unit tests mirroring OllamaLlmAdapterTest
  (returns content, passes 2 messages, empty response)

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## PR Description

```markdown
## Phase 6: Anthropic LLM Adapter + Improved Prompt

### What
Adds Claude as a third interchangeable LLM provider, proving the `LlmPort`
abstraction built in Phase 5 works as designed — the only change required to
add a provider is one adapter class. Also tightens the RAG system prompt's
citation and refusal behavior.

### Key decisions
- **No infra changes needed**: `spring-ai-anthropic-spring-boot-starter` and
  `spring.ai.anthropic.*` config already existed from earlier setup; this PR
  only adds the adapter and its test.
- **Fixed refusal string**: the system prompt now specifies an exact phrase
  for "not enough information," making it possible to detect no-answer
  responses programmatically later without parsing arbitrary text.
- **Provider switching stays purely config-driven**: `app.llm.provider=anthropic`
  with a real `ANTHROPIC_API_KEY` is the only change needed to go live; no
  code in `QueryService` or the controller changes.

### Not included
- Gemini adapter (deferred — Vertex AI requires GCP project + auth setup)
- Streaming responses (separate HTTP design decision — SSE vs WebFlux)
- Token usage tracking (better scoped with Micrometer in the Observability phase)
```
