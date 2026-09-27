package com.chris64233.reworkroute.web.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 提出处置决定。
 *
 * @param operations 返工指定工序，按顺序完成；reworkQuantity 大于 0 时必填
 * @param confirm    true 时在同一调用内原子确认
 */
public record CreateDispositionRequest(
        @NotBlank String businessNo,
        @NotBlank String ncNo,
        @NotNull BigDecimal reworkQuantity,
        @NotNull BigDecimal scrapQuantity,
        @NotNull BigDecimal concessionQuantity,
        List<String> operations,
        boolean confirm) {
}
