package com.kbassistant.domain.port.out;

import com.kbassistant.domain.model.ChatTurn;

import java.util.List;

public interface LlmPort {

    default String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, List.of(), userPrompt);
    }

    String complete(String systemPrompt, List<ChatTurn> history, String userPrompt);
}
