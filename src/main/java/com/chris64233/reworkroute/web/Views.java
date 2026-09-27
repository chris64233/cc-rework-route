package com.chris64233.reworkroute.web;

import java.time.Instant;
import java.util.List;

/** 实体到 JSON 的轻量视图，避免懒加载与枚举序号等序列化问题。 */
public final class Views {

    private Views() {
    }

    public record BatchView(String batchNo, String productCode, int initialQuantity,
                            int availableQuantity, Instant createdAt) {
    }

    public record NcView(String ncNo, String batchNo, String defectCode, int affectedQuantity,
                         String status, Instant createdAt, Instant disposedAt) {
    }

    public record DispositionView(String businessNo, String ncNo, int reworkQty, int scrapQty,
                                  int concessionQty, List<String> requiredOperations, String status,
                                  String reworkSubBatchNo, String scrapNo, boolean replayed,
                                  Instant confirmedAt) {
    }

    public record CorrectionView(Long id, String businessNo, String actionCode, String detail,
                                 String operator, Instant createdAt) {
    }

    public record SubBatchView(String subBatchNo, String batchNo, String ncNo, int quantity,
                               String status, List<String> requiredOperations,
                               List<String> completedOperations, Instant createdAt, Instant mergedAt) {
    }

    public record ReinspectionView(int resultVersion, String decision, String remark, String inspector,
                                   Instant createdAt) {
    }
}
