package com.chris64233.reworkroute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.reworkroute.domain.Disposition;
import com.chris64233.reworkroute.domain.ReinspectionDecision;
import com.chris64233.reworkroute.domain.ReworkStatus;
import com.chris64233.reworkroute.repo.DispositionRepository;
import com.chris64233.reworkroute.repo.ReinspectionResultRepository;
import com.chris64233.reworkroute.service.BatchService;
import com.chris64233.reworkroute.service.BusinessConflictException;
import com.chris64233.reworkroute.service.DispositionService;
import com.chris64233.reworkroute.service.ReworkService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ExtendWith(DatabaseCleaner.class)
class ReworkServiceTest {

    @Autowired
    private ReworkService reworkService;
    @Autowired
    private DispositionService dispositionService;
    @Autowired
    private BatchService batchService;
    @Autowired
    private DispositionRepository dispositionRepository;
    @Autowired
    private ReinspectionResultRepository resultRepository;
    @Autowired
    private TestFixtures fixtures;

    private String confirmedRework(String batchNo, String ncNo, int qty) {
        DispositionService.SubmitResult result = dispositionService.submitDisposition(
                fixtures.unique("DSP"), ncNo, qty, 0, 0, List.of("CUT", "WELD", "POLISH"));
        return result.disposition().getReworkSubBatch().getSubBatchNo();
    }

