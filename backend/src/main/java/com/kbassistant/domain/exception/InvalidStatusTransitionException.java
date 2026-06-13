package com.kbassistant.domain.exception;

import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.DocumentStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(DocumentId id,
                                            DocumentStatus from,
                                            DocumentStatus to) {
        super("Document %s cannot transition from %s to %s".formatted(id, from, to));
    }
}
