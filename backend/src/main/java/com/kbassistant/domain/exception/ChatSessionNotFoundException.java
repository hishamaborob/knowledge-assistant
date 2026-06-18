package com.kbassistant.domain.exception;

import com.kbassistant.domain.model.ChatSessionId;

public class ChatSessionNotFoundException extends RuntimeException {

    public ChatSessionNotFoundException(ChatSessionId id) {
        super("Chat session not found: " + id);
    }
}
