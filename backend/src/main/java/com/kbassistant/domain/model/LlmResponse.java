package com.kbassistant.domain.model;

public record LlmResponse(
        String content,
        int promptTokens,
        int completionTokens,
        String modelUsed
) {}
