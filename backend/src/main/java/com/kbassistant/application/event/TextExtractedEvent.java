package com.kbassistant.application.event;

import com.kbassistant.domain.model.DocumentId;
import org.springframework.context.ApplicationEvent;

public class TextExtractedEvent extends ApplicationEvent {

    private final DocumentId documentId;
    private final String extractedText;

    public TextExtractedEvent(Object source, DocumentId documentId, String extractedText) {
        super(source);
        this.documentId = documentId;
        this.extractedText = extractedText;
    }

    public DocumentId documentId() { return documentId; }
    public String extractedText() { return extractedText; }
}
