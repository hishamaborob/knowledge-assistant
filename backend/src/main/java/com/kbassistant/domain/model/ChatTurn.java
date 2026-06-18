package com.kbassistant.domain.model;

/**
 * A single prior turn fed to the LLM as conversation history.
 * Transient projection of a persisted ChatMessage — carries only what
 * prompt construction needs, not citations or token usage.
 */
public record ChatTurn(ChatRole role, String content) {}
