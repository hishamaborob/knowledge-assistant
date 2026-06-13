package com.kbassistant.domain.model;

import java.util.Set;

public enum DocumentStatus {
    PENDING,     // created, not yet uploaded to S3
    STORED,      // raw file is in S3, ingestion not started
    PROCESSING,  // chunking + embedding in progress
    READY,       // fully ingested, available for search
    FAILED;      // ingestion failed, see errorMessage on Document

    private static final Set<DocumentStatus> TERMINAL = Set.of(READY, FAILED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(DocumentStatus next) {
        return switch (this) {
            case PENDING    -> next == STORED || next == FAILED;
            case STORED     -> next == PROCESSING || next == FAILED;
            case PROCESSING -> next == READY || next == FAILED;
            case READY, FAILED -> false;
        };
    }
}
