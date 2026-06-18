package com.kbassistant.infrastructure.persistence.repository;

import com.kbassistant.infrastructure.persistence.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, UUID> {
}
