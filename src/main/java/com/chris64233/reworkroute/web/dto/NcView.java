package com.chris64233.reworkroute.web.dto;

import java.math.BigDecimal;

public record NcView(String ncNo, String batchNo, String materialCode, BigDecimal affectedQuantity,
                     BigDecimal consumedQuantity, BigDecimal remainingQuantity, String status,
                     String defectDescription) {
}
