package com.kbassistant.infrastructure.llm;

import com.kbassistant.domain.model.ChatTurn;
import com.kbassistant.domain.model.LlmResponse;
import com.kbassistant.domain.port.out.LlmPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
public class OllamaLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmAdapter.class);

    private final OllamaChatModel chatModel;
    private final String modelName;

    public OllamaLlmAdapter(OllamaChatModel chatModel,
                             @Value("${spring.ai.ollama.chat.model:llama3.2}") String modelName) {
        this.chatModel = chatModel;
        this.modelName = modelName;
    }

    @CircuitBreaker(name = "llm", fallbackMethod = "fallback")
    @Retry(name = "llm")
    @Override
    public LlmResponse complete(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        for (ChatTurn turn : history) {
            messages.add(switch (turn.role()) {
                case USER -> new UserMessage(turn.content());
                case ASSISTANT -> new AssistantMessage(turn.content());
            });
        }
        messages.add(new UserMessage(userPrompt));

        var response = chatModel.call(new Prompt(messages));
        var output = response.getResult().getOutput();
        var usage = response.getMetadata().getUsage();
        return new LlmResponse(
                output.getText(),
                usage != null ? (int) usage.getPromptTokens() : 0,
                usage != null ? (int) usage.getCompletionTokens() : 0,
                modelName
        );
    }

    private LlmResponse fallback(String systemPrompt, List<ChatTurn> history, String userPrompt, Throwable t) {
        log.warn("LLM unavailable (circuit breaker or max retries): {}", t.getMessage());
        return new LlmResponse(
                "The AI service is temporarily unavailable. Please try again in a moment.",
                0, 0, "unavailable");
    }
}
