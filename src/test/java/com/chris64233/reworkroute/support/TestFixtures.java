package com.chris64233.reworkroute.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.service.BatchService;
import com.chris64233.reworkroute.web.dto.CreateBatchRequest;
import com.chris64233.reworkroute.web.dto.CreateNcRequest;

/**
 * 测试夹具：生成唯一编号并快速建立 批次 → 不合格记录 的前置数据。
 */
public final class TestFixtures {

    private static final AtomicLong SEQ = new AtomicLong();

    private TestFixtures() {
    }

    public static String unique(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet();
    }

    public static ProductionBatch createBatch(BatchService batchService, String total) {
        return batchService.createBatch(new CreateBatchRequest(
                unique("B"), "MAT-A", new BigDecimal(total)));
    }

    public static NonConformingRecord registerNc(BatchService batchService, String batchNo, String qty) {
        return batchService.registerNc(new CreateNcRequest(
                unique("NC"), batchNo, new BigDecimal(qty), "测试缺陷"));
    }

    public static List<String> ops(String... codes) {
        return List.of(codes);
    }
}
