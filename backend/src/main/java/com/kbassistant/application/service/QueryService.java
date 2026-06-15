package com.kbassistant.application.service;

import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.DocumentRepository;
import com.kbassistant.domain.port.out.EmbeddingPort;
import com.kbassistant.domain.port.out.LlmPort;
import com.kbassistant.domain.port.out.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private static final int MAX_CONTEXT_CHARS = 12_000;
    private static final int SNIPPET_LENGTH = 200;

    private static final String SYSTEM_PROMPT = """
            You are a knowledge assistant. Answer the user's question using ONLY the context provided below.
            If the context does not contain enough information to answer the question, say so explicitly.
            Do not make up or infer information beyond what is stated in the context.
            When referencing information, cite the source number in brackets, e.g. [1].
            Be concise and accurate.
            """;

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final LlmPort llmPort;
    private final DocumentRepository documentRepository;
    private final double similarityThreshold;
    private final int topK;

    public QueryService(EmbeddingPort embeddingPort,
                        VectorStorePort vectorStorePort,
                        LlmPort llmPort,
                        DocumentRepository documentRepository,
                        @Value("${app.similarity.threshold:0.75}") double similarityThreshold,
                        @Value("${app.similarity.top-k:10}") int topK) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.llmPort = llmPort;
        this.documentRepository = documentRepository;
        this.similarityThreshold = similarityThreshold;
        this.topK = topK;
    }

    public QueryResult query(String question, List<DocumentId> documentFilter) {
        long start = System.currentTimeMillis();
        log.info("Processing query: '{}'", question);

        // 1. Embed the question with the same model used during ingestion
        float[] queryVector = embeddingPort.embed(List.of(question)).get(0);

        // 2. Similarity search
        SimilaritySearchRequest.Builder builder = SimilaritySearchRequest.builder()
                .queryVector(queryVector)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        if (documentFilter != null && !documentFilter.isEmpty()) {
            builder.documentIds(documentFilter);
        }

        List<ScoredChunk> results = vectorStorePort.similaritySearch(builder.build());
        log.info("Similarity search returned {} chunks", results.size());

        // 3. No relevant chunks — return without calling the LLM
        if (results.isEmpty()) {
            return new QueryResult(
                    "No relevant documents found for this question.",
                    List.of(),
                    System.currentTimeMillis() - start
            );
        }

        // 4. Load document names for citations (batch by unique documentId)
        Map<DocumentId, String> docNames = loadDocumentNames(results);

        // 5. Build context string, capped at MAX_CONTEXT_CHARS
        String context = buildContext(results, docNames);

        // 6. Call LLM
        String answer = llmPort.complete(SYSTEM_PROMPT, buildUserPrompt(question, context));
        log.info("LLM answer generated ({} chars)", answer.length());

        // 7. Build source citations for the response
        List<SourceChunk> sources = buildSources(results, docNames);

        return new QueryResult(answer, sources, System.currentTimeMillis() - start);
    }

    private Map<DocumentId, String> loadDocumentNames(List<ScoredChunk> results) {
        return results.stream()
                .map(sc -> sc.chunk().documentId())
                .distinct()
                .map(documentRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(Document::id, Document::name));
    }

    private String buildContext(List<ScoredChunk> results, Map<DocumentId, String> docNames) {
        StringBuilder sb = new StringBuilder();
        int totalChars = 0;
        int idx = 1;

        for (ScoredChunk sc : results) {
            String text = sc.chunk().content();
            if (totalChars + text.length() > MAX_CONTEXT_CHARS) break;
            String docName = docNames.getOrDefault(sc.chunk().documentId(), "Unknown");
            sb.append("[").append(idx++).append("] (")
              .append(docName).append(", score: ")
              .append(String.format("%.2f", sc.score())).append(")\n")
              .append(text).append("\n\n");
            totalChars += text.length();
        }

        return sb.toString().trim();
    }

    private String buildUserPrompt(String question, String context) {
        return "Context:\n" + context + "\n\nQuestion: " + question;
    }

    private List<SourceChunk> buildSources(List<ScoredChunk> results, Map<DocumentId, String> docNames) {
        return results.stream()
                .map(sc -> {
                    String content = sc.chunk().content();
                    String snippet = content.length() > SNIPPET_LENGTH
                            ? content.substring(0, SNIPPET_LENGTH) + "..."
                            : content;
                    String docName = docNames.getOrDefault(sc.chunk().documentId(), "Unknown");
                    return new SourceChunk(sc.chunk().documentId(), docName, snippet, sc.score());
                })
                .toList();
    }
}
