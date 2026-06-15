package com.kbassistant.infrastructure.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaLlmAdapterTest {

    @Mock OllamaChatModel chatModel;
    OllamaLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OllamaLlmAdapter(chatModel);
    }

    @Test
    void complete_returnsLlmContent() {
        doReturn(responseWith("The answer is 42.")).when(chatModel).call((Prompt) any());

        String result = adapter.complete("You are a helpful assistant.", "What is the answer?");

        assertThat(result).isEqualTo("The answer is 42.");
    }

    @Test
    void complete_passesSystemAndUserMessagesInPrompt() {
        doReturn(responseWith("ok")).when(chatModel).call((Prompt) any());

        adapter.complete("system prompt", "user prompt");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call((Prompt) captor.capture());
        assertThat(captor.getValue().getInstructions()).hasSize(2);
    }

    @Test
    void complete_emptyResponse_returnsEmptyString() {
        doReturn(responseWith("")).when(chatModel).call((Prompt) any());

        String result = adapter.complete("sys", "user");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------

    private static ChatResponse responseWith(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
