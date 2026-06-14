package com.kbassistant.application.service;

import com.kbassistant.application.event.DocumentUploadedEvent;
import com.kbassistant.application.event.TextExtractedEvent;
import com.kbassistant.domain.model.Document;
import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.port.out.DocumentRepository;
import com.kbassistant.domain.port.out.DocumentStorePort;
import com.kbassistant.domain.port.out.TextExtractorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorePort documentStorePort;
    private final TextExtractorPort textExtractorPort;
    private final ApplicationEventPublisher eventPublisher;

    public IngestionService(DocumentRepository documentRepository,
                            DocumentStorePort documentStorePort,
                            TextExtractorPort textExtractorPort,
                            ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.documentStorePort = documentStorePort;
        this.textExtractorPort = textExtractorPort;
        this.eventPublisher = eventPublisher;
    }

    // AFTER_COMMIT: guarantees the document's STORED status is visible before this runs.
    // @Async: runs on the ingestionTaskExecutor virtual thread pool, freeing the HTTP thread.
    // REQUIRES_NEW: Spring 6.2+ requires @TransactionalEventListener + @Transactional to use
    //   REQUIRES_NEW or NOT_SUPPORTED. REQUIRES_NEW is correct here — we explicitly want a
    //   fresh transaction that is independent of the (already committed) upload transaction.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("ingestionTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleDocumentUploaded(DocumentUploadedEvent event) {
        DocumentId documentId = event.documentId();
        log.info("Starting ingestion for document {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Document disappeared before ingestion could start: " + documentId));

        try {
            document.startProcessing();
            documentRepository.save(document);

            byte[] content = documentStorePort.retrieve(event.s3Key());
            String extractedText = textExtractorPort.extract(content, document.mimeType());

            log.info("Extracted {} chars from document {}", extractedText.length(), documentId);

            // Phase 4 adds a handler for TextExtractedEvent that chunks + embeds.
            eventPublisher.publishEvent(new TextExtractedEvent(this, documentId, extractedText));

        } catch (Exception e) {
            log.error("Ingestion failed for document {}: {}", documentId, e.getMessage(), e);
            document.markFailed(e.getMessage());
            documentRepository.save(document);
        }
    }
}
