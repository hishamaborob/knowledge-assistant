package com.kbassistant.application.event;

import com.kbassistant.domain.model.DocumentId;
import org.springframework.context.ApplicationEvent;

public class DocumentUploadedEvent extends ApplicationEvent {

    private final DocumentId documentId;
    private final String s3Key;

    public DocumentUploadedEvent(Object source, DocumentId documentId, String s3Key) {
        super(source);
        this.documentId = documentId;
        this.s3Key = s3Key;
    }

    public DocumentId documentId() { return documentId; }
    public String s3Key() { return s3Key; }
}
