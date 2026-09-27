package com.chris64233.reworkroute;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.reworkroute.domain.ReinspectionDecision;
import com.chris64233.reworkroute.service.BatchService;
import com.chris64233.reworkroute.service.DispositionService;
import com.chris64233.reworkroute.service.ReworkService;
import com.chris64233.reworkroute.service.TraceQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ExtendWith(DatabaseCleaner.class)
class TraceQueryServiceTest {

    @Autowired
    private BatchService batchService;
    @Autowired
    private DispositionService dispositionService;
    @Autowired
    private ReworkService reworkService;
    @Autowired
    private TraceQueryService traceService;
    @Autowired
    private TestFixtures fixtures;

    @Test
    void quantityTraceProgressReinspectionAndGenealogy() {
        String batchNo = fixtures.unique("B");
        String ncNo = fixtures.unique("NC");
        batchService.createBatch(batchNo, "P", 100);
        batchService.openNonconformance(ncNo, batchNo, "D01", 30);
        var d = dispositionService.submitDisposition("DSP-GENEALOGY", ncNo, 12, 8, 10,
                List.of("FIX", "CHECK")).disposition();
        String sub = d.getReworkSubBatch().getSubBatchNo();
        dispositionService.appendCorrection("DSP-GENEALOGY", "CAUSE", "根因分析", "qa");

        reworkService.completeOperation(sub, "FIX");
        reworkService.completeOperation(sub, "CHECK");
        reworkService.submitReinspection(sub, ReinspectionDecision.FAIL, "first fail", "qa");
        reworkService.submitReinspection(sub, ReinspectionDecision.PASS, "second pass", "qa");
        reworkService.mergeBack(sub, 2);

        // 不合格数量去向
        TraceQueryService.QuantityTrace qt = traceService.traceQuantity(ncNo);
        assertThat(qt.affectedQuantity()).isEqualTo(30);
        assertThat(qt.reworkQty()).isEqualTo(12);
        assertThat(qt.scrapQty()).isEqualTo(8);
        assertThat(qt.concessionQty()).isEqualTo(10);
        assertThat(qt.reworkMergedQty()).isEqualTo(12);
        assertThat(qt.reworkInProgressQty()).isZero();
        assertThat(qt.movements()).extracting(TraceQueryService.MovementView::type)
                .contains("BLOCK", "TO_REWORK", "SCRAP", "CONCESSION_RELEASE", "REWORK_MERGE");

        // 返工进度
        TraceQueryService.ReworkProgress progress = traceService.getReworkProgress(sub);
        assertThat(progress.requiredOperations()).containsExactly("FIX", "CHECK");
        assertThat(progress.completedOperations()).containsExactly("FIX", "CHECK");
        assertThat(progress.status()).isEqualTo("MERGED");

        // 复验结果历史与最新版本
        TraceQueryService.ReinspectionHistory history = traceService.getReinspectionHistory(sub);
        assertThat(history.results()).hasSize(2);
        assertThat(history.latest().version()).isEqualTo(2);
        assertThat(history.latest().decision()).isEqualTo("PASS");

        // 批次谱系全链路
        TraceQueryService.BatchGenealogy genealogy = traceService.getBatchGenealogy(batchNo);
        assertThat(genealogy.initialQuantity()).isEqualTo(100);
        assertThat(genealogy.availableQuantity()).isEqualTo(100 - 8); // 仅报废永久损失
        assertThat(genealogy.nonconformances()).hasSize(1);
        TraceQueryService.NcGenealogy nc = genealogy.nonconformances().get(0);
        assertThat(nc.businessNo()).isEqualTo("DSP-GENEALOGY");
        assertThat(nc.reworkQty() + nc.scrapQty() + nc.concessionQty())
                .isEqualTo(nc.affectedQuantity());
        assertThat(nc.rework().subBatchNo()).isEqualTo(sub);
        assertThat(nc.rework().reinspections()).hasSize(2);
        assertThat(nc.scrap().quantity()).isEqualTo(8);
        assertThat(nc.corrections()).hasSize(1);
    }
}