    @Test
    void operationsMustCompleteInSpecifiedOrder() {
        String batchNo = fixtures.unique("B");
        String ncNo = fixtures.unique("NC");
        batchService.createBatch(batchNo, "P", 50);
        batchService.openNonconformance(ncNo, batchNo, "D", 10);
        String sub = confirmedRework(batchNo, ncNo, 10);

        // 跳序提交第二道工序被拒。
        assertThatThrownBy(() -> reworkService.completeOperation(sub, "WELD"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("out of order");

        // 未知工序被拒。
        assertThatThrownBy(() -> reworkService.completeOperation(sub, "PAINT"))
                .isInstanceOf(BusinessConflictException.class);

        reworkService.completeOperation(sub, "CUT");
        assertThat(reworkService.completeOperation(sub, "WELD").getStatus())
                .isEqualTo(ReworkStatus.IN_PROGRESS);
        // 重复提交同一工序：此时期望 POLISH，再报 WELD 即跳序。
        assertThatThrownBy(() -> reworkService.completeOperation(sub, "WELD"))
                .isInstanceOf(BusinessConflictException.class);

        assertThat(reworkService.completeOperation(sub, "POLISH").getStatus())
                .isEqualTo(ReworkStatus.COMPLETED);

        // 全部完成后再提交工序冲突。
        assertThatThrownBy(() -> reworkService.completeOperation(sub, "POLISH"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void reinspectionRequiresAllOperationsCompleted() {
        String batchNo = fixtures.unique("B");
        String ncNo = fixtures.unique("NC");
        batchService.createBatch(batchNo, "P", 50);
        batchService.openNonconformance(ncNo, batchNo, "D", 10);
        String sub = confirmedRework(batchNo, ncNo, 10);

        reworkService.completeOperation(sub, "CUT");
        assertThatThrownBy(() -> reworkService.submitReinspection(sub, ReinspectionDecision.PASS,
                null, "i"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("all required operations");

        reworkService.completeOperation(sub, "WELD");
        reworkService.completeOperation(sub, "POLISH");
        var pass = reworkService.submitReinspection(sub, ReinspectionDecision.PASS, null, "i");
        assertThat(pass.getResultVersion()).isEqualTo(1);
    }

    @Test
    void failedReinspectionCannotMerge() {
        String batchNo = fixtures.unique("B");
        String ncNo = fixtures.unique("NC");
        batchService.createBatch(batchNo, "P", 50);
        batchService.openNonconformance(ncNo, batchNo, "D", 10);
        String sub = confirmedRework(batchNo, ncNo, 10);
        completeAll(sub);

        reworkService.submitReinspection(sub, ReinspectionDecision.FAIL, "still defective", "i");
        assertThatThrownBy(() -> reworkService.mergeBack(sub, null))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("FAIL");
        // 未并回，可用库存仍为 50 - 10 = 40。
        assertThat(batchService.getBatch(batchNo).getAvailableQuantity()).isEqualTo(40);
    }

    @Test
    void staleReinspectionVersionCannotRelease() {
        String batchNo = fixtures.unique("B");
        String ncNo = fixtures.unique("NC");
        batchService.createBatch(batchNo, "P", 50);
        batchService.openNonconformance(ncNo, batchNo, "D", 10);
        String sub = confirmedRework(batchNo, ncNo, 10);
        completeAll(sub);

        reworkService.submitReinspection(sub, ReinspectionDecision.PASS, "v1", "i");
        // v1 之后复验出现更新版本（仍允许提交复验结论），调用方若持 v1 放行必须拒绝。
        reworkService.submitReinspection(sub, ReinspectionDecision.PASS, "v2", "i");

        assertThatThrownBy(() -> reworkService.mergeBack(sub, 1))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("stale reinspection version");
        assertThat(batchService.getBatch(batchNo).getAvailableQuantity()).isEqualTo(40);

        // 用最新版本放行成功，数量回到 50。
        assertThat(reworkService.mergeBack(sub, 2).getStatus()).isEqualTo(ReworkStatus.MERGED);
        assertThat(batchService.getBatch(batchNo).getAvailableQuantity()).isEqualTo(50);
        fixtures.assertQuantityConservation(ncNo, 50);
    }

    @Test
    void mergeRequiresLatestPassAndIsIdempotentlyGuarded() {
        String batchNo = fixtures.unique("B");
        String ncNo = fixtures.unique("NC");
        batchService.createBatch(batchNo, "P", 50);
        batchService.openNonconformance(ncNo, batchNo, "D", 10);
        String sub = confirmedRework(batchNo, ncNo, 10);
        completeAll(sub);

        // 没有任何复验结果时不能并回。
        assertThatThrownBy(() -> reworkService.mergeBack(sub, null))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("no reinspection result");

        reworkService.submitReinspection(sub, ReinspectionDecision.PASS, null, "i");
        reworkService.mergeBack(sub, null);
        // 已并回的子批次不能重复并回（否则数量翻倍）。
        assertThatThrownBy(() -> reworkService.mergeBack(sub, null))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("already merged");
        assertThat(batchService.getBatch(batchNo).getAvailableQuantity()).isEqualTo(50);
    }

    @Test
    void fullReworkClosedLoop() {
        String batchNo = fixtures.unique("B");
        String ncNo = fixtures.unique("NC");
        batchService.createBatch(batchNo, "P", 100);
        batchService.openNonconformance(ncNo, batchNo, "D", 30);
        // 30 = 返工 12 + 报废 8 + 让步 10
        Disposition d = dispositionService.submitDisposition(fixtures.unique("DSP"), ncNo,
                12, 8, 10, List.of("FIX")).disposition();
        String sub = d.getReworkSubBatch().getSubBatchNo();

        // 冻结 30、让步释放 10 后可用 80。
        assertThat(batchService.getBatch(batchNo).getAvailableQuantity()).isEqualTo(80);

        reworkService.completeOperation(sub, "FIX");
        reworkService.submitReinspection(sub, ReinspectionDecision.PASS, "ok", "qa");
        reworkService.mergeBack(sub, 1);

        // 返工 12 并回 => 92；报废 8 永久出库。
        assertThat(batchService.getBatch(batchNo).getAvailableQuantity()).isEqualTo(92);
        assertThat(resultRepository.findBySubBatchIdOrderByResultVersionAsc(
                d.getReworkSubBatch().getId())).hasSize(1);
        assertThat(dispositionRepository.findByNcId(d.getNc().getId())).isPresent();
        fixtures.assertQuantityConservation(ncNo, 100);
    }

    private void completeAll(String sub) {
        for (String op : List.of("CUT", "WELD", "POLISH")) {
            reworkService.completeOperation(sub, op);
        }
    }
}
