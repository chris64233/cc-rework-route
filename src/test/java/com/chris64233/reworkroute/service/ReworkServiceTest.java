package com.chris64233.reworkroute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.reworkroute.domain.InspectionVerdict;
import com.chris64233.reworkroute.domain.NcStatus;
import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.domain.ReworkStatus;
import com.chris64233.reworkroute.domain.ReworkSubBatch;
import com.chris64233.reworkroute.support.BusinessRuleException;
import com.chris64233.reworkroute.support.TestFixtures;
import com.chris64233.reworkroute.web.dto.CompleteOperationRequest;
import com.chris64233.reworkroute.web.dto.CreateDispositionRequest;
import com.chris64233.reworkroute.web.dto.SubmitInspectionRequest;

@SpringBootTest
class ReworkServiceTest {

    @Autowired
    private BatchService batchService;
    @Autowired
    private DispositionService dispositionService;
    @Autowired
    private ReworkService reworkService;

    /** 建立 批次(100) → 不合格(10) → 返工5/报废3/让步2（OP1,OP2） 的已确认处置。 */
    private Fixture confirmedRework(String... ops) {
        ProductionBatch batch = TestFixtures.createBatch(batchService, "100");
        NonConformingRecord nc = TestFixtures.registerNc(batchService, batch.getBatchNo(), "10");
        String bizNo = TestFixtures.unique("D");
        dispositionService.createOrReplay(new CreateDispositionRequest(
                bizNo, nc.getNcNo(), new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("2"),
                java.util.List.of(ops), true));
        String subBatchNo = "RW-" + bizNo;
        return new Fixture(batch, nc, bizNo, subBatchNo);
    }

    private record Fixture(ProductionBatch batch, NonConformingRecord nc, String bizNo, String subBatchNo) {
    }

    private CompleteOperationRequest op(String code) {
        return new CompleteOperationRequest(code, "worker-1");
    }

    private SubmitInspectionRequest inspection(InspectionVerdict verdict, Integer expectedVersion) {
        return new SubmitInspectionRequest(verdict, expectedVersion, "inspector", null);
    }

    @Test
    void 工序必须按指定顺序完成_跳序与重复都被拒绝() {
        Fixture f = confirmedRework("OP1", "OP2");

        assertThatThrownBy(() -> reworkService.completeOperation(f.subBatchNo(), op("OP2")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("工序顺序违反");

        reworkService.completeOperation(f.subBatchNo(), op("OP1"));
        // 重复完成同一道
        assertThatThrownBy(() -> reworkService.completeOperation(f.subBatchNo(), op("OP1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("工序顺序违反");
        // 非指定工序
        assertThatThrownBy(() -> reworkService.completeOperation(f.subBatchNo(), op("OP9")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不在指定返工工序序列");

        reworkService.completeOperation(f.subBatchNo(), op("OP2"));
        ReworkSubBatch sub = reworkService.requireSubBatch(f.subBatchNo());
        assertThat(sub.getCompletedOperationCount()).isEqualTo(2);
        assertThat(sub.getStatus()).isEqualTo(ReworkStatus.OPERATIONS_DONE);
    }

    @Test
    void 工序未完成不能提交复验也不能放行() {
        Fixture f = confirmedRework("OP1", "OP2");
        reworkService.completeOperation(f.subBatchNo(), op("OP1"));
        assertThatThrownBy(() -> reworkService.submitInspection(
                f.subBatchNo(), inspection(InspectionVerdict.PASSED, 0)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("尚未全部完成");
        assertThatThrownBy(() -> reworkService.release(f.subBatchNo()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("尚未全部完成");
    }

    @Test
    void 复验合格后才能放行并重新并入可用库存_闭环关闭() {
        Fixture f = confirmedRework("OP1");
        reworkService.completeOperation(f.subBatchNo(), op("OP1"));

        // 无复验结果不能放行
        assertThatThrownBy(() -> reworkService.release(f.subBatchNo()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("尚未取得复验结果");

        reworkService.submitInspection(f.subBatchNo(), inspection(InspectionVerdict.PASSED, 0));
        // 放行前：可用 100-10+让步2 = 92
        assertThat(batchService.availableQuantity(f.batch().getBatchNo())).isEqualByComparingTo("92");
        reworkService.release(f.subBatchNo());

        ReworkSubBatch done = reworkService.requireSubBatch(f.subBatchNo());
        assertThat(done.getStatus()).isEqualTo(ReworkStatus.REINTEGRATED);
        assertThat(done.getReintegratedAt()).isNotNull();
        // 返工 5 回补：92 + 5 = 97（报废 3 永久核销）
        assertThat(batchService.availableQuantity(f.batch().getBatchNo())).isEqualByComparingTo("97");
        assertThat(batchService.requireNc(f.nc().getNcNo()).getStatus()).isEqualTo(NcStatus.CLOSED);

        // 放行幂等：重复放行不重复加库存
        reworkService.release(f.subBatchNo());
        assertThat(batchService.availableQuantity(f.batch().getBatchNo())).isEqualByComparingTo("97");
    }

    @Test
    void 复验不合格不得放行() {
        Fixture f = confirmedRework("OP1");
        reworkService.completeOperation(f.subBatchNo(), op("OP1"));
        reworkService.submitInspection(f.subBatchNo(), inspection(InspectionVerdict.FAILED, 0));
        assertThatThrownBy(() -> reworkService.release(f.subBatchNo()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不得放行");
        assertThat(batchService.availableQuantity(f.batch().getBatchNo())).isEqualByComparingTo("92");
    }

    @Test
    void 复验结果按版本递增_旧版本结论不得放行() {
        Fixture f = confirmedRework("OP1");
        reworkService.completeOperation(f.subBatchNo(), op("OP1"));
        // v1 合格
        reworkService.submitInspection(f.subBatchNo(), inspection(InspectionVerdict.PASSED, 0));
        // v2 不合格（基于 v1 提交）——最新版本变为 FAILED
        reworkService.submitInspection(f.subBatchNo(), inspection(InspectionVerdict.FAILED, 1));
        ReworkSubBatch sub = reworkService.requireSubBatch(f.subBatchNo());
        assertThat(sub.getInspectionVersion()).isEqualTo(2);

        // 即使 v1 曾合格，最新 v2 不合格也不得放行
        assertThatThrownBy(() -> reworkService.release(f.subBatchNo()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("v2");

        // 基于过期版本 v0 提交被拒绝
        assertThatThrownBy(() -> reworkService.submitInspection(
                f.subBatchNo(), inspection(InspectionVerdict.PASSED, 0)))
                .hasMessageContaining("复验版本冲突");

        // v3 重新合格后放行
        reworkService.submitInspection(f.subBatchNo(), inspection(InspectionVerdict.PASSED, 2));
        reworkService.release(f.subBatchNo());
        assertThat(batchService.requireNc(f.nc().getNcNo()).getStatus()).isEqualTo(NcStatus.CLOSED);
    }

    @Test
    void 已放行子批次不能再登记工序或复验() {
        Fixture f = confirmedRework("OP1");
        reworkService.completeOperation(f.subBatchNo(), op("OP1"));
        reworkService.submitInspection(f.subBatchNo(), inspection(InspectionVerdict.PASSED, 0));
        reworkService.release(f.subBatchNo());

        assertThatThrownBy(() -> reworkService.completeOperation(f.subBatchNo(), op("OP1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已放行");
        assertThatThrownBy(() -> reworkService.submitInspection(
                f.subBatchNo(), inspection(InspectionVerdict.PASSED, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已放行");
    }
}
