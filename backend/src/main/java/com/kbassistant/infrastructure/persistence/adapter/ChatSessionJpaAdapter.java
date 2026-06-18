package com.kbassistant.infrastructure.persistence.adapter;

import com.kbassistant.domain.model.ChatSession;
import com.kbassistant.domain.model.ChatSessionId;
import com.kbassistant.domain.port.out.ChatSessionRepository;
import com.kbassistant.infrastructure.persistence.entity.ChatSessionEntity;
import com.kbassistant.infrastructure.persistence.repository.ChatSessionJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional
public class ChatSessionJpaAdapter implements ChatSessionRepository {

    private final ChatSessionJpaRepository jpaRepository;

    public ChatSessionJpaAdapter(ChatSessionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ChatSession save(ChatSession session) {
        ChatSessionEntity entity = toEntity(session);
        ChatSessionEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatSession> findById(ChatSessionId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    private ChatSession toDomain(ChatSessionEntity e) {
        return ChatSession.reconstitute(ChatSessionId.of(e.getId()), e.getUserId(), e.getCreatedAt());
    }

    private ChatSessionEntity toEntity(ChatSession s) {
        var e = new ChatSessionEntity();
        e.setId(s.id().value());
        e.setUserId(s.userId());
        e.setCreatedAt(s.createdAt());
        return e;
    }
}
