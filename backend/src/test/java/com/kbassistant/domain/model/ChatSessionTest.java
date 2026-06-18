package com.kbassistant.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ChatSessionTest {

    @Test
    void create_assignsIdAndCreatedAt() {
        var session = ChatSession.create("user-1");

        assertThat(session.id()).isNotNull();
        assertThat(session.userId()).isEqualTo("user-1");
        assertThat(session.createdAt()).isNotNull();
    }

    @Test
    void create_requiresNonNullUserId() {
        assertThatNullPointerException().isThrownBy(() -> ChatSession.create(null));
    }

    @Test
    void reconstitute_rebuildsExactState() {
        ChatSessionId id = ChatSessionId.generate();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        var session = ChatSession.reconstitute(id, "user-2", createdAt);

        assertThat(session.id()).isEqualTo(id);
        assertThat(session.userId()).isEqualTo("user-2");
        assertThat(session.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void equality_basedOnId() {
        var s1 = ChatSession.create("user-1");
        var s2 = ChatSession.create("user-1");

        assertThat(s1).isNotEqualTo(s2);
        assertThat(s1).isEqualTo(s1);
    }
}
