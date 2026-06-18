package com.kbassistant.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root for a chat conversation. Deliberately lightweight — messages
 * are not loaded as part of this aggregate (same precedent as Document vs.
 * DocumentChunk); they are queried independently via ChatMessageRepository.
 */
public class ChatSession {

    private ChatSessionId id;
    private String userId;
    private Instant createdAt;

    private ChatSession() {}

    public static ChatSession create(String userId) {
        Objects.requireNonNull(userId, "userId");

        var session = new ChatSession();
        session.id = ChatSessionId.generate();
        session.userId = userId;
        session.createdAt = Instant.now();
        return session;
    }

    public static ChatSession reconstitute(ChatSessionId id, String userId, Instant createdAt) {
        var session = new ChatSession();
        session.id = id;
        session.userId = userId;
        session.createdAt = createdAt;
        return session;
    }

    public ChatSessionId id()    { return id; }
    public String userId()       { return userId; }
    public Instant createdAt()   { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatSession that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
