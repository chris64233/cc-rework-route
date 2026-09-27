package com.chris64233.reworkroute.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 处置内容指纹：业务号相同而内容不同的重放据此判定为冲突。
 *
 * <p>指纹覆盖不合格记录、返工/报废/让步三部分数量以及返工指定工序序列，
 * 数量按 stripTrailingZeros 归一化（1.0 与 1.00 视为相同）。</p>
 */
public final class ContentHasher {

    private ContentHasher() {
    }

    public static String hashDisposition(String ncNo, BigDecimal rework, BigDecimal scrap,
                                         BigDecimal concession, List<String> operations) {
        String canonical = "nc=" + ncNo
                + "|rework=" + normalize(rework)
                + "|scrap=" + normalize(scrap)
                + "|concession=" + normalize(concession)
                + "|ops=" + String.join(">", operations);
        return sha256(canonical);
    }

    private static String normalize(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
