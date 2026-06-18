package com.kbassistant.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A single turn in a chat session. Queried independently from ChatSession,
 * same precedent as DocumentChunk vs. Document.
 *
 * promptTokens/completionTokens/modelUsed are always null in this phase —
 * the schema carries the columns, but LlmPort still returns a plain String
 * with no usage metadata. Populating them is deferred to the Observability
 * phase, which needs the same data for Micrometer instrumentation anyway.
 */
public class ChatMessage {

    private ChatMessageId id;
    private ChatSessionId sessionId;
    private ChatRole role;
    private String content;
    private List<SourceChunk> citations;
    private Integer promptTokens;
    private Integer completionTokens;
    private String modelUsed;
    private Instant createdAt;

    private ChatMessage() {}

    public static ChatMessage create(ChatSessionId sessionId, ChatRole role, String content, List<SourceChunk> citations) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");

        var message = new ChatMessage();
        message.id = ChatMessageId.generate();
        message.sessionId = sessionId;
        message.role = role;
        message.content = content;
        message.citations = citations != null ? citations : List.of();
        message.createdAt = Instant.now();
        return message;
    }

    public static ChatMessage reconstitute(ChatMessageId id,
                                           ChatSessionId sessionId,
                                           ChatRole role,
                                           String content,
                                           List<SourceChunk> citations,
                                           Integer promptTokens,
                                           Integer completionTokens,
                                           String modelUsed,
                                           Instant createdAt) {
        var message = new ChatMessage();
        message.id = id;
        message.sessionId = sessionId;
        message.role = role;
        message.content = content;
        message.citations = citations != null ? citations : List.of();
        message.promptTokens = promptTokens;
        message.completionTokens = completionTokens;
        message.modelUsed = modelUsed;
        message.createdAt = createdAt;
        return message;
    }

    public ChatMessageId id()              { return id; }
    public ChatSessionId sessionId()       { return sessionId; }
    public ChatRole role()                 { return role; }
    public String content()                { return content; }
    public List<SourceChunk> citations()   { return citations; }
    public Integer promptTokens()          { return promptTokens; }
    public Integer completionTokens()      { return completionTokens; }
    public String modelUsed()              { return modelUsed; }
    public Instant createdAt()             { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
