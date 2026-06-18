package com.kbassistant.api.dto;

import com.kbassistant.domain.model.ChatSession;

public record SessionResponse(
        String id,
        String userId,
        String createdAt
) {
    public static SessionResponse from(ChatSession session) {
        return new SessionResponse(
                session.id().toString(),
                session.userId(),
                session.createdAt().toString()
        );
    }
}
