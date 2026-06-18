package com.kbassistant.api.dto;

import com.kbassistant.domain.model.QueryResult;

import java.util.List;

public record ChatMessageResponse(
        String answer,
        List<SourceChunkResponse> sources,
        long durationMs
) {
    public static ChatMessageResponse from(QueryResult result) {
        return new ChatMessageResponse(
                result.answer(),
                result.sources().stream().map(SourceChunkResponse::from).toList(),
                result.durationMs()
        );
    }
}
