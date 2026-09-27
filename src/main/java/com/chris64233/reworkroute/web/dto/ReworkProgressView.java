package com.chris64233.reworkroute.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReworkProgressView(String subBatchNo, String ncNo, String businessNo, BigDecimal quantity,
                                 List<String> requiredOperations, int completedOperationCount,
                                 int requiredOperationCount, String currentOperation, String status,
                                 int inspectionVersion, String latestVerdict, String reintegratedAt) {
}
