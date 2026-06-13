package com.kbassistant.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class DocumentId {

    private final UUID value;

    private DocumentId(UUID value) {
        this.value = Objects.requireNonNull(value, "DocumentId value must not be null");
    }

    public static DocumentId of(UUID value) {
        return new DocumentId(value);
    }

    public static DocumentId generate() {
        return new DocumentId(UUID.randomUUID());
    }

    public static DocumentId from(String value) {
        return new DocumentId(UUID.fromString(value));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
