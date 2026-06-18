package com.kbassistant.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class ChatSessionId {

    private final UUID value;

    private ChatSessionId(UUID value) {
        this.value = Objects.requireNonNull(value, "ChatSessionId value must not be null");
    }

    public static ChatSessionId of(UUID value)     { return new ChatSessionId(value); }
    public static ChatSessionId generate()         { return new ChatSessionId(UUID.randomUUID()); }
    public static ChatSessionId from(String value) { return new ChatSessionId(UUID.fromString(value)); }

    public UUID value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatSessionId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return value.toString(); }
}
