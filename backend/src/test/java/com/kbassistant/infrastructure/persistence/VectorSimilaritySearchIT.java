package com.kbassistant.infrastructure.persistence;

import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.DocumentRepository;
import com.kbassistant.domain.port.out.VectorStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Proves that pgvector's HNSW index returns results in correct cosine similarity order.
 *
 * Test vectors use standard basis vectors (1.0 in one dimension, 0.0 elsewhere).
 * Cosine similarity between standard basis vectors:
 *   - same vector: 1.0  (identical direction)
 *   - different vectors: 0.0  (orthogonal)
 *
 * Query vector = e₁ (1.0 in dim 0) → chunk with e₁ embedding should rank first.
 */
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
class VectorSimilaritySearchIT {

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

    @Autowired DocumentRepository documentRepository;
    @Autowired VectorStorePort vectorStorePort;

    Document testDocument;

    @BeforeEach
    void setUp() {
        testDocument = documentRepository.save(
                Document.create("Test Doc", "test.txt", MimeType.TEXT, 100L, "test-user"));
    }

    @Test
    void similaritySearch_returnsChunksInScoreOrder() {
        // Three orthogonal unit vectors in 1536-dimensional space
        float[] v0 = basisVector(0);   // [1,0,0,...,0]
        float[] v1 = basisVector(1);   // [0,1,0,...,0]
        float[] v2 = basisVector(2);   // [0,0,1,...,0]

        vectorStorePort.saveChunks(List.of(
                DocumentChunk.create(testDocument.id(), 0, "Content about topic A", v0, 10),
                DocumentChunk.create(testDocument.id(), 1, "Content about topic B", v1, 10),
                DocumentChunk.create(testDocument.id(), 2, "Content about topic C", v2, 10)
        ));

        // Query with v0 — chunk 0 should be first (score=1.0), others should be filtered out
        SimilaritySearchRequest request = SimilaritySearchRequest.builder()
                .queryVector(v0)
                .topK(3)
                .similarityThreshold(0.5)   // lower threshold to see all results
                .build();

        List<ScoredChunk> results = vectorStorePort.similaritySearch(request);

        assertThat(results).isNotEmpty();
        // First result should be the chunk with v0 embedding (perfect match)
        assertThat(results.get(0).chunk().content()).isEqualTo("Content about topic A");
        assertThat(results.get(0).score()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void similaritySearch_thresholdFiltersLowScoreResults() {
        float[] v0 = basisVector(0);
        float[] v1 = basisVector(1);

        vectorStorePort.saveChunks(List.of(
                DocumentChunk.create(testDocument.id(), 0, "Relevant content", v0, 5),
                DocumentChunk.create(testDocument.id(), 1, "Unrelated content", v1, 5)
        ));

        // High threshold — only near-identical vectors should pass
        SimilaritySearchRequest request = SimilaritySearchRequest.builder()
                .queryVector(v0)
                .topK(10)
                .similarityThreshold(0.9)
                .build();

        List<ScoredChunk> results = vectorStorePort.similaritySearch(request);

        // Only chunk 0 (score=1.0) should pass threshold 0.9
        // Chunk 1 is orthogonal to v0 (score≈0.0)
        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunk().content()).isEqualTo("Relevant content");
    }

    @Test
    void similaritySearch_withDocumentFilter_onlySearchesSpecifiedDocument() {
        Document otherDoc = documentRepository.save(
                Document.create("Other Doc", "other.txt", MimeType.TEXT, 50L, "test-user"));

        float[] v0 = basisVector(0);

        // Same vector in both documents
        vectorStorePort.saveChunks(List.of(
                DocumentChunk.create(testDocument.id(), 0, "From test doc", v0, 5)
        ));
        vectorStorePort.saveChunks(List.of(
                DocumentChunk.create(otherDoc.id(), 0, "From other doc", v0, 5)
        ));

        SimilaritySearchRequest request = SimilaritySearchRequest.builder()
                .queryVector(v0)
                .topK(10)
                .similarityThreshold(0.5)
                .documentIds(List.of(testDocument.id()))  // filter to testDocument only
                .build();

        List<ScoredChunk> results = vectorStorePort.similaritySearch(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunk().content()).isEqualTo("From test doc");
    }

    @Test
    void countByDocumentId_reflectsInsertedChunks() {
        vectorStorePort.saveChunks(List.of(
                DocumentChunk.create(testDocument.id(), 0, "Chunk 0", basisVector(0), 5),
                DocumentChunk.create(testDocument.id(), 1, "Chunk 1", basisVector(1), 5),
                DocumentChunk.create(testDocument.id(), 2, "Chunk 2", basisVector(2), 5)
        ));

        assertThat(vectorStorePort.countByDocumentId(testDocument.id())).isEqualTo(3);
    }

    @Test
    void deleteByDocumentId_removesAllChunks() {
        vectorStorePort.saveChunks(List.of(
                DocumentChunk.create(testDocument.id(), 0, "To delete", basisVector(0), 5)
        ));

        vectorStorePort.deleteByDocumentId(testDocument.id());

        assertThat(vectorStorePort.countByDocumentId(testDocument.id())).isZero();
    }

    // =========================================================================

    /** Returns a 1536-dim unit vector with 1.0 at the given dimension index. */
    private float[] basisVector(int dimension) {
        float[] v = new float[1536];
        v[dimension] = 1.0f;
        return v;
    }

    private static org.assertj.core.data.Offset<Double> within(double delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }
}
