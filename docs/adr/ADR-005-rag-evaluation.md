# ADR-005: RAG Evaluation Strategy — Online LLM-as-a-Judge with Sampling

**Status:** Accepted  
**Date:** 2026-06-21

## Context

A RAG system has two distinct health dimensions:
1. **Operational health**: is it running? is it fast? — answered by latency/error-rate metrics.
2. **Answer quality health**: are the answers correct and grounded? — not answerable by operational metrics alone.

After Phase 8, the system has no signal on answer quality. A query that returns in 200ms and HTTP 200 could still be hallucinating or completely off-topic. Phase 9 adds quality observability.

Four evaluation strategies were considered:

| Strategy | Ground truth needed | LLM calls per query | Online/Offline |
|---|---|---|---|
| LLM-as-a-judge (single prompt) | No | 1–2 (async, sampled) | Online |
| RAGAS decomposed evaluation | No | 3–5 per query | Online |
| Retrieval metrics (hit rate, MRR) | Yes | 0 | Offline |
| Human feedback (thumbs up/down) | Collected over time | 0 | Online (needs UI) |

## Decision

Use **online LLM-as-a-judge with 20% sampling** for two metrics: faithfulness and answer relevance. Both are evaluated against the retrieved context and the answer — no ground truth required.

**Faithfulness** (0–1): does the answer contain only claims directly supported by the retrieved context? This is the RAG-specific failure mode: the LLM inventing facts not present in the documents.

**Answer relevance** (0–1): does the answer actually address the question? This catches evasive or off-topic responses even when faithfulness is high.

The judge prompt is a single LLM call with explicit 0.0–1.0 scale instructions and "Return ONLY a decimal number" in the system prompt. Context is truncated to 4,000 chars in the faithfulness prompt to stay within token limits for all configured providers. Non-numeric responses fall back to 0.5 (neutral) with a warning log.

Evaluation calls are:
- **Async** (`@Async("evaluationTaskExecutor")`) — never blocks the user's response
- **Sampled** (`app.evaluation.sample-rate: 0.2`) — 20% of queries to bound LLM API cost
- **Non-critical** — exceptions are caught and logged as warnings; no evaluation failure reaches the caller

Scores flow into Micrometer `DistributionSummary` meters (`rag.evaluation.faithfulness`, `rag.evaluation.answer_relevance`) and are scraped by Prometheus alongside operational metrics, visible on the same Grafana dashboard.

## Why Not RAGAS

RAGAS decomposes faithfulness into: (1) extract atomic statements from the answer, (2) verify each statement against the context. This requires 3–5 LLM calls per query and is significantly more rigorous. The single-prompt approach gives roughly 80% of the faithfulness signal at 20% of the cost. RAGAS decomposition is an upgrade path when an eval budget permits.

## Why Not Retrieval Metrics

Hit rate and MRR require ground truth: a labelled dataset of (question, expected_chunk_id) pairs. No such dataset exists at this stage. These metrics are a better fit for offline regression testing once a labelled eval set is assembled.

## Why Not Offline Batch Evaluation Only

Offline evaluation requires a representative fixed dataset and a separate pipeline to run it. It doesn't catch distribution shift — if the nature of user queries drifts away from the eval set, the scores stop being meaningful. Online sampling provides continuous signal on actual production queries.

## Consequences

**Good:**
- Live quality signal in the same Grafana dashboard as operational metrics — no separate tooling
- No ground truth required — can start immediately
- Async + sampling means zero impact on user latency and bounded LLM cost
- `app.evaluation.enabled: false` fully disables it for cost-sensitive or local environments

**Bad:**
- 20% sample rate with the same answering model doubles LLM API cost for those queries. Mitigate: configure a cheaper judge model in Phase 10 (e.g., `gpt-4o-mini` or `claude-haiku-4-5`).
- Single-prompt scoring is less rigorous than RAGAS decomposition. The score is directionally correct but can be noisy on edge cases (ambiguous context, compound questions).
- The judge LLM may have systematic biases (e.g., rewarding verbose answers). Scores should be tracked as trends, not as absolute ground truth.

## Upgrade Path

When a labelled eval dataset is available:
1. Add an offline eval pipeline (GitHub Actions nightly job or manual trigger) that runs the dataset through the RAG system and judges each answer.
2. Track scores per-commit for regression detection.
3. Switch the online judge to a cheaper model to reduce per-query cost.
4. Implement RAGAS decomposed faithfulness for higher-signal online evaluation.
