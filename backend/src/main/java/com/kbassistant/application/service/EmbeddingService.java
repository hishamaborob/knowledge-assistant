package com.kbassistant.application.service;

import com.kbassistant.application.event.TextExtractedEvent;
import com.kbassistant.domain.model.DocumentChunk;
import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.Document;
import com.kbassistant.domain.port.out.DocumentRepository;
import com.kbassistant.domain.port.out.EmbeddingPort;
import com.kbassistant.domain.port.out.VectorStorePort;
import com.kbassistant.domain.service.FixedSizeChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final DocumentRepository documentRepository;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final FixedSizeChunker chunker;

    public EmbeddingService(DocumentRepository documentRepository,
                            EmbeddingPort embeddingPort,
                            VectorStorePort vectorStorePort,
                            FixedSizeChunker chunker) {
        this.documentRepository = documentRepository;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.chunker = chunker;
    }

    // AFTER_COMMIT: the document is in PROCESSING state (committed by IngestionService)
    //   before this handler fires.
    // REQUIRES_NEW: Spring 6.2 requires this when combining @TransactionalEventListener
    //   with @Transactional. Correct semantic — we want a brand-new transaction here.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("ingestionTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTextExtracted(TextExtractedEvent event) {
        DocumentId documentId = event.documentId();
        log.info("Starting embedding pipeline for document {}", documentId);

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.error("Document {} disappeared before embedding — skipping", documentId);
            return;
        }

        try {
            List<String> chunkTexts = chunker.chunk(event.extractedText());
            log.info("Split document {} into {} chunks", documentId, chunkTexts.size());

            if (chunkTexts.isEmpty()) {
                document.markReady(0);
                documentRepository.save(document);
                log.info("Document {} marked READY (empty content, 0 chunks)", documentId);
                return;
            }

            List<float[]> embeddings = embeddingPort.embed(chunkTexts);

            List<DocumentChunk> chunks = new ArrayList<>(chunkTexts.size());
            for (int i = 0; i < chunkTexts.size(); i++) {
                String text = chunkTexts.get(i);
                chunks.add(DocumentChunk.create(
                        documentId,
                        i,
                        text,
                        embeddings.get(i),
                        countWords(text)
                ));
            }

            vectorStorePort.saveChunks(chunks);
            document.markReady(chunks.size());
            documentRepository.save(document);

            log.info("Document {} is READY — {} chunks embedded", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Embedding pipeline failed for document {}: {}", documentId, e.getMessage(), e);
            document.markFailed(e.getMessage());
            documentRepository.save(document);
        }
    }

    private int countWords(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }
}
