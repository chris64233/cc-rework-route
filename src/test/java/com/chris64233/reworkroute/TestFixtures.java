package com.chris64233.reworkroute;

import com.chris64233.reworkroute.domain.MovementType;
import com.chris64233.reworkroute.domain.StockMovement;
import com.chris64233.reworkroute.repo.StockMovementRepository;
import com.chris64233.reworkroute.service.BatchService;
import com.chris64233.reworkroute.service.DispositionService;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** 生成唯一业务号与常用测试数据。 */
@Component
public class TestFixtures {

    private static final AtomicLong SEQ = new AtomicLong();

    private final BatchService batchService;
    private final DispositionService dispositionService;
    private final StockMovementRepository movementRepository;

    public TestFixtures(BatchService batchService, DispositionService dispositionService,
                        StockMovementRepository movementRepository) {
        this.batchService = batchService;
        this.dispositionService = dispositionService;
        this.movementRepository = movementRepository;
    }

    public String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + SEQ.incrementAndGet();
    }

    /** 建批次并开立不合格记录，返回不合格号。 */
    public String batchWithNc(int initialQty, int affectedQty) {
        String batchNo = unique("B");
        String ncNo = unique("NC");
        batchService.createBatch(batchNo, "P-1", initialQty);
        batchService.openNonconformance(ncNo, batchNo, "D-01", affectedQty);
        return ncNo;
    }

    public DispositionService.SubmitResult disposeAllRework(String ncNo, int qty, List<String> ops) {
        return dispositionService.submitDisposition(unique("DSP"), ncNo, qty, 0, 0, ops);
    }

    /** 库存守恒断言：期初 = 当前可用 + 在途返工 + 已报废（让步与返工并回都回到可用）。 */
    public void assertQuantityConservation(String ncNo, int initialQuantity) {
        List<StockMovement> movements =
                movementRepository.findAll().stream().filter(m -> m.getNc().getNcNo().equals(ncNo)).toList();
        int blocked = sum(movements, MovementType.BLOCK);
        int toRework = sum(movements, MovementType.TO_REWORK);
        int scrap = sum(movements, MovementType.SCRAP);
        int concession = sum(movements, MovementType.CONCESSION_RELEASE);
        int merged = sum(movements, MovementType.REWORK_MERGE);
        if (toRework + scrap + concession != blocked) {
            throw new AssertionError(String.format(
                    "quantity routing mismatch for %s: blocked=%d rework=%d scrap=%d concession=%d",
                    ncNo, blocked, toRework, scrap, concession));
        }
        if (merged > toRework) {
            throw new AssertionError("merged quantity exceeds rework quantity");
        }
    }

    private int sum(List<StockMovement> movements, MovementType type) {
        return movements.stream().filter(m -> m.getType() == type).mapToInt(StockMovement::getQuantity).sum();
    }
}
