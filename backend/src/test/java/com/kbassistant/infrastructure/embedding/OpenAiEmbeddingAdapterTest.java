package com.kbassistant.infrastructure.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAiEmbeddingAdapterTest {

    @Mock OpenAiEmbeddingModel openAiEmbeddingModel;
    OpenAiEmbeddingAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiEmbeddingAdapter(openAiEmbeddingModel);
    }

    @Test
    void embed_singleText_callsModelAndReturnsFloatArray() {
        float[] expected = new float[768];
        expected[0] = 0.5f;
        when(openAiEmbeddingModel.embed("hello world")).thenReturn(expected);

        List<float[]> result = adapter.embed(List.of("hello world"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expected);
        verify(openAiEmbeddingModel).embed("hello world");
    }

    @Test
    void embed_multipleTexts_callsModelForEachText() {
        float[] v1 = new float[768];
        float[] v2 = new float[768];
        v1[0] = 1.0f;
        v2[1] = 1.0f;
        when(openAiEmbeddingModel.embed("text one")).thenReturn(v1);
        when(openAiEmbeddingModel.embed("text two")).thenReturn(v2);

        List<float[]> result = adapter.embed(List.of("text one", "text two"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(v1);
        assertThat(result.get(1)).isEqualTo(v2);
        verify(openAiEmbeddingModel, times(2)).embed(anyString());
    }

    @Test
    void embed_emptyList_returnsEmptyList() {
        List<float[]> result = adapter.embed(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(openAiEmbeddingModel);
    }
}
