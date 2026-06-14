package com.kbassistant.domain.model;

import com.kbassistant.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DocumentStatusTransitionTest {

    @Test
    void happyPath_pendingToReadyViaAllSteps() {
        Document doc = Document.create("Doc", "file.txt", MimeType.TEXT, 100L, "user");
        assertThat(doc.status()).isEqualTo(DocumentStatus.PENDING);

        doc.markStored("s3://bucket/key");
        assertThat(doc.status()).isEqualTo(DocumentStatus.STORED);

        doc.startProcessing();
        assertThat(doc.status()).isEqualTo(DocumentStatus.PROCESSING);

        doc.markReady(5);
        assertThat(doc.status()).isEqualTo(DocumentStatus.READY);
        assertThat(doc.chunkCount()).isEqualTo(5);
    }

    @Test
    void markFailed_fromPending() {
        Document doc = Document.create("Doc", "file.txt", MimeType.TEXT, 100L, "user");
        doc.markFailed("network timeout");
        assertThat(doc.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.errorMessage()).isEqualTo("network timeout");
    }

    @Test
    void markFailed_fromStored() {
        Document doc = Document.create("Doc", "file.txt", MimeType.TEXT, 100L, "user");
        doc.markStored("s3://key");
        doc.markFailed("extraction failed");
        assertThat(doc.status()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void markFailed_fromProcessing() {
        Document doc = Document.create("Doc", "file.txt", MimeType.TEXT, 100L, "user");
        doc.markStored("s3://key");
        doc.startProcessing();
        doc.markFailed("embedding API 503");
        assertThat(doc.status()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void invalidTransition_pendingToProcessing_throws() {
        Document doc = Document.create("Doc", "file.txt", MimeType.TEXT, 100L, "user");
        assertThatThrownBy(doc::startProcessing)
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("PROCESSING");
    }

    @Test
    void invalidTransition_storedToReady_throws() {
        Document doc = Document.create("Doc", "file.txt", MimeType.TEXT, 100L, "user");
        doc.markStored("s3://key");
        assertThatThrownBy(() -> doc.markReady(0))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void terminalStates_cannotTransition() {
        Document ready = buildReadyDoc();
        assertThat(ready.status().isTerminal()).isTrue();
        assertThatThrownBy(() -> ready.markStored("key"))
                .isInstanceOf(InvalidStatusTransitionException.class);

        Document failed = Document.create("f", "f.txt", MimeType.TEXT, 1L, "u");
        failed.markFailed("err");
        assertThat(failed.status().isTerminal()).isTrue();
        assertThatThrownBy(() -> failed.markStored("key"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void markReady_negativeChunkCount_throws() {
        Document doc = buildProcessingDoc();
        assertThatThrownBy(() -> doc.markReady(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Document buildReadyDoc() {
        Document doc = Document.create("r", "r.txt", MimeType.TEXT, 10L, "u");
        doc.markStored("s3://key");
        doc.startProcessing();
        doc.markReady(3);
        return doc;
    }

    private Document buildProcessingDoc() {
        Document doc = Document.create("p", "p.txt", MimeType.TEXT, 10L, "u");
        doc.markStored("s3://key");
        doc.startProcessing();
        return doc;
    }
}
