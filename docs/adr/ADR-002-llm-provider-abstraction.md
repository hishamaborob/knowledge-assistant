# ADR-002: LLM Provider Abstraction via Spring AI + Domain Port

**Status:** Accepted  
**Date:** 2026-06-10

## Context

We want to support OpenAI, Anthropic Claude, and Google Gemini interchangeably.
Spring AI provides `ChatModel` and `EmbeddingModel` abstractions, but they are framework types.
Importing Spring AI into the domain layer would violate hexagonal boundaries.

## Decision

Define two domain ports:
- `LlmPort` — chat completion
- `EmbeddingPort` — vector generation

Each LLM provider adapter wraps Spring AI's `ChatModel`/`EmbeddingModel` and implements these ports.
Provider selection via `@ConditionalOnProperty(name = "app.llm.provider", havingValue = "openai")`.

**Embedding note:** Only one provider is active for embeddings at a time. The embedding model
used for ingestion **must** be the same model used for query embedding — mixing models produces
incomparable vectors. This is enforced by storing the model name in `document_chunks.metadata`.

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
