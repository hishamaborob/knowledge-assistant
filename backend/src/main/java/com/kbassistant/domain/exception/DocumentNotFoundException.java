package com.kbassistant.domain.exception;

import com.kbassistant.domain.model.DocumentId;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(DocumentId id) {
        super("Document not found: " + id);
    }
}
