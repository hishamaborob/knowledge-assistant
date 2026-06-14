package com.kbassistant.domain.port.out;

import java.util.List;

/**
 * Port for text embedding generation.
 *
 * Takes a batch of text strings and returns one embedding vector per input,
 * in the same order. The vector dimension is determined by the active adapter
 * (768 for nomic-embed-text, configurable for OpenAI).
 *
 * Implementations: OllamaEmbeddingAdapter (local), OpenAiEmbeddingAdapter (production).
 */
public interface EmbeddingPort {
    List<float[]> embed(List<String> texts);
}
