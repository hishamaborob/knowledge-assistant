# ADR-003: Chunking Strategy — Fixed-Size First, Semantic Later

**Status:** Accepted  
**Date:** 2026-06-10

## Context

Chunking determines retrieval quality more than almost any other factor.
Options range from trivial (fixed character count) to expensive (LLM-based semantic chunking).

## Decision

**Phase 4:** Implement `FixedSizeChunker` — 512 tokens, 64 token overlap.
**Phase 10:** Add `SemanticChunker` — split where embedding cosine similarity between adjacent sentences drops below a threshold. (Phase 7 delivered conversation history instead.)

The `ChunkingStrategy` port allows both to coexist and be selected per document type via config or document metadata.

## Token Budget Math

Given a 4096-token context LLM:
- System prompt: ~300 tokens
- Retrieved chunks (topK=5, 512 tokens each): 2560 tokens
- Question + answer buffer: ~1200 tokens
- Total: ~4060 tokens — fits within 4096

For GPT-4o (128k context): topK=20 at 512 tokens = 10240 tokens retrieved — much better coverage.

## Chunk Size Recommendations

| Content Type | Chunk Size | Overlap | Rationale |
|---|---|---|---|
| Legal/technical docs | 256-512 | 64-128 | High density, precision matters |
| Narrative/general prose | 512-1024 | 128 | Broader context helps |
| Code | 256-512 | 0-32 | Function boundaries > arbitrary splits |
| FAQ/structured | 128-256 | 0 | Each Q&A is atomic |

## Consequences

**Fixed-size:** Fast, deterministic, easy to reason about. May split mid-sentence or mid-concept.

**Semantic:** Better chunk boundaries, higher retrieval quality. Requires embedding every sentence — doubles ingestion cost and latency. Worth it for production.

**Overlap:** Ensures concepts spanning chunk boundaries appear in at least one chunk. Increases storage ~12% for 64/512 overlap ratio.
