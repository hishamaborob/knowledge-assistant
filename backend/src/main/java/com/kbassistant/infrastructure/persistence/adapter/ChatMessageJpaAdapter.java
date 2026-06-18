package com.kbassistant.infrastructure.persistence.adapter;

import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.ChatMessageRepository;
import com.kbassistant.infrastructure.persistence.entity.ChatMessageEntity;
import com.kbassistant.infrastructure.persistence.entity.CitationRecord;
import com.kbassistant.infrastructure.persistence.repository.ChatMessageJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@Transactional
public class ChatMessageJpaAdapter implements ChatMessageRepository {

    private final ChatMessageJpaRepository jpaRepository;

    public ChatMessageJpaAdapter(ChatMessageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        ChatMessageEntity entity = toEntity(message);
        ChatMessageEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> findBySessionId(ChatSessionId sessionId) {
        return jpaRepository.findBySessionIdOrderByCreatedAtAsc(sessionId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    // =========================================================================
    // Mapping — domain ↔ entity
    // =========================================================================

    private ChatMessage toDomain(ChatMessageEntity e) {
        List<SourceChunk> citations = e.getCitations() == null ? List.of()
                : e.getCitations().stream()
                        .map(c -> new SourceChunk(
                                DocumentId.from(c.documentId()),
                                c.documentName(),
                                c.contentSnippet(),
                                c.score()))
                        .toList();

        return ChatMessage.reconstitute(
                ChatMessageId.of(e.getId()),
                ChatSessionId.of(e.getSessionId()),
                toRole(e.getRole()),
                e.getContent(),
                citations,
                e.getPromptTokens(),
                e.getCompletionTokens(),
                e.getModelUsed(),
                e.getCreatedAt()
        );
    }

    private ChatMessageEntity toEntity(ChatMessage m) {
        var e = new ChatMessageEntity();
        e.setId(m.id().value());
        e.setSessionId(m.sessionId().value());
        e.setRole(fromRole(m.role()));
        e.setContent(m.content());
        e.setCitations(m.citations().stream()
                .map(c -> new CitationRecord(
                        c.documentId().value().toString(),
                        c.documentName(),
                        c.contentSnippet(),
                        c.score()))
                .toList());
        e.setPromptTokens(m.promptTokens());
        e.setCompletionTokens(m.completionTokens());
        e.setModelUsed(m.modelUsed());
        e.setCreatedAt(m.createdAt());
        return e;
    }

    private ChatRole toRole(String value) {
        return "assistant".equals(value) ? ChatRole.ASSISTANT : ChatRole.USER;
    }

    private String fromRole(ChatRole role) {
        return role == ChatRole.ASSISTANT ? "assistant" : "user";
    }
}
