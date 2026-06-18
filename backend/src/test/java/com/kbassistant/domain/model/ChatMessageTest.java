package com.kbassistant.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ChatMessageTest {

    @Test
    void create_userMessage_hasEmptyCitations() {
        ChatSessionId sessionId = ChatSessionId.generate();

        var message = ChatMessage.create(sessionId, ChatRole.USER, "What is Spring AI?", List.of());

        assertThat(message.id()).isNotNull();
        assertThat(message.sessionId()).isEqualTo(sessionId);
        assertThat(message.role()).isEqualTo(ChatRole.USER);
        assertThat(message.content()).isEqualTo("What is Spring AI?");
        assertThat(message.citations()).isEmpty();
        assertThat(message.createdAt()).isNotNull();
    }

    @Test
    void create_assistantMessage_carriesCitations() {
        ChatSessionId sessionId = ChatSessionId.generate();
        SourceChunk source = new SourceChunk(DocumentId.generate(), "Guide", "snippet...", 0.9);

        var message = ChatMessage.create(sessionId, ChatRole.ASSISTANT, "It's a framework.", List.of(source));

        assertThat(message.citations()).containsExactly(source);
    }

    @Test
    void create_nullCitations_defaultsToEmptyList() {
        var message = ChatMessage.create(ChatSessionId.generate(), ChatRole.USER, "question", null);
        assertThat(message.citations()).isEmpty();
    }

    @Test
    void reconstitute_rebuildsTokenAndModelFields() {
        ChatMessageId id = ChatMessageId.generate();
        ChatSessionId sessionId = ChatSessionId.generate();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        var message = ChatMessage.reconstitute(
                id, sessionId, ChatRole.ASSISTANT, "answer", List.of(),
                120, 45, "claude-sonnet-4-6", createdAt);

        assertThat(message.promptTokens()).isEqualTo(120);
        assertThat(message.completionTokens()).isEqualTo(45);
        assertThat(message.modelUsed()).isEqualTo("claude-sonnet-4-6");
        assertThat(message.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void equality_basedOnId() {
        ChatSessionId sessionId = ChatSessionId.generate();
        var m1 = ChatMessage.create(sessionId, ChatRole.USER, "a", List.of());
        var m2 = ChatMessage.create(sessionId, ChatRole.USER, "a", List.of());

        assertThat(m1).isNotEqualTo(m2);
        assertThat(m1).isEqualTo(m1);
    }
}
