package com.chris64233.reworkroute.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteOperationRequest(
        @NotBlank String operationCode,
        String operator) {
}
