package com.kbassistant.application.service;

import com.kbassistant.domain.model.LlmResponse;
import com.kbassistant.domain.port.out.LlmPort;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock LlmPort llmPort;

    // 1. evaluate_belowSampleRate_doesNotCallLlm — sampleRate=0.0 means never evaluate
    @Test
    void evaluate_belowSampleRate_doesNotCallLlm() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EvaluationService service = new EvaluationService(llmPort, true, 0.0, "openai", meterRegistry);

        service.evaluate("question", "context", "answer");

        verifyNoInteractions(llmPort);
    }

    // 2. evaluate_disabled_doesNotCallLlm — enabled=false skips regardless of sampleRate
    @Test
    void evaluate_disabled_doesNotCallLlm() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EvaluationService service = new EvaluationService(llmPort, false, 1.0, "openai", meterRegistry);

        service.evaluate("question", "context", "answer");

        verifyNoInteractions(llmPort);
    }

    // 3. evaluate_happyPath_recordsBothScores — both distribution summaries get recorded
    @Test
    void evaluate_happyPath_recordsBothScores() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EvaluationService service = new EvaluationService(llmPort, true, 1.0, "openai", meterRegistry);

        // First call: faithfulness prompt, second call: relevance prompt
        when(llmPort.complete(anyString(), anyString()))
                .thenReturn(new LlmResponse("0.9", 10, 5, "gpt-4o"))
                .thenReturn(new LlmResponse("0.8", 10, 5, "gpt-4o"));

        service.evaluate("question", "context", "answer");

        DistributionSummary faithfulness = meterRegistry.summary("rag.evaluation.faithfulness", "provider", "openai");
        DistributionSummary relevance = meterRegistry.summary("rag.evaluation.answer_relevance", "provider", "openai");

        assertThat(faithfulness.count()).isEqualTo(1);
        assertThat(faithfulness.totalAmount()).isCloseTo(0.9, within(0.001));

        assertThat(relevance.count()).isEqualTo(1);
        assertThat(relevance.totalAmount()).isCloseTo(0.8, within(0.001));
    }

    // 4. evaluate_nonNumericResponse_defaultsToPointFive
    @Test
    void evaluate_nonNumericResponse_defaultsToPointFive() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EvaluationService service = new EvaluationService(llmPort, true, 1.0, "openai", meterRegistry);

        when(llmPort.complete(anyString(), anyString()))
                .thenReturn(new LlmResponse("not a number", 10, 5, "gpt-4o"));

        service.evaluate("question", "context", "answer");

        DistributionSummary faithfulness = meterRegistry.summary("rag.evaluation.faithfulness", "provider", "openai");
        assertThat(faithfulness.count()).isEqualTo(1);
        assertThat(faithfulness.totalAmount()).isCloseTo(0.5, within(0.001));
    }

    // 5. evaluate_contextTruncatedToFourThousandChars — long context must not overflow judge prompt
    @Test
    void evaluate_longContext_contextTruncatedBeforeJudgeCall() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EvaluationService service = new EvaluationService(llmPort, true, 1.0, "openai", meterRegistry);

        String longContext = "x".repeat(5000);
        when(llmPort.complete(anyString(), anyString()))
                .thenReturn(new LlmResponse("0.8", 10, 5, "gpt-4o"));

        service.evaluate("question", longContext, "answer");

        // Faithfulness prompt replaces {context} — verify the prompt sent to the LLM does not contain
        // the full 5000-char context (it must be truncated to 4000 + ellipsis)
        org.mockito.ArgumentCaptor<String> userPromptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llmPort, org.mockito.Mockito.atLeastOnce()).complete(anyString(), userPromptCaptor.capture());
        String faithfulnessPrompt = userPromptCaptor.getAllValues().get(0);
        assertThat(faithfulnessPrompt).doesNotContain("x".repeat(5000));
        assertThat(faithfulnessPrompt.length()).isLessThan(5000);
    }

    // 6. evaluate_scoreAboveOne_clampedToOne
    @Test
    void evaluate_scoreAboveOne_clampedToOne() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EvaluationService service = new EvaluationService(llmPort, true, 1.0, "openai", meterRegistry);

        when(llmPort.complete(anyString(), anyString()))
                .thenReturn(new LlmResponse("1.5", 10, 5, "gpt-4o"))
                .thenReturn(new LlmResponse("2.0", 10, 5, "gpt-4o"));

        service.evaluate("question", "context", "answer");

        DistributionSummary faithfulness = meterRegistry.summary("rag.evaluation.faithfulness", "provider", "openai");
        assertThat(faithfulness.totalAmount()).isCloseTo(1.0, within(0.001));
    }

    // 7. evaluate_scoreBelowZero_clampedToZero
    @Test
    void evaluate_scoreBelowZero_clampedToZero() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EvaluationService service = new EvaluationService(llmPort, true, 1.0, "openai", meterRegistry);

        when(llmPort.complete(anyString(), anyString()))
                .thenReturn(new LlmResponse("-0.5", 10, 5, "gpt-4o"))
                .thenReturn(new LlmResponse("0.8", 10, 5, "gpt-4o"));

        service.evaluate("question", "context", "answer");

        DistributionSummary faithfulness = meterRegistry.summary("rag.evaluation.faithfulness", "provider", "openai");
        assertThat(faithfulness.totalAmount()).isCloseTo(0.0, within(0.001));
    }

    // 5. evaluate_llmThrows_doesNotPropagateException
    @Test
    void evaluate_llmThrows_doesNotPropagateException() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EvaluationService service = new EvaluationService(llmPort, true, 1.0, "openai", meterRegistry);

        when(llmPort.complete(anyString(), anyString())).thenThrow(new RuntimeException("LLM unavailable"));

        // Must not throw — evaluation is non-critical fire-and-forget
        assertThatNoException().isThrownBy(() ->
                service.evaluate("question", "context", "answer"));
    }
}
