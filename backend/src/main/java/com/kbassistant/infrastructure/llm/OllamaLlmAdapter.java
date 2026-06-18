package com.kbassistant.infrastructure.llm;

import com.kbassistant.domain.model.ChatTurn;
import com.kbassistant.domain.port.out.LlmPort;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
public class OllamaLlmAdapter implements LlmPort {

    private final OllamaChatModel chatModel;

    public OllamaLlmAdapter(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String complete(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        for (ChatTurn turn : history) {
            messages.add(switch (turn.role()) {
                case USER -> new UserMessage(turn.content());
                case ASSISTANT -> new AssistantMessage(turn.content());
            });
        }
        messages.add(new UserMessage(userPrompt));

        Prompt prompt = new Prompt(messages);
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
