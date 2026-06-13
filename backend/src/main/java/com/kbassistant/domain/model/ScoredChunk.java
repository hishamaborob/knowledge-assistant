package com.kbassistant.domain.model;

/**
 * A retrieved chunk paired with its cosine similarity score.
 * Score range: 0.0 (orthogonal, no similarity) to 1.0 (identical).
 *
 * Note: scores come from the retrieval step, not LLM output parsing.
 * This is what makes citations reliable — they're grounded in the
 * actual vector search result, not extracted from generated text.
 */
public record ScoredChunk(DocumentChunk chunk, double score) {

    public ScoredChunk {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Similarity score must be in [0.0, 1.0], got: " + score);
        }
    }
}
