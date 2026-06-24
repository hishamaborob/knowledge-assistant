package com.kbassistant.infrastructure.config;

import com.kbassistant.domain.port.out.EmbeddingPort;
import com.kbassistant.domain.service.ChunkingStrategy;
import com.kbassistant.domain.service.FixedSizeChunker;
import com.kbassistant.domain.service.SemanticChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChunkingConfig {

    @Bean
    @ConditionalOnProperty(name = "app.chunking.strategy", havingValue = "semantic")
    public ChunkingStrategy semanticChunker(EmbeddingPort embeddingPort,
            @Value("${app.chunking.semantic.split-threshold:0.7}") double splitThreshold,
            @Value("${app.chunking.chunk-size:512}") int maxChunkWords) {
        return new SemanticChunker(embeddingPort, splitThreshold, maxChunkWords);
    }

    @Bean
    @ConditionalOnProperty(name = "app.chunking.strategy", havingValue = "fixed", matchIfMissing = true)
    public ChunkingStrategy fixedSizeChunker(
            @Value("${app.chunking.chunk-size:512}") int chunkSize,
            @Value("${app.chunking.overlap:64}") int overlap) {
        return new FixedSizeChunker(chunkSize, overlap);
    }
}
