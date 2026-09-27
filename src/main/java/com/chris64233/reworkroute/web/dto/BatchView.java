package com.chris64233.reworkroute.web.dto;

import java.math.BigDecimal;

public record BatchView(String batchNo, String materialCode, BigDecimal totalQuantity,
                        BigDecimal availableQuantity) {
}
