package com.chris64233.reworkroute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.reworkroute.domain.DispositionStatus;
import com.chris64233.reworkroute.domain.DispositionType;
import com.chris64233.reworkroute.domain.NcStatus;
import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.repo.ReworkSubBatchRepository;
import com.chris64233.reworkroute.support.BusinessRuleException;
import com.chris64233.reworkroute.support.IdempotencyConflictException;
import com.chris64233.reworkroute.support.TestFixtures;
import com.chris64233.reworkroute.web.dto.AddCorrectionRequest;
import com.chris64233.reworkroute.web.dto.CreateDispositionRequest;

@SpringBootTest
class DispositionServiceTest {

    @Autowired
    private BatchService batchService;
    @Autowired
    private DispositionService dispositionService;
    @Autowired
    private QueryService queryService;
    @Autowired
    private ReworkSubBatchRepository reworkRepository;

    private NonConformingRecord newNc(String qty) {
        ProductionBatch batch = TestFixtures.createBatch(batchService, "100");
        return TestFixtures.registerNc(batchService, batch.getBatchNo(), qty);
    }

    private CreateDispositionRequest request(String bizNo, String ncNo,
                                             String rework, String scrap, String concession,
                                             String... ops) {
        return new CreateDispositionRequest(bizNo, ncNo, new BigDecimal(rework),
                new BigDecimal(scrap), new BigDecimal(concession),
                ops.length == 0 ? java.util.List.of() : java.util.List.of(ops), false);
    }

