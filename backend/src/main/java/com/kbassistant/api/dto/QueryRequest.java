package com.kbassistant.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record QueryRequest(
        @NotBlank(message = "question must not be blank")
        String question,

        List<String> documentIds   // null or empty = search all documents
) {}
