package com.kbassistant.domain.port.out;

import com.kbassistant.domain.model.ChatMessage;
import com.kbassistant.domain.model.ChatSessionId;

import java.util.List;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    /** Ordered ascending by createdAt — oldest first. */
    List<ChatMessage> findBySessionId(ChatSessionId sessionId);
}
