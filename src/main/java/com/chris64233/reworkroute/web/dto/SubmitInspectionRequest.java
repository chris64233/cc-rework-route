package com.chris64233.reworkroute.web.dto;

import com.chris64233.reworkroute.domain.InspectionVerdict;

import jakarta.validation.constraints.NotNull;

/**
 * 提交复验结果。
 *
 * @param expectedVersion 客户端认为当前应有的最新版本号；服务端实际版本与此不一致时拒绝，
 *                        防止旧版本结果覆盖新版本（乐观并发控制）。
 */
public record SubmitInspectionRequest(
        @NotNull InspectionVerdict verdict,
        Integer expectedVersion,
        String inspector,
        String remark) {
}
