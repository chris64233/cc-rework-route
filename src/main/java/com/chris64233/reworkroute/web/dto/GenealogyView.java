package com.chris64233.reworkroute.web.dto;

import java.util.List;

/**
 * 批次谱系：以边的形式描述不合格数量从原批次到返工子批次、报废、让步及复验回补的完整流转。
 */
public record GenealogyView(String rootBatchNo, List<EdgeView> edges) {

    public record EdgeView(String type, String source, String target, String ncNo,
                           String businessNo, String quantity, String createdAt) {
    }
}
