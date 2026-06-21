package com.kbassistant.application.service;

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
                .thenReturn("0.9")
                .thenReturn("0.8");

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

        when(llmPort.complete(anyString(), anyString())).thenReturn("not a number");

        service.evaluate("question", "context", "answer");

        DistributionSummary faithfulness = meterRegistry.summary("rag.evaluation.faithfulness", "provider", "openai");
        assertThat(faithfulness.count()).isEqualTo(1);
        assertThat(faithfulness.totalAmount()).isCloseTo(0.5, within(0.001));
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