    @Test
    void 三部分数量之和必须等于受影响数量() {
        NonConformingRecord nc = newNc("10");
        CreateDispositionRequest bad = request("D-SUM", nc.getNcNo(), "4", "4", "1", "OP1");
        assertThatThrownBy(() -> dispositionService.createOrReplay(bad))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("必须与原记录受影响数量");
        // 失败后不合格记录仍为 OPEN，数量未被消费
        NonConformingRecord reloaded = batchService.requireNc(nc.getNcNo());
        assertThat(reloaded.getStatus()).isEqualTo(NcStatus.OPEN);
        assertThat(reloaded.getConsumedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void 数量不能为负数且返工时必须指定工序() {
        NonConformingRecord nc = newNc("10");
        assertThatThrownBy(() -> dispositionService.createOrReplay(
                request("D-NEG", nc.getNcNo(), "-1", "5", "6", "OP1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不能为负数");

        assertThatThrownBy(() -> dispositionService.createOrReplay(
                request("D-NOOPS", nc.getNcNo(), "4", "6", "0")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("返工工序");
    }

    @Test
    void 确认处置原子生成子批次报废让步并入库存并消费全部数量() {
        ProductionBatch batch = TestFixtures.createBatch(batchService, "100");
        NonConformingRecord nc = TestFixtures.registerNc(batchService, batch.getBatchNo(), "10");
        // 登记即冻结 10
        assertThat(batchService.availableQuantity(batch.getBatchNo())).isEqualByComparingTo("90");

        String bizNo = TestFixtures.unique("D");
        dispositionService.createOrReplay(request(bizNo, nc.getNcNo(), "5", "3", "2", "OP1", "OP2"));
        dispositionService.confirm(bizNo);

        var view = queryService.getDisposition(bizNo);
        assertThat(view.status()).isEqualTo(DispositionStatus.CONFIRMED.name());
        assertThat(view.lines()).hasSize(3);
        // 返工子批次
        var reworkLine = view.lines().stream()
                .filter(l -> l.type().equals(DispositionType.REWORK.name())).findFirst().orElseThrow();
        assertThat(reworkLine.subBatchNo()).isEqualTo("RW-" + bizNo);
        assertThat(reworkLine.quantity()).isEqualByComparingTo("5");
        // 报废记录
        var scrapLine = view.lines().stream()
                .filter(l -> l.type().equals(DispositionType.SCRAP.name())).findFirst().orElseThrow();
        assertThat(scrapLine.scrapNo()).isEqualTo("SCRAP-" + bizNo);
        assertThat(scrapLine.quantity()).isEqualByComparingTo("3");

        // 原始数量全部消费；让步 2 回到可用库存，报废 3 永久核销，返工 5 待复验
        NonConformingRecord done = batchService.requireNc(nc.getNcNo());
        assertThat(done.getConsumedQuantity()).isEqualByComparingTo("10");
        assertThat(done.remainingQuantity()).isEqualByComparingTo("0");
        assertThat(done.getStatus()).isEqualTo(NcStatus.IN_REWORK);
        assertThat(batchService.availableQuantity(batch.getBatchNo())).isEqualByComparingTo("92");
    }

    @Test
    void 无返工部分确认后不合格记录直接关闭() {
        NonConformingRecord nc = newNc("8");
        String bizNo = TestFixtures.unique("D");
        dispositionService.createOrReplay(request(bizNo, nc.getNcNo(), "0", "3", "5"));
        dispositionService.confirm(bizNo);
        assertThat(batchService.requireNc(nc.getNcNo()).getStatus()).isEqualTo(NcStatus.CLOSED);
    }

    @Test
    void 同一原始数量不能被两个处置重复消费_失败者不留下任何结果() {
        ProductionBatch batch = TestFixtures.createBatch(batchService, "100");
        NonConformingRecord nc = TestFixtures.registerNc(batchService, batch.getBatchNo(), "10");
        // 两个待确认处置可同时存在，但只有一个能确认成功
        String d1 = TestFixtures.unique("D");
        String d2 = TestFixtures.unique("D");
        dispositionService.createOrReplay(request(d1, nc.getNcNo(), "4", "3", "3", "OP1"));
        dispositionService.createOrReplay(request(d2, nc.getNcNo(), "6", "2", "2", "OP2"));

        dispositionService.confirm(d1);
        assertThatThrownBy(() -> dispositionService.confirm(d2))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不能重复消费");

        // 失败的处置没有生成任何子批次/报废记录（无部分结果）
        assertThat(reworkRepository.findBySubBatchNo("RW-" + d2)).isEmpty();
        var loserView = queryService.getDisposition(d2);
        assertThat(loserView.status()).isEqualTo("PENDING");
        assertThat(loserView.lines()).allSatisfy(line -> {
            assertThat(line.subBatchNo()).isNull();
            assertThat(line.scrapNo()).isNull();
        });
        // 库存只反映获胜者 d1：让步 3 回补
        assertThat(batchService.availableQuantity(batch.getBatchNo())).isEqualByComparingTo("93");
    }

    @Test
    void 业务号幂等_相同内容重放返回原处置() {
        NonConformingRecord nc = newNc("10");
        String bizNo = TestFixtures.unique("D");
        var first = dispositionService.createOrReplay(request(bizNo, nc.getNcNo(), "4", "6", "0", "OP1"));
        assertThat(first.replayed()).isFalse();

        var replay = dispositionService.createOrReplay(request(bizNo, nc.getNcNo(),
                // 数值书写形式不同但等值不算冲突
                "4.0", "6.00", "0.000", "OP1"));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.order().getId()).isEqualTo(first.order().getId());
        assertThat(batchService.requireNc(nc.getNcNo()).getStatus()).isEqualTo(NcStatus.OPEN);

        // 已确认后相同内容重放不会重复消费
        dispositionService.confirm(bizNo);
        var replayAfterConfirm = dispositionService.createOrReplay(
                request(bizNo, nc.getNcNo(), "4", "6", "0", "OP1"));
        assertThat(replayAfterConfirm.replayed()).isTrue();
        assertThat(batchService.requireNc(nc.getNcNo()).getConsumedQuantity()).isEqualByComparingTo("10");
        assertThat(reworkRepository.findBySubBatchNo("RW-" + bizNo)).isPresent();
    }

    @Test
    void 业务号相同内容不同返回冲突() {
        NonConformingRecord nc = newNc("10");
        String bizNo = TestFixtures.unique("D");
        dispositionService.createOrReplay(request(bizNo, nc.getNcNo(), "4", "6", "0", "OP1"));
        assertThatThrownBy(() -> dispositionService.createOrReplay(
                request(bizNo, nc.getNcNo(), "5", "5", "0", "OP1")))
                .isInstanceOf(IdempotencyConflictException.class);
        // 工序不同也算内容不同
        assertThatThrownBy(() -> dispositionService.createOrReplay(
                request(bizNo, nc.getNcNo(), "4", "6", "0", "OP1", "OP2")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void 已确认处置不能修改只能追加纠正记录() {
        NonConformingRecord nc = newNc("10");
        String bizNo = TestFixtures.unique("D");
        dispositionService.createOrReplay(request(bizNo, nc.getNcNo(), "4", "6", "0", "OP1"));
        dispositionService.confirm(bizNo);

        // 同号改数量 → 冲突，原处置内容不变
        assertThatThrownBy(() -> dispositionService.createOrReplay(
                request(bizNo, nc.getNcNo(), "3", "7", "0", "OP1")))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(dispositionService.requireByBusinessNo(bizNo).getReworkQuantity())
                .isEqualByComparingTo("4");

        // 待确认处置不允许追加纠正
        NonConformingRecord otherNc = newNc("6");
        String pending = TestFixtures.unique("D");
        dispositionService.createOrReplay(request(pending, otherNc.getNcNo(), "4", "2", "0", "OP1"));
        assertThatThrownBy(() -> dispositionService.addCorrection(pending,
                new AddCorrectionRequest("ROOT_CAUSE", "原因", "op")))
                .isInstanceOf(BusinessRuleException.class);

        // 已确认处置可追加纠正，且纠正记录累积保留
        dispositionService.addCorrection(bizNo,
                new AddCorrectionRequest("ROOT_CAUSE", "刀具磨损", "qa"));
        dispositionService.addCorrection(bizNo,
                new AddCorrectionRequest("REWORK_INSTRUCTION", "按 OP1 返工", "qa"));
        var reloaded = queryService.getDisposition(bizNo);
        assertThat(reloaded.corrections()).hasSize(2);
        assertThat(reloaded.corrections().get(0).content()).isEqualTo("刀具磨损");
        assertThat(dispositionService.requireByBusinessNo(bizNo).getStatus())
                .isEqualTo(DispositionStatus.CONFIRMED);
    }
}
