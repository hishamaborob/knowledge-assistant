package com.kbassistant.domain.service;

import com.kbassistant.domain.port.out.EmbeddingPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SemanticChunkerTest {

    @Mock EmbeddingPort embeddingPort;

    @Test
    void chunk_blankText_returnsEmptyList() {
        SemanticChunker chunker = new SemanticChunker(embeddingPort, 0.7, 512);

        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk("   ")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
        verifyNoInteractions(embeddingPort);
    }

    @Test
    void chunk_singleSentence_returnsSingleChunkWithoutCallingEmbeddingPort() {
        SemanticChunker chunker = new SemanticChunker(embeddingPort, 0.7, 512);

        List<String> result = chunker.chunk("This is just one sentence.");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("This is just one sentence");
        verifyNoInteractions(embeddingPort);
    }

    @Test
    void chunk_lowSimilarityBetweenSentences_splitsAtBoundary() {
        SemanticChunker chunker = new SemanticChunker(embeddingPort, 0.7, 512);

        // 3 sentences: similarity between [1,2] is high (0.95), between [2,3] is low (0.1)
        // Expected: chunk 1 = sentences 1+2, chunk 2 = sentence 3
        float[] embedding1 = new float[]{1.0f, 0.0f};
        float[] embedding2 = new float[]{0.95f, 0.1f};  // similar to embedding1
        float[] embedding3 = new float[]{0.0f, 1.0f};   // orthogonal to embedding1 and embedding2

        when(embeddingPort.embed(anyList())).thenReturn(List.of(embedding1, embedding2, embedding3));

        List<String> result = chunker.chunk("First sentence. Second sentence. Third sentence.");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).contains("First sentence").contains("Second sentence");
        assertThat(result.get(1)).contains("Third sentence");
    }

    @Test
    void chunk_highSimilarityAllSentences_returnsOneChunk() {
        SemanticChunker chunker = new SemanticChunker(embeddingPort, 0.7, 512);

        // All sentences have very similar embeddings → no split boundary
        float[] embedding = new float[]{1.0f, 0.0f};
        when(embeddingPort.embed(anyList())).thenReturn(List.of(embedding, embedding, embedding));

        List<String> result = chunker.chunk("Sentence one. Sentence two. Sentence three.");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("Sentence one")
                .contains("Sentence two")
                .contains("Sentence three");
    }

    @Test
    void chunk_oversizedSemanticChunk_fallsBackToWordCountSplit() {
        // maxChunkWords=5 — a semantic chunk of 10 words gets split by word count
        SemanticChunker chunker = new SemanticChunker(embeddingPort, 0.7, 5);

        // All sentences similar → single semantic chunk but it's too large
        float[] embedding = new float[]{1.0f, 0.0f};
        when(embeddingPort.embed(anyList())).thenReturn(List.of(embedding, embedding));

        // 2 sentences, 5 words each = 10 words total → exceeds maxChunkWords=5 → 2 sub-chunks
        List<String> result = chunker.chunk("One two three four five. Six seven eight nine ten.");

        assertThat(result).hasSize(2);
    }
}
