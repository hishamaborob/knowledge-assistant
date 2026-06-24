package com.kbassistant.api.controller;

import com.kbassistant.api.dto.ChatMessageHistoryItem;
import com.kbassistant.api.dto.ChatMessageRequest;
import com.kbassistant.api.dto.ChatMessageResponse;
import com.kbassistant.api.dto.CreateSessionRequest;
import com.kbassistant.api.dto.SessionResponse;
import com.kbassistant.application.service.ChatService;
import com.kbassistant.domain.model.ChatSession;
import com.kbassistant.domain.model.ChatSessionId;
import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.QueryResult;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@Tag(name = "Chat", description = "Multi-turn conversation sessions over the RAG pipeline")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a chat session")
    public SessionResponse createSession(@RequestBody @Valid CreateSessionRequest request) {
        ChatSession session = chatService.startSession(request.userId());
        return SessionResponse.from(session);
    }

    @PostMapping("/{sessionId}/messages")
    @RateLimiter(name = "chatApi")
    @Operation(summary = "Send a message in a session", description =
            "Injects prior turns as conversation history before embedding, searching, and generating an answer.")
    public ChatMessageResponse sendMessage(@PathVariable String sessionId,
                                           @RequestBody @Valid ChatMessageRequest request) {
        List<DocumentId> filter = request.documentIds() == null ? List.of()
                : request.documentIds().stream()
                        .map(id -> DocumentId.of(UUID.fromString(id)))
                        .toList();

        QueryResult result = chatService.sendMessage(
                ChatSessionId.from(sessionId), request.question(), filter);
        return ChatMessageResponse.from(result);
    }

    @GetMapping("/{sessionId}/messages")
    @Operation(summary = "Retrieve full conversation history for a session")
    public List<ChatMessageHistoryItem> getHistory(@PathVariable String sessionId) {
        return chatService.getHistory(ChatSessionId.from(sessionId)).stream()
                .map(ChatMessageHistoryItem::from)
                .toList();
    }
}
