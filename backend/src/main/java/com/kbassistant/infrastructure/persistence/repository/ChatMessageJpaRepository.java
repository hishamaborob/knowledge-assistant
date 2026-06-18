package com.kbassistant.infrastructure.persistence.repository;

import com.kbassistant.infrastructure.persistence.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
