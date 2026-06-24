package com.kbassistant.infrastructure.llm;

import com.kbassistant.domain.model.ChatRole;
import com.kbassistant.domain.model.ChatTurn;
import com.kbassistant.domain.model.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnthropicLlmAdapterTest {

    @Mock AnthropicChatModel chatModel;
    AnthropicLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AnthropicLlmAdapter(chatModel, "claude-sonnet-4-6");
    }

    @Test
    void complete_returnsLlmContent() {
        doReturn(responseWith("The answer is 42.")).when(chatModel).call((Prompt) any());

        LlmResponse result = adapter.complete("You are a helpful assistant.", "What is the answer?");

        assertThat(result.content()).isEqualTo("The answer is 42.");
        assertThat(result.modelUsed()).isEqualTo("claude-sonnet-4-6");
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
    void complete_emptyResponse_returnsEmptyContent() {
        doReturn(responseWith("")).when(chatModel).call((Prompt) any());

        LlmResponse result = adapter.complete("sys", "user");

        assertThat(result.content()).isEmpty();
    }

    @Test
    void complete_withHistory_buildsSystemThenHistoryThenUserMessages() {
        doReturn(responseWith("ok")).when(chatModel).call((Prompt) any());

        List<ChatTurn> history = List.of(
                new ChatTurn(ChatRole.USER, "first question"),
                new ChatTurn(ChatRole.ASSISTANT, "first answer")
        );

        adapter.complete("system prompt", history, "second question");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call((Prompt) captor.capture());
        List<Message> messages = captor.getValue().getInstructions();

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(1).getText()).isEqualTo("first question");
        assertThat(messages.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(messages.get(2).getText()).isEqualTo("first answer");
        assertThat(messages.get(3).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(3).getText()).isEqualTo("second question");
    }

    // -------------------------------------------------------------------------

    private static ChatResponse responseWith(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
