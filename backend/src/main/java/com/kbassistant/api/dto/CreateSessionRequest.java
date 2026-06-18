package com.kbassistant.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
        @NotBlank(message = "userId must not be blank")
        String userId
) {}
