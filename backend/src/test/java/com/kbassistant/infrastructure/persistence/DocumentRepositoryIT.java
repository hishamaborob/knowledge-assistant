package com.kbassistant.infrastructure.persistence;

import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=sk-test",
        "spring.ai.anthropic.api-key=test",
        "spring.ai.vertex.ai.gemini.project-id=test",
        "spring.autoconfigure.exclude="
                + "org.springframework.ai.autoconfigure.vertexai.gemini.VertexAiGeminiAutoConfiguration,"
                + "org.springframework.ai.autoconfigure.anthropic.AnthropicAutoConfiguration"
})
@Testcontainers
@Transactional
class DocumentRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("knowledge_assistant")
                    .withUsername("ka_user")
                    .withPassword("ka_password");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.ai.vectorstore.pgvector.url", postgres::getJdbcUrl);
    }

    @Autowired
    DocumentRepository documentRepository;

    @Test
    void saveAndFindById_roundTripsCorrectly() {
        Document doc = Document.create("Design Doc", "design.pdf", MimeType.PDF, 4096L, "user-1");

        Document saved = documentRepository.save(doc);
        Optional<Document> found = documentRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Design Doc");
        assertThat(found.get().status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(found.get().mimeType()).isEqualTo(MimeType.PDF);
    }

    @Test
    void statusTransitions_persistedCorrectly() {
        Document doc = Document.create("Report", "report.pdf", MimeType.PDF, 1024L, "user-1");
        documentRepository.save(doc);

        doc.markStored("s3://bucket/report.pdf");
        documentRepository.save(doc);

        Optional<Document> found = documentRepository.findById(doc.id());
        assertThat(found.get().status()).isEqualTo(DocumentStatus.STORED);
        assertThat(found.get().s3Key()).isEqualTo("s3://bucket/report.pdf");
    }

    @Test
    void findAll_returnsAllDocuments() {
        documentRepository.save(Document.create("Doc A", "a.pdf", MimeType.PDF, 100L, "user-1"));
        documentRepository.save(Document.create("Doc B", "b.txt", MimeType.TEXT, 200L, "user-1"));

        assertThat(documentRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void delete_removesDocument() {
        Document doc = documentRepository.save(
                Document.create("To Delete", "delete.md", MimeType.MARKDOWN, 50L, "user-1"));

        documentRepository.delete(doc.id());

        assertThat(documentRepository.findById(doc.id())).isEmpty();
    }

    @Test
    void delete_nonExistentDocument_throwsNotFoundException() {
        DocumentId nonExistent = DocumentId.generate();
        assertThatThrownBy(() -> documentRepository.delete(nonExistent))
                .hasMessageContaining(nonExistent.toString());
    }
}
