package com.chris64233.reworkroute.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class Dtos {

    private Dtos() {
    }

    public record CreateBatchRequest(@NotBlank String batchNo, String productCode,
                                     @NotNull @Min(1) Integer initialQuantity) {
    }

    public record OpenNcRequest(@NotBlank String ncNo, @NotBlank String batchNo, String defectCode,
                                @NotNull @Min(1) Integer affectedQuantity) {
    }

    public record SubmitDispositionRequest(
            @NotBlank String businessNo,
            @NotBlank String ncNo,
            @NotNull @Min(0) Integer reworkQty,
            @NotNull @Min(0) Integer scrapQty,
            @NotNull @Min(0) Integer concessionQty,
            java.util.List<@NotBlank String> requiredOperations) {
    }

    public record CompleteOperationRequest(@NotBlank String subBatchNo, @NotBlank String operationCode) {
    }

    public record SubmitReinspectionRequest(@NotBlank String subBatchNo, @NotNull String decision,
                                            String remark, String inspector) {
    }

    public record MergeBackRequest(String subBatchNo,
                                   /** 调用方持有的复验版本号；与最新版本不一致时拒绝（旧结果不放行）。 */
                                   Integer expectedVersion) {
    }

    public record AppendCorrectionRequest(@NotBlank String actionCode, @NotBlank String detail,
                                          String operator) {
    }
}
