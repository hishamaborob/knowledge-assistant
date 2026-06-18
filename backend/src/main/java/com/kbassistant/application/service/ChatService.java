package com.kbassistant.application.service;

import com.kbassistant.domain.exception.ChatSessionNotFoundException;
import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.ChatMessageRepository;
import com.kbassistant.domain.port.out.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final QueryService queryService;
    private final int historyWindow;

    public ChatService(ChatSessionRepository chatSessionRepository,
                       ChatMessageRepository chatMessageRepository,
                       QueryService queryService,
                       @Value("${app.chat.history-window:10}") int historyWindow) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.queryService = queryService;
        this.historyWindow = historyWindow;
    }

    public ChatSession startSession(String userId) {
        ChatSession session = ChatSession.create(userId);
        return chatSessionRepository.save(session);
    }

    public QueryResult sendMessage(ChatSessionId sessionId, String question, List<DocumentId> documentFilter) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));

        List<ChatTurn> history = windowedHistory(sessionId);
        log.info("Sending message to session {} with {} prior turns", sessionId, history.size());

        chatMessageRepository.save(ChatMessage.create(session.id(), ChatRole.USER, question, List.of()));

        QueryResult result = queryService.query(question, documentFilter, history);

        chatMessageRepository.save(
                ChatMessage.create(session.id(), ChatRole.ASSISTANT, result.answer(), result.sources()));

        return result;
    }

    public List<ChatMessage> getHistory(ChatSessionId sessionId) {
        if (chatSessionRepository.findById(sessionId).isEmpty()) {
            throw new ChatSessionNotFoundException(sessionId);
        }
        return chatMessageRepository.findBySessionId(sessionId);
    }

    private List<ChatTurn> windowedHistory(ChatSessionId sessionId) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionId(sessionId);
        int fromIndex = Math.max(0, messages.size() - historyWindow);
        return messages.subList(fromIndex, messages.size()).stream()
                .map(m -> new ChatTurn(m.role(), m.content()))
                .toList();
    }
}
