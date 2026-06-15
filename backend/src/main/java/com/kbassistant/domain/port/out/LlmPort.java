package com.kbassistant.domain.port.out;

public interface LlmPort {
    String complete(String systemPrompt, String userPrompt);
}
