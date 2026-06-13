package com.kbassistant.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class ChunkId {

    private final UUID value;

    private ChunkId(UUID value) {
        this.value = Objects.requireNonNull(value);
    }

    public static ChunkId of(UUID value)    { return new ChunkId(value); }
    public static ChunkId generate()        { return new ChunkId(UUID.randomUUID()); }
    public static ChunkId from(String value){ return new ChunkId(UUID.fromString(value)); }

    public UUID value() { return value; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof ChunkId that)) return false;
        return Objects.equals(value, that.value);
    }
    @Override public int hashCode()    { return Objects.hash(value); }
    @Override public String toString() { return value.toString(); }
}
