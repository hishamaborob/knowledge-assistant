package com.kbassistant.infrastructure.config;

import com.kbassistant.domain.service.FixedSizeChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChunkingConfig {

    @Value("${app.chunking.chunk-size:512}")
    private int chunkSize;

    @Value("${app.chunking.overlap:64}")
    private int overlap;

    // FixedSizeChunker is pure Java (no Spring imports) — wired here so
    // infrastructure reads the config, domain stays framework-free.
    @Bean
    public FixedSizeChunker fixedSizeChunker() {
        return new FixedSizeChunker(chunkSize, overlap);
    }
}
