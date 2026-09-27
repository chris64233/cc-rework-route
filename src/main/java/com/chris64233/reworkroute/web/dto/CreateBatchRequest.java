package com.chris64233.reworkroute.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBatchRequest(
        @NotBlank String batchNo,
        @NotBlank String materialCode,
        @NotNull BigDecimal totalQuantity) {
}
