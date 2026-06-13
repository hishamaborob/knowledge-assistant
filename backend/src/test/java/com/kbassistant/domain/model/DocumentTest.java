package com.kbassistant.domain.model;

import com.kbassistant.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

class DocumentTest {

    @Test
    void createDocument_initialStatusIsPending() {
        var doc = Document.create("Design Doc", "design.pdf", MimeType.PDF, 1024L, "user-1");
        assertThat(doc.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(doc.id()).isNotNull();
        assertThat(doc.createdAt()).isNotNull();
    }

    @Test
    void markStored_transitionsFromPending() {
        var doc = Document.create("Design Doc", "design.pdf", MimeType.PDF, 1024L, "user-1");
        doc.markStored("docs/design.pdf");

        assertThat(doc.status()).isEqualTo(DocumentStatus.STORED);
        assertThat(doc.s3Key()).isEqualTo("docs/design.pdf");
    }

    @Test
    void fullHappyPath_statusTransitionsInOrder() {
        var doc = Document.create("Design Doc", "design.pdf", MimeType.PDF, 1024L, "user-1");

        doc.markStored("s3://bucket/key");
        assertThat(doc.status()).isEqualTo(DocumentStatus.STORED);

        doc.startProcessing();
        assertThat(doc.status()).isEqualTo(DocumentStatus.PROCESSING);

        doc.markReady(42);
        assertThat(doc.status()).isEqualTo(DocumentStatus.READY);
        assertThat(doc.chunkCount()).isEqualTo(42);
    }

    @Test
    void markFailed_fromAnyNonTerminalStatus() {
        var doc = Document.create("Design Doc", "design.pdf", MimeType.PDF, 1024L, "user-1");
        doc.markStored("s3://bucket/key");
        doc.startProcessing();
        doc.markFailed("Embedding API timeout");

        assertThat(doc.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.errorMessage()).contains("Embedding API timeout");
    }

    @Test
    void illegalTransition_throwsInvalidStatusTransitionException() {
        var doc = Document.create("Design Doc", "design.pdf", MimeType.PDF, 1024L, "user-1");

        // Cannot skip STORED and go directly to PROCESSING
        assertThatThrownBy(doc::startProcessing)
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("PROCESSING");
    }

    @Test
    void terminalStatuses_cannotTransitionFurther() {
        var doc = Document.create("Design Doc", "design.pdf", MimeType.PDF, 1024L, "user-1");
        doc.markStored("s3://key");
        doc.startProcessing();
        doc.markReady(10);

        assertThatThrownBy(() -> doc.markFailed("too late"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void markStored_requiresNonNullS3Key() {
        var doc = Document.create("Design Doc", "design.pdf", MimeType.PDF, 1024L, "user-1");
        assertThatNullPointerException().isThrownBy(() -> doc.markStored(null));
    }

    @Test
    void markReady_negativeChunkCount_throws() {
        var doc = Document.create("Design Doc", "design.pdf", MimeType.PDF, 1024L, "user-1");
        doc.markStored("s3://key");
        doc.startProcessing();
        assertThatIllegalArgumentException().isThrownBy(() -> doc.markReady(-1));
    }

    @Test
    void equality_basedOnId() {
        var doc1 = Document.create("Doc A", "a.pdf", MimeType.PDF, 100L, "user-1");
        var doc2 = Document.create("Doc B", "b.pdf", MimeType.PDF, 200L, "user-2");

        assertThat(doc1).isNotEqualTo(doc2);
        assertThat(doc1).isEqualTo(doc1);
    }
}
