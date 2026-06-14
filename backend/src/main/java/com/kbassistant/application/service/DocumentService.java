package com.kbassistant.application.service;

import com.kbassistant.application.command.UploadDocumentCommand;
import com.kbassistant.application.event.DocumentUploadedEvent;
import com.kbassistant.domain.exception.DocumentNotFoundException;
import com.kbassistant.domain.model.Document;
import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.port.out.DocumentRepository;
import com.kbassistant.domain.port.out.DocumentStorePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorePort documentStorePort;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentStorePort documentStorePort,
                           ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.documentStorePort = documentStorePort;
        this.eventPublisher = eventPublisher;
    }

    public Document upload(UploadDocumentCommand command) {
        Document document = Document.create(
                command.name(),
                command.originalFilename(),
                command.mimeType(),
                command.fileSizeBytes(),
                command.uploadedBy()
        );
        documentRepository.save(document);

        String s3Key = documentStorePort.store(
                document.id(),
                command.content(),
                command.originalFilename(),
                command.mimeType()
        );

        document.markStored(s3Key);
        documentRepository.save(document);

        // TransactionalEventListener in IngestionService fires after this transaction commits,
        // so the STORED status is visible to the async handler when it loads the document.
        eventPublisher.publishEvent(new DocumentUploadedEvent(this, document.id(), s3Key));

        return document;
    }

    @Transactional(readOnly = true)
    public Document findById(DocumentId id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Document> findAll() {
        return documentRepository.findAll();
    }

    public void delete(DocumentId id) {
        Document document = findById(id);
        if (document.s3Key() != null) {
            documentStorePort.delete(document.s3Key());
        }
        documentRepository.delete(id);
    }
}
