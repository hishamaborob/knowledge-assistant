package com.kbassistant.application.service;

import com.kbassistant.domain.port.out.LlmPort;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private static final String EVAL_SYSTEM_PROMPT =
            "You are an evaluation assistant. Return ONLY a decimal number between 0.0 and 1.0. No explanation.";

    private static final String FAITHFULNESS_PROMPT = """
            Evaluate the faithfulness of an AI answer to the provided context.
            Faithfulness: every claim in the answer must be directly supported by the context.

            Context:
            {context}

            Answer:
            {answer}

            Rate faithfulness 0.0–1.0:
            1.0 = every claim is directly supported by the context
            0.5 = some claims supported, some not
            0.0 = answer contains claims not present in the context

            Return ONLY a decimal number between 0.0 and 1.0.""";

    private static final String RELEVANCE_PROMPT = """
            Evaluate how well an AI answer addresses the question asked.

            Question:
            {question}

            Answer:
            {answer}

            Rate answer relevance 0.0–1.0:
            1.0 = fully and directly addresses the question
            0.5 = partially addresses the question
            0.0 = does not address the question

            Return ONLY a decimal number between 0.0 and 1.0.""";

    private final LlmPort llmPort;
    private final boolean enabled;
    private final double sampleRate;
    private final Random random;
    private final DistributionSummary faithfulnessSummary;
    private final DistributionSummary relevanceSummary;

    public EvaluationService(LlmPort llmPort,
                             @Value("${app.evaluation.enabled:true}") boolean enabled,
                             @Value("${app.evaluation.sample-rate:0.2}") double sampleRate,
                             @Value("${app.llm.provider:openai}") String llmProvider,
                             MeterRegistry meterRegistry) {
        this.llmPort = llmPort;
        this.enabled = enabled;
        this.sampleRate = sampleRate;
        this.random = new Random();
        this.faithfulnessSummary = DistributionSummary.builder("rag.evaluation.faithfulness")
                .description("LLM-as-a-judge faithfulness score (0–1): answer claims supported by retrieved context")
                .tag("provider", llmProvider)
                .register(meterRegistry);
        this.relevanceSummary = DistributionSummary.builder("rag.evaluation.answer_relevance")
                .description("LLM-as-a-judge answer relevance score (0–1): how well the answer addresses the question")
                .tag("provider", llmProvider)
                .register(meterRegistry);
    }

    @Async("evaluationTaskExecutor")
    public void evaluate(String question, String context, String answer) {
        if (!enabled || random.nextDouble() > sampleRate) {
            return;
        }
        try {
            double faithfulness = scoreWith(FAITHFULNESS_PROMPT
                    .replace("{context}", truncate(context, 4000))
                    .replace("{answer}", answer));
            double relevance = scoreWith(RELEVANCE_PROMPT
                    .replace("{question}", question)
                    .replace("{answer}", answer));

            faithfulnessSummary.record(faithfulness);
            relevanceSummary.record(relevance);

            log.debug("RAG evaluation — faithfulness={}, relevance={}", faithfulness, relevance);
        } catch (Exception e) {
            log.warn("RAG evaluation skipped (non-critical): {}", e.getMessage());
        }
    }

    private double scoreWith(String userPrompt) {
        String response = llmPort.complete(EVAL_SYSTEM_PROMPT, userPrompt).content().trim();
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(response)));
        } catch (NumberFormatException e) {
            log.warn("Judge returned non-numeric '{}' — defaulting to 0.5", response);
            return 0.5;
        }
    }

    private static String truncate(String text, int maxChars) {
        return text.length() > maxChars ? text.substring(0, maxChars) + "…" : text;
    }
}
