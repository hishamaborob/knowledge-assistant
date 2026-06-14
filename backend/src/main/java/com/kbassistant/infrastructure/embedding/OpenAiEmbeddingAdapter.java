package com.kbassistant.infrastructure.embedding;

import com.kbassistant.domain.port.out.EmbeddingPort;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EmbeddingPort implementation backed by OpenAI text-embedding-3-small.
 *
 * Active when: app.embedding.provider=openai (default for production).
 * Configure spring.ai.openai.embedding.options.dimensions=768 to match
 * nomic-embed-text output so no schema migration is needed between environments.
 *
 * Requires: OPENAI_API_KEY environment variable (or spring.ai.openai.api-key).
 */
@Component
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "openai")
public class OpenAiEmbeddingAdapter implements EmbeddingPort {

    private final OpenAiEmbeddingModel model;

    public OpenAiEmbeddingAdapter(OpenAiEmbeddingModel model) {
        this.model = model;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream()
                .map(model::embed)
                .toList();
    }
}
