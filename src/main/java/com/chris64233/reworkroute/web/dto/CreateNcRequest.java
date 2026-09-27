package com.chris64233.reworkroute.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateNcRequest(
        @NotBlank String ncNo,
        @NotBlank String batchNo,
        @NotNull BigDecimal affectedQuantity,
        String defectDescription) {
}
