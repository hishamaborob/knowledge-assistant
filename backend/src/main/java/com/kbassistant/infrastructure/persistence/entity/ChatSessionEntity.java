package com.kbassistant.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions")
public class ChatSessionEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId()                  { return id; }
    public void setId(UUID id)           { this.id = id; }

    public String getUserId()                { return userId; }
    public void setUserId(String userId)     { this.userId = userId; }

    public Instant getCreatedAt()                { return createdAt; }
    public void setCreatedAt(Instant createdAt)  { this.createdAt = createdAt; }
}
