package com.kbassistant.application.service;

import com.kbassistant.domain.model.ChatTurn;
import com.kbassistant.domain.port.out.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestionCondenser {

    private static final Logger log = LoggerFactory.getLogger(QuestionCondenser.class);

    private static final String SYSTEM_PROMPT =
            "You are a question rewriting assistant. Return ONLY the rewritten question, with no explanation.";

    private static final String USER_PROMPT_TEMPLATE = """
            Given the conversation history below, rewrite the follow-up question as a fully \
            self-contained question that includes all necessary context from the history \
            (resolve pronouns, references, and implicit context).

            Conversation history:
            {history}

            Follow-up question: {question}
            """;

    private final LlmPort llmPort;
    private final boolean enabled;

    public QuestionCondenser(LlmPort llmPort,
                             @Value("${app.question-condenser.enabled:true}") boolean enabled) {
        this.llmPort = llmPort;
        this.enabled = enabled;
    }

    public String condense(String question, List<ChatTurn> history) {
        if (!enabled || history.isEmpty()) {
            return question;
        }
        try {
            String historyText = history.stream()
                    .map(t -> t.role().name() + ": " + t.content())
                    .collect(Collectors.joining("\n"));

            String userPrompt = USER_PROMPT_TEMPLATE
                    .replace("{history}", historyText)
                    .replace("{question}", question);

            String condensed = llmPort.complete(SYSTEM_PROMPT, userPrompt).content();
            log.debug("Condensed question: '{}' → '{}'", question, condensed);
            return condensed.isBlank() ? question : condensed;
        } catch (Exception e) {
            log.warn("Question condensing failed (using original): {}", e.getMessage());
            return question;
        }
    }
}
