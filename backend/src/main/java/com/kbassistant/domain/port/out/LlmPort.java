package com.kbassistant.domain.port.out;

import com.kbassistant.domain.model.ChatTurn;
import com.kbassistant.domain.model.LlmResponse;

import java.util.List;

public interface LlmPort {

    default LlmResponse complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, List.of(), userPrompt);
    }

    LlmResponse complete(String systemPrompt, List<ChatTurn> history, String userPrompt);
}
