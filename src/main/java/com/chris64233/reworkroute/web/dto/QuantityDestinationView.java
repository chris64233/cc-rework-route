package com.chris64233.reworkroute.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 不合格数量去向：三部分拆分、已生成结果及剩余未消费数量。
 */
public record QuantityDestinationView(String ncNo, String batchNo, BigDecimal affectedQuantity,
                                      BigDecimal consumedQuantity, BigDecimal remainingQuantity,
                                      BigDecimal reworkQuantity, BigDecimal scrapQuantity,
                                      BigDecimal concessionQuantity, List<Item> items) {

    public record Item(String type, BigDecimal quantity, String referenceNo, String status) {
    }
}
