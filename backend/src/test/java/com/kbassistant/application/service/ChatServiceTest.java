package com.kbassistant.application.service;

import com.kbassistant.domain.exception.ChatSessionNotFoundException;
import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.ChatMessageRepository;
import com.kbassistant.domain.port.out.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatSessionRepository chatSessionRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock QueryService queryService;

    ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatSessionRepository, chatMessageRepository, queryService, 10);
        lenient().when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void startSession_createsAndPersistsSession() {
        when(chatSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChatSession session = chatService.startSession("user-1");

        assertThat(session.userId()).isEqualTo("user-1");
        verify(chatSessionRepository).save(argThat(s -> s.userId().equals("user-1")));
    }

    @Test
    void sendMessage_sessionNotFound_throwsAndNeverCallsQueryService() {
        ChatSessionId sessionId = ChatSessionId.generate();
        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(sessionId, "question", List.of()))
                .isInstanceOf(ChatSessionNotFoundException.class)
                .hasMessageContaining(sessionId.toString());

        verifyNoInteractions(queryService);
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_firstMessage_passesEmptyHistory() {
        ChatSession session = ChatSession.create("user-1");
        when(chatSessionRepository.findById(session.id())).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionId(session.id())).thenReturn(List.of());
        when(queryService.query(eq("hi"), anyList(), eq(List.of())))
                .thenReturn(new QueryResult("hello!", List.of(), 10L));

        QueryResult result = chatService.sendMessage(session.id(), "hi", List.of());

        assertThat(result.answer()).isEqualTo("hello!");
        verify(queryService).query("hi", List.of(), List.of());
    }

    @Test
    void sendMessage_savesUserMessageThenAssistantMessageWithCitations() {
        ChatSession session = ChatSession.create("user-1");
        when(chatSessionRepository.findById(session.id())).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionId(session.id())).thenReturn(List.of());

        SourceChunk source = new SourceChunk(DocumentId.generate(), "Guide", "snippet...", 0.9);
        when(queryService.query(anyString(), anyList(), anyList()))
                .thenReturn(new QueryResult("the answer", List.of(source), 20L));

        chatService.sendMessage(session.id(), "question", List.of());

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());

        List<ChatMessage> saved = captor.getAllValues();
        assertThat(saved.get(0).role()).isEqualTo(ChatRole.USER);
        assertThat(saved.get(0).content()).isEqualTo("question");
        assertThat(saved.get(1).role()).isEqualTo(ChatRole.ASSISTANT);
        assertThat(saved.get(1).content()).isEqualTo("the answer");
        assertThat(saved.get(1).citations()).containsExactly(source);
    }

    @Test
    void sendMessage_windowsHistoryToConfiguredSize() {
        chatService = new ChatService(chatSessionRepository, chatMessageRepository, queryService, 2);

        ChatSession session = ChatSession.create("user-1");
        when(chatSessionRepository.findById(session.id())).thenReturn(Optional.of(session));

        List<ChatMessage> priorMessages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            priorMessages.add(ChatMessage.reconstitute(
                    ChatMessageId.generate(), session.id(),
                    i % 2 == 0 ? ChatRole.USER : ChatRole.ASSISTANT,
                    "turn-" + i, List.of(), null, null, null, Instant.now()));
        }
        when(chatMessageRepository.findBySessionId(session.id())).thenReturn(priorMessages);
        when(queryService.query(anyString(), anyList(), anyList()))
                .thenReturn(new QueryResult("answer", List.of(), 5L));

        chatService.sendMessage(session.id(), "new question", List.of());

        ArgumentCaptor<List<ChatTurn>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(queryService).query(eq("new question"), anyList(), historyCaptor.capture());

        List<ChatTurn> history = historyCaptor.getValue();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).content()).isEqualTo("turn-3");
        assertThat(history.get(1).content()).isEqualTo("turn-4");
    }

    @Test
    void getHistory_sessionNotFound_throws() {
        ChatSessionId sessionId = ChatSessionId.generate();
        when(chatSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getHistory(sessionId))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    @Test
    void getHistory_returnsMessagesFromRepository() {
        ChatSession session = ChatSession.create("user-1");
        ChatMessage message = ChatMessage.create(session.id(), ChatRole.USER, "hi", List.of());
        when(chatSessionRepository.findById(session.id())).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionId(session.id())).thenReturn(List.of(message));

        List<ChatMessage> history = chatService.getHistory(session.id());

        assertThat(history).containsExactly(message);
    }
}
