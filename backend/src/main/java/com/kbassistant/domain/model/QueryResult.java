package com.kbassistant.domain.model;

import java.util.List;

public record QueryResult(
        String answer,
        List<SourceChunk> sources,
        long durationMs,
        int promptTokens,
        int completionTokens,
        String modelUsed
) {
    public boolean hasResults() {
        return !sources.isEmpty();
    }
}
