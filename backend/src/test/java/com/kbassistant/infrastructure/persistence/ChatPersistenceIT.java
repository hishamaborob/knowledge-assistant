package com.kbassistant.infrastructure.persistence;

import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.ChatMessageRepository;
import com.kbassistant.domain.port.out.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=sk-test",
        "spring.ai.anthropic.api-key=test",
        "spring.ai.vertex.ai.gemini.project-id=test",
        "app.embedding.provider=openai",
        "spring.autoconfigure.exclude="
                + "org.springframework.ai.autoconfigure.vertexai.gemini.VertexAiGeminiAutoConfiguration,"
                + "org.springframework.ai.autoconfigure.anthropic.AnthropicAutoConfiguration,"
                + "org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration"
})
@Testcontainers
@Transactional
class ChatPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("knowledge_assistant")
                    .withUsername("ka_user")
                    .withPassword("ka_password");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.ai.vectorstore.pgvector.url", postgres::getJdbcUrl);
    }

    @Autowired ChatSessionRepository chatSessionRepository;
    @Autowired ChatMessageRepository chatMessageRepository;

    @Test
    void saveAndFindSession_roundTripsCorrectly() {
        ChatSession session = ChatSession.create("user-1");

        ChatSession saved = chatSessionRepository.save(session);
        Optional<ChatSession> found = chatSessionRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo("user-1");
    }

    @Test
    void findSession_unknownId_returnsEmpty() {
        assertThat(chatSessionRepository.findById(ChatSessionId.generate())).isEmpty();
    }

    @Test
    void saveMessage_withCitations_roundTripsJsonbCorrectly() {
        ChatSession session = chatSessionRepository.save(ChatSession.create("user-1"));
        SourceChunk citation = new SourceChunk(DocumentId.generate(), "Guide", "snippet...", 0.92);

        ChatMessage message = ChatMessage.create(session.id(), ChatRole.ASSISTANT, "the answer", List.of(citation));
        chatMessageRepository.save(message);

        List<ChatMessage> found = chatMessageRepository.findBySessionId(session.id());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).citations()).hasSize(1);
        SourceChunk roundTripped = found.get(0).citations().get(0);
        assertThat(roundTripped.documentId()).isEqualTo(citation.documentId());
        assertThat(roundTripped.documentName()).isEqualTo("Guide");
        assertThat(roundTripped.contentSnippet()).isEqualTo("snippet...");
        assertThat(roundTripped.score()).isEqualTo(0.92);
    }

    @Test
    void saveMessage_userRole_hasNoCitationsAndStoresLowercaseRole() {
        ChatSession session = chatSessionRepository.save(ChatSession.create("user-1"));
        chatMessageRepository.save(ChatMessage.create(session.id(), ChatRole.USER, "question", List.of()));

        List<ChatMessage> found = chatMessageRepository.findBySessionId(session.id());

        assertThat(found.get(0).role()).isEqualTo(ChatRole.USER);
        assertThat(found.get(0).citations()).isEmpty();
    }

    @Test
    void findBySessionId_returnsMessagesInChronologicalOrder() {
        ChatSession session = chatSessionRepository.save(ChatSession.create("user-1"));

        chatMessageRepository.save(ChatMessage.create(session.id(), ChatRole.USER, "first", List.of()));
        chatMessageRepository.save(ChatMessage.create(session.id(), ChatRole.ASSISTANT, "second", List.of()));
        chatMessageRepository.save(ChatMessage.create(session.id(), ChatRole.USER, "third", List.of()));

        List<ChatMessage> found = chatMessageRepository.findBySessionId(session.id());

        assertThat(found).hasSize(3);
        assertThat(found.get(0).content()).isEqualTo("first");
        assertThat(found.get(1).content()).isEqualTo("second");
        assertThat(found.get(2).content()).isEqualTo("third");
    }

    @Test
    void findBySessionId_unknownSession_returnsEmptyList() {
        assertThat(chatMessageRepository.findBySessionId(ChatSessionId.generate())).isEmpty();
    }
}
