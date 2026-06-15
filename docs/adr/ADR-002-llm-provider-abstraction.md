# ADR-002: LLM Provider Abstraction via Spring AI + Domain Port

**Status:** Accepted  
**Date:** 2026-06-10

## Context

We want to support Ollama (local), OpenAI, Anthropic Claude, and Google Gemini interchangeably.
Spring AI provides `ChatModel` and `EmbeddingModel` abstractions, but they are framework types.
Importing Spring AI into the domain layer would violate hexagonal boundaries.

Ollama is the default local provider — no API key required, runs offline, and nomic-embed-text
produces 768-dim vectors that match OpenAI text-embedding-3-small when configured to 768 dims.

## Decision

Define two domain ports:
- `LlmPort` — chat completion
- `EmbeddingPort` — vector generation

Each provider adapter wraps Spring AI's `ChatModel`/`EmbeddingModel` and implements these ports.
Provider selection via `@ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")`.
LLM and embedding providers are switched independently:
- `app.llm.provider=ollama|openai|anthropic|gemini`
- `app.embedding.provider=ollama|openai`

**Embedding note:** Only one embedding provider is active at a time. The model used for ingestion
**must** be the same model used for query embedding — mixing models produces incomparable vectors.
Both Ollama nomic-embed-text and OpenAI text-embedding-3-small are configured to 768 dims, making
them schema-compatible (same column size) even if vectors are not interchangeable across models.

## Consequences

**Good:**
- Domain has no Spring AI or provider-specific imports
- Swap provider: change one config property, restart
- Each adapter can be unit-tested by mocking `LlmPort`

**Bad:**
- Spring AI abstracts away some provider-specific features (e.g., Anthropic's extended thinking, OpenAI's Structured Outputs). Advanced features require provider-specific code paths.
- Spring AI's model IDs and config keys differ per provider — provider-specific config sections are needed.

## When to Use AWS Bedrock Instead

1. **Compliance/data residency:** Bedrock runs in your VPC. External APIs send data to third-party servers. If your documents contain PII or regulated data, Bedrock may be required.
2. **Unified IAM:** No API keys to rotate. IAM role permissions are sufficient.
3. **Cost at scale:** Bedrock's Titan Embeddings is cheaper than OpenAI for high-volume embedding.
4. **Already on AWS:** Avoid cross-cloud egress charges and added latency.

**Trade-off:** Bedrock has a smaller model selection and slower feature releases than direct APIs.
