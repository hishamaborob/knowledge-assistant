package com.kbassistant.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbassistant.api.dto.QueryRequest;
import com.kbassistant.application.service.QueryService;
import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.QueryResult;
import com.kbassistant.domain.model.SourceChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean QueryService queryService;

    @Test
    void postQuery_validRequest_returns200WithAnswer() throws Exception {
        QueryResult result = new QueryResult("Spring AI is a framework.", List.of(), 150L);
        when(queryService.query(eq("What is Spring AI?"), anyList())).thenReturn(result);

        mockMvc.perform(post("/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QueryRequest("What is Spring AI?", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Spring AI is a framework."))
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.durationMs").value(150));
    }

    @Test
    void postQuery_withSources_returnsCitations() throws Exception {
        DocumentId docId = DocumentId.generate();
        SourceChunk source = new SourceChunk(docId, "Guide", "snippet...", 0.91);
        QueryResult result = new QueryResult("The answer.", List.of(source), 200L);
        when(queryService.query(anyString(), anyList())).thenReturn(result);

        mockMvc.perform(post("/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QueryRequest("question", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].documentName").value("Guide"))
                .andExpect(jsonPath("$.sources[0].contentSnippet").value("snippet..."))
                .andExpect(jsonPath("$.sources[0].score").value(0.91));
    }

    @Test
    void postQuery_blankQuestion_returns400() throws Exception {
        mockMvc.perform(post("/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QueryRequest("", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postQuery_nullQuestion_returns400() throws Exception {
        mockMvc.perform(post("/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postQuery_withDocumentFilter_passesIdsToService() throws Exception {
        String docId = UUID.randomUUID().toString();
        QueryResult result = new QueryResult("answer", List.of(), 100L);
        when(queryService.query(anyString(), argThat(ids ->
                ids.size() == 1 && ids.get(0).value().toString().equals(docId))))
                .thenReturn(result);

        mockMvc.perform(post("/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QueryRequest("question", List.of(docId)))))
                .andExpect(status().isOk());
    }

    @Test
    void postQuery_invalidUuidInFilter_returns400() throws Exception {
        mockMvc.perform(post("/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QueryRequest("question", List.of("not-a-uuid")))))
                .andExpect(status().isBadRequest());
    }
}
