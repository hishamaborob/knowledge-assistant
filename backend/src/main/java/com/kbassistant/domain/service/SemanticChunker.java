package com.kbassistant.domain.service;

import com.kbassistant.domain.port.out.EmbeddingPort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.joining;

public class SemanticChunker implements ChunkingStrategy {

    private final EmbeddingPort embeddingPort;
    private final double splitThreshold;   // cosine similarity below this → boundary
    private final int maxChunkWords;       // fallback split if semantic chunk is too large

    public SemanticChunker(EmbeddingPort embeddingPort, double splitThreshold, int maxChunkWords) {
        this.embeddingPort = embeddingPort;
        this.splitThreshold = splitThreshold;
        this.maxChunkWords = maxChunkWords;
    }

    @Override
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<String> sentences = splitIntoSentences(text);
        if (sentences.size() <= 1) return sentences.isEmpty() ? List.of() : List.of(text.trim());

        List<float[]> embeddings = embeddingPort.embed(sentences);

        // Find split boundaries where adjacent sentence similarity drops below threshold
        List<Integer> splitPoints = new ArrayList<>();
        for (int i = 0; i < embeddings.size() - 1; i++) {
            if (cosineSimilarity(embeddings.get(i), embeddings.get(i + 1)) < splitThreshold) {
                splitPoints.add(i + 1);
            }
        }

        return groupIntoChunks(sentences, splitPoints);
    }

    private List<String> splitIntoSentences(String text) {
        return Arrays.stream(text.trim().split("(?<=[.!?])\\s+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return (normA == 0 || normB == 0) ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<String> groupIntoChunks(List<String> sentences, List<Integer> splitPoints) {
        List<String> chunks = new ArrayList<>();
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);
        boundaries.addAll(splitPoints);
        boundaries.add(sentences.size());

        for (int i = 0; i < boundaries.size() - 1; i++) {
            String chunk = sentences.subList(boundaries.get(i), boundaries.get(i + 1))
                    .stream().collect(joining(" "));

            // Fallback: further split oversized semantic chunks by word count
            int wordCount = chunk.isBlank() ? 0 : chunk.trim().split("\\s+").length;
            if (wordCount > maxChunkWords) {
                chunks.addAll(splitByWordCount(chunk));
            } else if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    private List<String> splitByWordCount(String text) {
        String[] words = text.trim().split("\\s+");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < words.length; i += maxChunkWords) {
            int end = Math.min(i + maxChunkWords, words.length);
            result.add(String.join(" ", Arrays.copyOfRange(words, i, end)));
        }
        return result;
    }
}
