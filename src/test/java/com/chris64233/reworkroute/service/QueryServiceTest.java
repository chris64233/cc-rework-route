package com.chris64233.reworkroute.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.reworkroute.domain.GenealogyType;
import com.chris64233.reworkroute.domain.InspectionVerdict;
import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.support.TestFixtures;
import com.chris64233.reworkroute.web.dto.AddCorrectionRequest;
import com.chris64233.reworkroute.web.dto.CompleteOperationRequest;
import com.chris64233.reworkroute.web.dto.CreateDispositionRequest;
import com.chris64233.reworkroute.web.dto.GenealogyView;
import com.chris64233.reworkroute.web.dto.InspectionHistoryView;
import com.chris64233.reworkroute.web.dto.QuantityDestinationView;
import com.chris64233.reworkroute.web.dto.ReworkProgressView;
import com.chris64233.reworkroute.web.dto.SubmitInspectionRequest;

@SpringBootTest
class QueryServiceTest {

    @Autowired
    private BatchService batchService;
    @Autowired
    private DispositionService dispositionService;
    @Autowired
    private ReworkService reworkService;
    @Autowired
    private QueryService queryService;

    @Test
    void 查询数量去向返工进度复验结果与完整谱系() {
        ProductionBatch batch = TestFixtures.createBatch(batchService, "100");
        NonConformingRecord nc = TestFixtures.registerNc(batchService, batch.getBatchNo(), "10");
        String bizNo = TestFixtures.unique("D");
        dispositionService.createOrReplay(new CreateDispositionRequest(
                bizNo, nc.getNcNo(), new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("2"),
                java.util.List.of("OP1", "OP2"), true));
        String subBatchNo = "RW-" + bizNo;

        // 数量去向
        QuantityDestinationView destination = queryService.getQuantityDestination(nc.getNcNo());
        assertThat(destination.reworkQuantity()).isEqualByComparingTo("5");
        assertThat(destination.scrapQuantity()).isEqualByComparingTo("3");
        assertThat(destination.concessionQuantity()).isEqualByComparingTo("2");
        assertThat(destination.remainingQuantity()).isEqualByComparingTo("0");
        assertThat(destination.items()).hasSize(3);
        assertThat(destination.items()).extracting(QuantityDestinationView.Item::type)
                .containsExactly("REWORK", "SCRAP", "CONCESSION");
        assertThat(destination.items()).extracting(QuantityDestinationView.Item::referenceNo)
                .contains(subBatchNo, "SCRAP-" + bizNo, bizNo);

        // 返工进度
        reworkService.completeOperation(subBatchNo, new CompleteOperationRequest("OP1", "w"));
        ReworkProgressView progress = queryService.getReworkProgress(subBatchNo);
        assertThat(progress.requiredOperations()).containsExactly("OP1", "OP2");
        assertThat(progress.completedOperationCount()).isEqualTo(1);
        assertThat(progress.currentOperation()).isEqualTo("OP2");
        assertThat(queryService.listReworkByNc(nc.getNcNo())).hasSize(1);

        // 复验结果：v1 不合格、v2 合格，历史按版本完整保留
        reworkService.completeOperation(subBatchNo, new CompleteOperationRequest("OP2", "w"));
        reworkService.submitInspection(subBatchNo,
                new SubmitInspectionRequest(InspectionVerdict.FAILED, 0, "qa", "尺寸超差"));
        reworkService.submitInspection(subBatchNo,
                new SubmitInspectionRequest(InspectionVerdict.PASSED, 1, "qa", "返工后合格"));
        InspectionHistoryView history = queryService.getInspectionHistory(subBatchNo);
        assertThat(history.results()).hasSize(2);
        assertThat(history.results()).extracting(InspectionHistoryView.ResultView::version)
                .containsExactly(1, 2);
        assertThat(history.results().get(0).verdict()).isEqualTo("FAILED");
        assertThat(history.results().get(1).verdict()).isEqualTo("PASSED");
        assertThat(history.released()).isFalse();

        reworkService.release(subBatchNo);

        // 谱系：REWORK / SCRAP / CONCESSION 三条确认时生成，REINTEGRATE 放行时生成
        GenealogyView genealogy = queryService.getGenealogy(nc.getNcNo());
        assertThat(genealogy.rootBatchNo()).isEqualTo(batch.getBatchNo());
        assertThat(genealogy.edges()).hasSize(4);
        assertThat(genealogy.edges()).extracting(GenealogyView.EdgeView::type)
                .containsExactly("REWORK", "SCRAP", "CONCESSION", "REINTEGRATE");
        var reintegrate = genealogy.edges().get(3);
        assertThat(reintegrate.source()).isEqualTo(subBatchNo);
        assertThat(reintegrate.target()).isEqualTo(batch.getBatchNo());
        assertThat(new BigDecimal(reintegrate.quantity())).isEqualByComparingTo("5");
        // 报废边指向报废记录
        assertThat(genealogy.edges()).filteredOn(e -> e.type().equals(GenealogyType.SCRAP.name()))
                .singleElement().extracting(GenealogyView.EdgeView::target)
                .isEqualTo("SCRAP-" + bizNo);

        // 处置视图包含追加的纠正记录，谱系完整
        dispositionService.addCorrection(bizNo,
                new AddCorrectionRequest("ROOT_CAUSE", "定位偏差", "qa"));
        var disposition = queryService.getDisposition(bizNo);
        assertThat(disposition.corrections()).hasSize(1);
        assertThat(disposition.corrections().get(0).content()).isEqualTo("定位偏差");
        assertThat(queryService.getInspectionHistory(subBatchNo).released()).isTrue();
        assertThat(queryService.getNc(nc.getNcNo()).status()).isEqualTo("CLOSED");
    }
}
