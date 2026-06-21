package com.kbassistant.application.service;

import com.kbassistant.application.event.TextExtractedEvent;
import com.kbassistant.domain.model.Document;
import com.kbassistant.domain.model.DocumentStatus;
import com.kbassistant.domain.model.MimeType;
import com.kbassistant.domain.port.out.DocumentRepository;
import com.kbassistant.domain.port.out.EmbeddingPort;
import com.kbassistant.domain.port.out.VectorStorePort;
import com.kbassistant.domain.service.FixedSizeChunker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock EmbeddingPort embeddingPort;
    @Mock VectorStorePort vectorStorePort;

    // Real chunker: chunkSize=5 words, overlap=1
    FixedSizeChunker chunker = new FixedSizeChunker(5, 1);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(
                documentRepository, embeddingPort, vectorStorePort, chunker, meterRegistry);
    }

    @Test
    void handleTextExtracted_successPath_savesChunksAndMarksReady() {
        Document doc = processingDocument();
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 6-word text → 2 chunks with chunkSize=5, overlap=1
        String text = "word1 word2 word3 word4 word5 word6";
        float[] embedding = new float[768];
        when(embeddingPort.embed(anyList())).thenReturn(List.of(embedding, embedding));

        TextExtractedEvent event = new TextExtractedEvent(this, doc.id(), text);
        embeddingService.handleTextExtracted(event);

        verify(vectorStorePort).saveChunks(argThat(chunks -> chunks.size() == 2));
        verify(documentRepository, atLeastOnce()).save(any(Document.class));
        assertThat(doc.status()).isEqualTo(DocumentStatus.READY);
        assertThat(doc.chunkCount()).isEqualTo(2);
    }

    @Test
    void handleTextExtracted_successPath_recordsEmbeddingTimer() {
        Document doc = processingDocument();
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 6-word text → 2 chunks with chunkSize=5, overlap=1
        String text = "word1 word2 word3 word4 word5 word6";
        float[] embedding = new float[768];
        when(embeddingPort.embed(anyList())).thenReturn(List.of(embedding, embedding));

        TextExtractedEvent event = new TextExtractedEvent(this, doc.id(), text);
        embeddingService.handleTextExtracted(event);

        // Timer recorded with batch_size tag matching the chunk count
        assertThat(meterRegistry.timer("embedding.generation.duration", "batch_size", "2").count())
                .isEqualTo(1);
    }

    @Test
    void handleTextExtracted_emptyText_marksReadyWithZeroChunks() {
        Document doc = processingDocument();
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        embeddingService.handleTextExtracted(new TextExtractedEvent(this, doc.id(), "   "));

        verifyNoInteractions(embeddingPort);
        verifyNoInteractions(vectorStorePort);
        assertThat(doc.status()).isEqualTo(DocumentStatus.READY);
        assertThat(doc.chunkCount()).isEqualTo(0);
    }

    @Test
    void handleTextExtracted_embeddingFails_marksDocumentFailed() {
        Document doc = processingDocument();
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(embeddingPort.embed(anyList())).thenThrow(new RuntimeException("Ollama timeout"));

        String text = "word1 word2 word3 word4 word5";
        embeddingService.handleTextExtracted(new TextExtractedEvent(this, doc.id(), text));

        assertThat(doc.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.errorMessage()).contains("Ollama timeout");
    }

    @Test
    void handleTextExtracted_documentNotFound_gracefullyReturns() {
        Document doc = processingDocument();
        when(documentRepository.findById(doc.id())).thenReturn(Optional.empty());

        // Should not throw, should not call embedding port
        embeddingService.handleTextExtracted(
                new TextExtractedEvent(this, doc.id(), "some text"));

        verifyNoInteractions(embeddingPort);
        verifyNoInteractions(vectorStorePort);
    }

    private Document processingDocument() {
        Document doc = Document.create("doc", "doc.txt", MimeType.TEXT, 100L, "user");
        doc.markStored("s3://key");
        doc.startProcessing();
        return doc;
    }
}
