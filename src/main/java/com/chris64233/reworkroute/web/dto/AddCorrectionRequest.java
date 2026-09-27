package com.chris64233.reworkroute.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AddCorrectionRequest(
        @NotBlank String correctionType,
        @NotBlank String content,
        String operator) {
}
