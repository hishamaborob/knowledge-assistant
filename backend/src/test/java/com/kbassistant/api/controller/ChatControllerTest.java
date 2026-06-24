package com.kbassistant.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbassistant.api.dto.ChatMessageRequest;
import com.kbassistant.api.dto.CreateSessionRequest;
import com.kbassistant.application.service.ChatService;
import com.kbassistant.domain.exception.ChatSessionNotFoundException;
import com.kbassistant.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ChatController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ChatService chatService;

    @Test
    void createSession_validRequest_returns201WithSession() throws Exception {
        ChatSession session = ChatSession.create("user-1");
        when(chatService.startSession("user-1")).thenReturn(session);

        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSessionRequest("user-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(session.id().toString()))
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void createSession_blankUserId_returns400() throws Exception {
        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSessionRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_validRequest_returns200WithAnswer() throws Exception {
        ChatSessionId sessionId = ChatSessionId.generate();
        QueryResult result = new QueryResult("the answer", List.of(), 50L, 0, 0, "none");
        when(chatService.sendMessage(eq(sessionId), eq("question"), anyList())).thenReturn(result);

        mockMvc.perform(post("/sessions/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatMessageRequest("question", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("the answer"))
                .andExpect(jsonPath("$.durationMs").value(50));
    }

    @Test
    void sendMessage_unknownSession_returns404() throws Exception {
        ChatSessionId sessionId = ChatSessionId.generate();
        when(chatService.sendMessage(eq(sessionId), anyString(), anyList()))
                .thenThrow(new ChatSessionNotFoundException(sessionId));

        mockMvc.perform(post("/sessions/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatMessageRequest("question", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendMessage_blankQuestion_returns400() throws Exception {
        ChatSessionId sessionId = ChatSessionId.generate();

        mockMvc.perform(post("/sessions/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatMessageRequest("", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_returns200WithMessages() throws Exception {
        ChatSessionId sessionId = ChatSessionId.generate();
        ChatMessage message = ChatMessage.create(sessionId, ChatRole.USER, "hi", List.of());
        when(chatService.getHistory(sessionId)).thenReturn(List.of(message));

        mockMvc.perform(get("/sessions/" + sessionId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("hi"));
    }

    @Test
    void getHistory_unknownSession_returns404() throws Exception {
        ChatSessionId sessionId = ChatSessionId.generate();
        when(chatService.getHistory(sessionId)).thenThrow(new ChatSessionNotFoundException(sessionId));

        mockMvc.perform(get("/sessions/" + sessionId + "/messages"))
                .andExpect(status().isNotFound());
    }
}
