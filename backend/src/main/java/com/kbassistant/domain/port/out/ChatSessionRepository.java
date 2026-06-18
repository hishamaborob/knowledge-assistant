package com.kbassistant.domain.port.out;

import com.kbassistant.domain.model.ChatSession;
import com.kbassistant.domain.model.ChatSessionId;

import java.util.Optional;

public interface ChatSessionRepository {

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(ChatSessionId id);
}
