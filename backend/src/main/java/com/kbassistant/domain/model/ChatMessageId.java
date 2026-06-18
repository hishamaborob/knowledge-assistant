package com.kbassistant.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class ChatMessageId {

    private final UUID value;

    private ChatMessageId(UUID value) {
        this.value = Objects.requireNonNull(value, "ChatMessageId value must not be null");
    }

    public static ChatMessageId of(UUID value)     { return new ChatMessageId(value); }
    public static ChatMessageId generate()         { return new ChatMessageId(UUID.randomUUID()); }
    public static ChatMessageId from(String value) { return new ChatMessageId(UUID.fromString(value)); }

    public UUID value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessageId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return value.toString(); }
}
