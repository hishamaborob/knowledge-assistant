package com.kbassistant.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    /**
     * Stored lowercase ("user" | "assistant") to match the convention documented
     * in V4__chat.sql's comment. That migration is already applied and checksummed
     * by Flyway (validate-on-migrate: true) — it cannot be edited after the fact,
     * so the adapter maps ChatRole.USER/ASSISTANT to lowercase strings explicitly,
     * unlike DocumentStatus which stores uppercase enum names.
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations", columnDefinition = "jsonb")
    private List<CitationRecord> citations;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId()                  { return id; }
    public void setId(UUID id)           { this.id = id; }

    public UUID getSessionId()                   { return sessionId; }
    public void setSessionId(UUID sessionId)     { this.sessionId = sessionId; }

    public String getRole()              { return role; }
    public void setRole(String role)     { this.role = role; }

    public String getContent()               { return content; }
    public void setContent(String content)   { this.content = content; }

    public List<CitationRecord> getCitations()                  { return citations; }
    public void setCitations(List<CitationRecord> citations)    { this.citations = citations; }

    public Integer getPromptTokens()                 { return promptTokens; }
    public void setPromptTokens(Integer promptTokens){ this.promptTokens = promptTokens; }

    public Integer getCompletionTokens()                     { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens){ this.completionTokens = completionTokens; }

    public String getModelUsed()                 { return modelUsed; }
    public void setModelUsed(String modelUsed)   { this.modelUsed = modelUsed; }

    public Instant getCreatedAt()                { return createdAt; }
    public void setCreatedAt(Instant createdAt)  { this.createdAt = createdAt; }
}
