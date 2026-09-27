package com.chris64233.reworkroute.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 数量与工序字符串的基础校验工具。
 */
public final class Quantities {

    private Quantities() {
    }

    /** 要求数量非空、非负。 */
    public static BigDecimal requireNonNegative(BigDecimal value, String name) {
        if (value == null) {
            throw new BusinessRuleException(name + " 不能为空");
        }
        if (value.signum() < 0) {
            throw new BusinessRuleException(name + " 不能为负数: " + value);
        }
        return value;
    }

    /** 要求数量严格为正。 */
    public static BigDecimal requirePositive(BigDecimal value, String name) {
        requireNonNegative(value, name);
        if (value.signum() == 0) {
            throw new BusinessRuleException(name + " 必须大于 0");
        }
        return value;
    }

    /**
     * 解析返工指定工序序列：返工数量大于 0 时必须至少指定一个工序；
     * 忽略空白项，不允许重复工序编码。
     */
    public static List<String> parseOperations(String raw, boolean reworkPresent) {
        List<String> operations = new ArrayList<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String code = part.trim();
                if (!code.isEmpty()) {
                    if (operations.contains(code)) {
                        throw new BusinessRuleException("返工工序重复: " + code);
                    }
                    operations.add(code);
                }
            }
        }
        if (reworkPresent && operations.isEmpty()) {
            throw new BusinessRuleException("返工数量大于 0 时必须指定至少一道返工工序");
        }
        return operations;
    }
}
