package com.kbassistant.infrastructure.embedding;

import com.kbassistant.domain.port.out.EmbeddingPort;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EmbeddingPort implementation backed by Ollama nomic-embed-text (768 dims).
 *
 * Active when: app.embedding.provider=ollama (set in application-local.yml).
 * Requires Ollama running at spring.ai.ollama.base-url (default: http://localhost:11434)
 * with the nomic-embed-text model pulled: `ollama pull nomic-embed-text`
 *
 * Each text is embedded in a separate call — Ollama processes sequentially.
 * For 20 chunks at ~50ms/call this is ~1s total, acceptable for local dev.
 */
@Component
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "ollama")
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final OllamaEmbeddingModel model;

    public OllamaEmbeddingAdapter(OllamaEmbeddingModel model) {
        this.model = model;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream()
                .map(model::embed)
                .toList();
    }
}
