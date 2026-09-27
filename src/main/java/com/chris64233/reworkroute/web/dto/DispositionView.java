package com.chris64233.reworkroute.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record DispositionView(String businessNo, String ncNo, String status,
                              BigDecimal reworkQuantity, BigDecimal scrapQuantity,
                              BigDecimal concessionQuantity, List<String> operations,
                              List<LineView> lines, List<CorrectionView> corrections,
                              boolean replayed) {

    public record LineView(String type, BigDecimal quantity, String subBatchNo, String scrapNo) {
    }
}
