package com.chris64233.reworkroute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.reworkroute.domain.Disposition;
import com.chris64233.reworkroute.domain.DispositionStatus;
import com.chris64233.reworkroute.domain.NcStatus;
import com.chris64233.reworkroute.repo.DispositionRepository;
import com.chris64233.reworkroute.repo.NonconformingRecordRepository;
import com.chris64233.reworkroute.repo.ProductionBatchRepository;
import com.chris64233.reworkroute.repo.ScrapRecordRepository;
import com.chris64233.reworkroute.service.BatchService;
import com.chris64233.reworkroute.service.BusinessConflictException;
import com.chris64233.reworkroute.service.DispositionService;
import com.chris64233.reworkroute.service.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ExtendWith(DatabaseCleaner.class)
class DispositionServiceTest {

    @Autowired
    private DispositionService dispositionService;
    @Autowired
    private BatchService batchService;
    @Autowired
    private DispositionRepository dispositionRepository;
    @Autowired
    private NonconformingRecordRepository ncRepository;
    @Autowired
    private ProductionBatchRepository batchRepository;
    @Autowired
    private ScrapRecordRepository scrapRepository;
    @Autowired
    private TestFixtures fixtures;

    @Test
    void splitQuantitiesMustSumToAffectedQuantity() {
        String ncNo = fixtures.batchWithNc(100, 30);

        assertThatThrownBy(() -> dispositionService.submitDisposition(
                fixtures.unique("DSP"), ncNo, 10, 10, 5, List.of("OP1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must sum to affectedQuantity");

        // 校验失败不得留下处置或报废记录。
        assertThat(dispositionRepository.count()).isZero();
        assertThat(scrapRepository.count()).isZero();
        assertThat(batchService.getNc(ncNo).getStatus()).isEqualTo(NcStatus.OPEN);
    }

    @Test
    void negativeQuantitiesRejectedAtomically() {
        String ncNo = fixtures.batchWithNc(100, 10);
        assertThatThrownBy(() -> dispositionService.submitDisposition(
                fixtures.unique("DSP"), ncNo, -1, 11, 0, List.of("OP1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not be negative");
        assertThat(dispositionRepository.count()).isZero();
    }

    @Test
    void confirmAtomicallyCreatesSubBatchScrapAndConcessionRelease() {
        String batchNo = fixtures.unique("B");
        String ncNo = fixtures.unique("NC");
        batchService.createBatch(batchNo, "P-1", 100);
        batchService.openNonconformance(ncNo, batchNo, "D-01", 40);

        DispositionService.SubmitResult result = dispositionService.submitDisposition(
                "DSP-SPLIT-1", ncNo, 20, 10, 10, List.of("OP1", "OP2"));

        assertThat(result.replayed()).isFalse();
        Disposition d = result.disposition();
        assertThat(d.getStatus()).isEqualTo(DispositionStatus.CONFIRMED);
        assertThat(d.getReworkSubBatch()).isNotNull();
        assertThat(d.getReworkSubBatch().getQuantity()).isEqualTo(20);
        assertThat(d.getScrapRecord()).isNotNull();
        assertThat(d.getScrapRecord().getQuantity()).isEqualTo(10);

        // 冻结 40：让步 10 回到可用 => 可用 = 100 - 40 + 10 = 70；返工/报废在途不出库。
        assertThat(batchService.getBatch(batchNo).getAvailableQuantity()).isEqualTo(70);
        assertThat(batchService.getNc(ncNo).getStatus()).isEqualTo(NcStatus.DISPOSED);
        fixtures.assertQuantityConservation(ncNo, 100);
    }

    @Test
    void reworkWithoutRequiredOperationsRejected() {
        String ncNo = fixtures.batchWithNc(100, 10);
        assertThatThrownBy(() -> dispositionService.submitDisposition(
                fixtures.unique("DSP"), ncNo, 10, 0, 0, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("requiredOperations");
    }

    @Test
    void sameQuantityCannotBeConsumedTwice() {
        String ncNo = fixtures.batchWithNc(100, 10);
        dispositionService.submitDisposition("DSP-ONCE", ncNo, 10, 0, 0, List.of("OP1"));

        // 不同业务号再次消费同一 NC 必须冲突。
        assertThatThrownBy(() -> dispositionService.submitDisposition(
                "DSP-AGAIN", ncNo, 10, 0, 0, List.of("OP1")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("already disposed");
        assertThat(dispositionRepository.count()).isEqualTo(1);
    }

    @Test
    void replaySameContentReturnsOriginalResult() {
        String ncNo = fixtures.batchWithNc(100, 10);
        DispositionService.SubmitResult first = dispositionService.submitDisposition(
                "DSP-IDEM", ncNo, 6, 2, 2, List.of("OP1", "OP2"));

        DispositionService.SubmitResult replay = dispositionService.submitDisposition(
                "DSP-IDEM", ncNo, 6, 2, 2, List.of("OP1", "OP2"));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.disposition().getId()).isEqualTo(first.disposition().getId());
        assertThat(dispositionRepository.count()).isEqualTo(1);
        assertThat(scrapRepository.count()).isEqualTo(1);
        // 重放不得重复释放让步数量。
        assertThat(batchRepository.findByBatchNo(
                        ncRepository.findByNcNo(ncNo).orElseThrow().getBatch().getBatchNo())
                .orElseThrow().getAvailableQuantity())
                .isEqualTo(100 - 10 + 2);
    }

    @Test
    void replayDifferentContentConflicts() {
        String ncNo = fixtures.batchWithNc(100, 10);
        dispositionService.submitDisposition("DSP-X", ncNo, 6, 2, 2, List.of("OP1"));

        assertThatThrownBy(() -> dispositionService.submitDisposition(
                "DSP-X", ncNo, 8, 2, 0, List.of("OP1")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("different content");

        // 工序列表不同也算内容不同。
        assertThatThrownBy(() -> dispositionService.submitDisposition(
                "DSP-X", ncNo, 6, 2, 2, List.of("OP1", "OP9")))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void confirmedDispositionCannotChangeOnlyAppendCorrections() {
        String ncNo = fixtures.batchWithNc(100, 10);
        dispositionService.submitDisposition("DSP-LOCK", ncNo, 5, 3, 2, List.of("OP1"));

        // 同业务号改内容 = 冲突，而不是覆盖。
        assertThatThrownBy(() -> dispositionService.submitDisposition(
                "DSP-LOCK", ncNo, 0, 10, 0, List.of("OP1")))
                .isInstanceOf(BusinessConflictException.class);

        dispositionService.appendCorrection("DSP-LOCK", "CAUSE_ANALYSIS", "根因为刀具磨损", "qa");
        dispositionService.appendCorrection("DSP-LOCK", "CONTAINMENT", "已隔离同批库存", "qa");

        Disposition d = dispositionRepository.findByBusinessNo("DSP-LOCK").orElseThrow();
        assertThat(d.getReworkQty()).isEqualTo(5);
        assertThat(d.getCorrections()).hasSize(2);
        assertThat(d.getCorrections().get(0).getActionCode()).isEqualTo("CAUSE_ANALYSIS");
    }
}
