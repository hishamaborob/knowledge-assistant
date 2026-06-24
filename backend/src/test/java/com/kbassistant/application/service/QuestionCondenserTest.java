package com.kbassistant.application.service;

import com.kbassistant.domain.model.ChatRole;
import com.kbassistant.domain.model.ChatTurn;
import com.kbassistant.domain.model.LlmResponse;
import com.kbassistant.domain.port.out.LlmPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionCondenserTest {

    @Mock LlmPort llmPort;

    @Test
    void condense_emptyHistory_returnsOriginalQuestionWithoutCallingLlm() {
        QuestionCondenser condenser = new QuestionCondenser(llmPort, true);

        String result = condenser.condense("What is Spring AI?", List.of());

        assertThat(result).isEqualTo("What is Spring AI?");
        verifyNoInteractions(llmPort);
    }

    @Test
    void condense_disabled_returnsOriginalQuestionWithoutCallingLlm() {
        QuestionCondenser condenser = new QuestionCondenser(llmPort, false);
        List<ChatTurn> history = List.of(
                new ChatTurn(ChatRole.USER, "hello"),
                new ChatTurn(ChatRole.ASSISTANT, "hi there")
        );

        String result = condenser.condense("What about that?", history);

        assertThat(result).isEqualTo("What about that?");
        verifyNoInteractions(llmPort);
    }

    @Test
    void condense_withHistory_callsLlmAndReturnsCondensedQuestion() {
        QuestionCondenser condenser = new QuestionCondenser(llmPort, true);
        List<ChatTurn> history = List.of(
                new ChatTurn(ChatRole.USER, "Tell me about Spring AI"),
                new ChatTurn(ChatRole.ASSISTANT, "Spring AI is a framework for AI applications.")
        );

        when(llmPort.complete(anyString(), anyString()))
                .thenReturn(new LlmResponse("What are the main features of Spring AI?", 10, 5, "gpt-4o"));

        String result = condenser.condense("What are its main features?", history);

        assertThat(result).isEqualTo("What are the main features of Spring AI?");
        verify(llmPort).complete(anyString(), contains("What are its main features?"));
    }

    @Test
    void condense_llmThrows_returnsOriginalQuestion() {
        QuestionCondenser condenser = new QuestionCondenser(llmPort, true);
        List<ChatTurn> history = List.of(
                new ChatTurn(ChatRole.USER, "some prior question"),
                new ChatTurn(ChatRole.ASSISTANT, "some prior answer")
        );

        when(llmPort.complete(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        String result = condenser.condense("follow-up question", history);

        assertThat(result).isEqualTo("follow-up question");
    }
}
