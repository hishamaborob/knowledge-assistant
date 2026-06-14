package com.kbassistant.domain.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits text into fixed-size overlapping chunks by word count.
 *
 * Word count is used as a proxy for token count (1 token ≈ 0.75 words for English).
 * A 512-word chunk is approximately 680 tokens — well within context limits.
 * Phase 10 can swap this for tiktoken-based counting if token budget accuracy matters.
 *
 * Wired as a Spring bean by ChunkingConfig in infrastructure — this class has
 * zero framework dependencies and can be unit-tested without a Spring context.
 */
public class FixedSizeChunker {

    private final int chunkSize;
    private final int overlap;

    public FixedSizeChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be > 0");
        if (overlap < 0) throw new IllegalArgumentException("overlap must be >= 0");
        if (overlap >= chunkSize) throw new IllegalArgumentException("overlap must be < chunkSize");
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /**
     * Splits text into chunks. Returns a single chunk when text fits within chunkSize.
     * Returns an empty list for null/blank input.
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] words = text.trim().split("\\s+");
        if (words.length == 0) {
            return List.of();
        }
        if (words.length <= chunkSize) {
            return List.of(String.join(" ", words));
        }

        List<String> chunks = new ArrayList<>();
        int stride = chunkSize - overlap;
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            if (end == words.length) break;
            start += stride;
        }

        return chunks;
    }

    public int chunkSize() { return chunkSize; }
    public int overlap()   { return overlap; }
}
