package com.kbassistant.api.dto;

import com.kbassistant.domain.model.QueryResult;

import java.util.List;

public record QueryResponse(
        String answer,
        List<SourceChunkResponse> sources,
        long durationMs
) {
    public static QueryResponse from(QueryResult result) {
        return new QueryResponse(
                result.answer(),
                result.sources().stream().map(SourceChunkResponse::from).toList(),
                result.durationMs()
        );
    }
}
