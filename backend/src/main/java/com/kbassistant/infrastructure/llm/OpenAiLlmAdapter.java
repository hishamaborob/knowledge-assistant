package com.kbassistant.infrastructure.llm;

import com.kbassistant.domain.port.out.LlmPort;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "openai")
public class OpenAiLlmAdapter implements LlmPort {

    private final OpenAiChatModel chatModel;

    public OpenAiLlmAdapter(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        ));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
