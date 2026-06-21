package com.kbassistant.application.service;

import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.DocumentRepository;
import com.kbassistant.domain.port.out.EmbeddingPort;
import com.kbassistant.domain.port.out.LlmPort;
import com.kbassistant.domain.port.out.VectorStorePort;
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
class QueryServiceTest {

    @Mock EmbeddingPort embeddingPort;
    @Mock VectorStorePort vectorStorePort;
    @Mock LlmPort llmPort;
    @Mock DocumentRepository documentRepository;
    @Mock EvaluationService evaluationService;

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    QueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new QueryService(
                embeddingPort, vectorStorePort, llmPort, documentRepository,
                evaluationService, 0.75, 10, "openai", meterRegistry);
    }

    @Test
    void query_happyPath_returnsAnswerWithSources() {
        Document doc = Document.create("Guide", "guide.pdf", MimeType.PDF, 1000L, "user");
        DocumentChunk chunk = DocumentChunk.create(doc.id(), 0, "Spring AI is great", new float[768], 4);
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.92);

        when(embeddingPort.embed(List.of("What is Spring AI?")))
                .thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(List.of(scoredChunk));
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));
        when(llmPort.complete(anyString(), anyList(), anyString())).thenReturn("Spring AI is a framework.");

        QueryResult result = queryService.query("What is Spring AI?", List.of());

        assertThat(result.answer()).isEqualTo("Spring AI is a framework.");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).documentName()).isEqualTo("Guide");
        assertThat(result.sources().get(0).score()).isEqualTo(0.92);
        assertThat(result.hasResults()).isTrue();
        verify(llmPort).complete(anyString(), anyList(), contains("Spring AI is great"));
    }

    @Test
    void query_happyPath_recordsQueryTimer() {
        Document doc = Document.create("Guide", "guide.pdf", MimeType.PDF, 1000L, "user");
        DocumentChunk chunk = DocumentChunk.create(doc.id(), 0, "Spring AI is great", new float[768], 4);
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.92);

        when(embeddingPort.embed(anyList())).thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(List.of(scoredChunk));
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));
        when(llmPort.complete(anyString(), anyList(), anyString())).thenReturn("answer");

        queryService.query("What is Spring AI?", List.of());

        assertThat(meterRegistry.timer("chat.query.duration", "provider", "openai").count())
                .isEqualTo(1);
    }

    @Test
    void query_noResults_returnsNoResultsMessageWithoutCallingLlm() {
        when(embeddingPort.embed(anyList())).thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(List.of());

        QueryResult result = queryService.query("Unknown topic", List.of());

        assertThat(result.answer()).contains("No relevant documents found");
        assertThat(result.sources()).isEmpty();
        assertThat(result.hasResults()).isFalse();
        verifyNoInteractions(llmPort);
    }

    @Test
    void query_noResults_recordsSearchOutcomeNotFound() {
        when(embeddingPort.embed(anyList())).thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(List.of());

        queryService.query("Unknown topic", List.of());

        assertThat(meterRegistry.summary("similarity.search.results", "outcome", "not_found").count())
                .isEqualTo(1);
    }

    @Test
    void query_withDocumentFilter_passesFilterToVectorStore() {
        when(embeddingPort.embed(anyList())).thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(List.of());

        DocumentId filterId = DocumentId.generate();
        queryService.query("question", List.of(filterId));

        verify(vectorStorePort).similaritySearch(argThat(req ->
                req.hasDocumentFilter() && req.documentIds().contains(filterId)
        ));
    }

    @Test
    void query_contextSnippetTruncatedTo200Chars() {
        Document doc = Document.create("Long Doc", "long.txt", MimeType.TEXT, 5000L, "user");
        String longContent = "x".repeat(300);
        DocumentChunk chunk = DocumentChunk.create(doc.id(), 0, longContent, new float[768], 60);
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.8);

        when(embeddingPort.embed(anyList())).thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(List.of(scoredChunk));
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));
        when(llmPort.complete(anyString(), anyList(), anyString())).thenReturn("answer");

        QueryResult result = queryService.query("question", List.of());

        String snippet = result.sources().get(0).contentSnippet();
        assertThat(snippet).endsWith("...");
        assertThat(snippet.length()).isLessThanOrEqualTo(203); // 200 + "..."
    }

    @Test
    void query_documentNotFoundInRepository_usesUnknownFallback() {
        DocumentId docId = DocumentId.generate();
        DocumentChunk chunk = DocumentChunk.create(docId, 0, "Some content", new float[768], 3);
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.85);

        when(embeddingPort.embed(anyList())).thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(List.of(scoredChunk));
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());
        when(llmPort.complete(anyString(), anyList(), anyString())).thenReturn("answer");

        QueryResult result = queryService.query("question", List.of());

        assertThat(result.sources().get(0).documentName()).isEqualTo("Unknown");
    }

    @Test
    void query_contextBudgetExceeded_stopsAddingChunks() {
        Document doc = Document.create("Doc", "doc.txt", MimeType.TEXT, 1000L, "user");
        // Each chunk is 4000 chars; 3 chunks = 12,000 which hits the budget after chunk 3
        String bigContent = "w".repeat(4001);
        List<ScoredChunk> chunks = List.of(
                new ScoredChunk(DocumentChunk.create(doc.id(), 0, bigContent, new float[768], 800), 0.9),
                new ScoredChunk(DocumentChunk.create(doc.id(), 1, bigContent, new float[768], 800), 0.88),
                new ScoredChunk(DocumentChunk.create(doc.id(), 2, bigContent, new float[768], 800), 0.86)
        );

        when(embeddingPort.embed(anyList())).thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(chunks);
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));
        when(llmPort.complete(anyString(), anyList(), anyString())).thenReturn("answer");

        // Capture the user prompt passed to the LLM
        queryService.query("question", List.of());

        verify(llmPort).complete(anyString(), anyList(), argThat(userPrompt ->
                // The third chunk exceeds the budget so should not appear as [3]
                !userPrompt.contains("[3]")
        ));
    }

    @Test
    void query_recordsDurationMs() {
        when(embeddingPort.embed(anyList())).thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(List.of());

        QueryResult result = queryService.query("question", List.of());

        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void query_twoArgOverload_passesEmptyHistory() {
        Document doc = Document.create("Guide", "guide.pdf", MimeType.PDF, 1000L, "user");
        DocumentChunk chunk = DocumentChunk.create(doc.id(), 0, "content", new float[768], 4);
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.9);

        when(embeddingPort.embed(anyList())).thenReturn(List.of(new float[768]));
        when(vectorStorePort.similaritySearch(any())).thenReturn(List.of(scoredChunk));
        when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));
        when(llmPort.complete(anyString(), anyList(), anyString())).thenReturn("answer");

        queryService.query("question", List.of());

        verify(llmPort).complete(anyString(), eq(List.of()), anyString());
    }
}
