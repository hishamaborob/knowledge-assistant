package com.kbassistant.application.service;

import com.kbassistant.application.command.UploadDocumentCommand;
import com.kbassistant.application.event.DocumentUploadedEvent;
import com.kbassistant.domain.exception.DocumentNotFoundException;
import com.kbassistant.domain.model.Document;
import com.kbassistant.domain.model.DocumentStatus;
import com.kbassistant.domain.model.MimeType;
import com.kbassistant.domain.port.out.DocumentRepository;
import com.kbassistant.domain.port.out.DocumentStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock DocumentStorePort documentStorePort;
    @Mock ApplicationEventPublisher eventPublisher;

    DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, documentStorePort, eventPublisher);
    }

    @Test
    void upload_storesFileAndSavesDocumentWithStoredStatus() {
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentStorePort.store(any(), any(), any(), any()))
                .thenReturn("documents/some-id/report.pdf");

        UploadDocumentCommand command = new UploadDocumentCommand(
                "Report", "report.pdf", "pdf bytes".getBytes(), MimeType.PDF, 9L, "user-1");

        Document result = documentService.upload(command);

        assertThat(result.status()).isEqualTo(DocumentStatus.STORED);
        assertThat(result.s3Key()).isEqualTo("documents/some-id/report.pdf");
        assertThat(result.name()).isEqualTo("Report");
    }

    @Test
    void upload_publishesDocumentUploadedEvent() {
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentStorePort.store(any(), any(), any(), any())).thenReturn("s3://key");

        documentService.upload(new UploadDocumentCommand(
                "Doc", "doc.txt", "text".getBytes(), MimeType.TEXT, 4L, "user"));

        ArgumentCaptor<DocumentUploadedEvent> captor = ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().s3Key()).isEqualTo("s3://key");
    }

    @Test
    void upload_savesDocumentTwice_pendingThenStored() {
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentStorePort.store(any(), any(), any(), any())).thenReturn("key");

        documentService.upload(new UploadDocumentCommand(
                "Doc", "doc.txt", new byte[0], MimeType.TEXT, 0L, "user"));

        // First save: PENDING (persists before S3 upload)
        // Second save: STORED (persists after S3 upload with key)
        verify(documentRepository, times(2)).save(any(Document.class));
    }

    @Test
    void findById_delegatesToRepository() {
        Document doc = Document.create("name", "f.txt", MimeType.TEXT, 10L, "u");
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));

        assertThat(documentService.findById(doc.id())).isSameAs(doc);
    }

    @Test
    void findById_unknownId_throwsDocumentNotFoundException() {
        Document doc = Document.create("name", "f.txt", MimeType.TEXT, 10L, "u");
        when(documentRepository.findById(doc.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.findById(doc.id()))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void findAll_delegatesToRepository() {
        Document d1 = Document.create("a", "a.txt", MimeType.TEXT, 1L, "u");
        Document d2 = Document.create("b", "b.pdf", MimeType.PDF, 2L, "u");
        when(documentRepository.findAll()).thenReturn(List.of(d1, d2));

        assertThat(documentService.findAll()).hasSize(2);
    }
}
