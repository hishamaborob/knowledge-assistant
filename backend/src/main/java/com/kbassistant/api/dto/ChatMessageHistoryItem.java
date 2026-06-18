package com.kbassistant.api.dto;

import com.kbassistant.domain.model.ChatMessage;

import java.util.List;

public record ChatMessageHistoryItem(
        String id,
        String role,
        String content,
        List<SourceChunkResponse> citations,
        String createdAt
) {
    public static ChatMessageHistoryItem from(ChatMessage message) {
        return new ChatMessageHistoryItem(
                message.id().toString(),
                message.role().name(),
                message.content(),
                message.citations().stream().map(SourceChunkResponse::from).toList(),
                message.createdAt().toString()
        );
    }
}
