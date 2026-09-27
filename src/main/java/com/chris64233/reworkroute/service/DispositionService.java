package com.chris64233.reworkroute.service;

import com.chris64233.reworkroute.domain.Disposition;
import com.chris64233.reworkroute.domain.DispositionStatus;
import com.chris64233.reworkroute.domain.CorrectionEntry;
import com.chris64233.reworkroute.domain.MovementType;
import com.chris64233.reworkroute.domain.NcStatus;
import com.chris64233.reworkroute.domain.NonconformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.domain.ReworkSubBatch;
import com.chris64233.reworkroute.domain.ScrapRecord;
import com.chris64233.reworkroute.domain.StockMovement;
import com.chris64233.reworkroute.repo.DispositionRepository;
import com.chris64233.reworkroute.repo.NonconformingRecordRepository;
import com.chris64233.reworkroute.repo.ProductionBatchRepository;
import com.chris64233.reworkroute.repo.ReworkSubBatchRepository;
import com.chris64233.reworkroute.repo.ScrapRecordRepository;
import com.chris64233.reworkroute.repo.StockMovementRepository;
import com.chris64233.reworkroute.repo.CorrectionEntryRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 处置决定服务。
 *
 * 关键保证：
 * 1. businessNo 为幂等键：相同内容重放返回原处置；内容不同抛 {@link BusinessConflictException}。
 * 2. 确认在一个数据库事务内原子完成：校验三部分数量之和 == 受影响数量、NC 未被消费、
 *    状态为 OPEN，随后一次性生成返工子批次/报废记录/库存移动并调整库存。
 *    任一校验失败整体回滚，不会留下部分结果。
 * 3. 按“批次行锁 -> 不合格记录行锁”的固定顺序加锁，同一份受影响数量不可能被
 *    两个处置并发消费（NC 状态翻转 + 唯一约束双重保障）。
 * 4. 已确认处置内容冻结，仅可追加纠正记录。
 */
@Service
public class DispositionService {

    private final DispositionRepository dispositionRepository;
    private final NonconformingRecordRepository ncRepository;
    private final ProductionBatchRepository batchRepository;
    private final ReworkSubBatchRepository subBatchRepository;
    private final ScrapRecordRepository scrapRepository;
    private final StockMovementRepository movementRepository;
    private final CorrectionEntryRepository correctionRepository;
    private final TransactionTemplate txTemplate;

    public DispositionService(DispositionRepository dispositionRepository,
                              NonconformingRecordRepository ncRepository,
                              ProductionBatchRepository batchRepository,
                              ReworkSubBatchRepository subBatchRepository,
                              ScrapRecordRepository scrapRepository,
                              StockMovementRepository movementRepository,
                              CorrectionEntryRepository correctionRepository,
                              PlatformTransactionManager transactionManager) {
        this.dispositionRepository = dispositionRepository;
        this.ncRepository = ncRepository;
        this.batchRepository = batchRepository;
        this.subBatchRepository = subBatchRepository;
        this.scrapRepository = scrapRepository;
        this.movementRepository = movementRepository;
        this.correctionRepository = correctionRepository;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 提交并确认处置。无外层事务：内部以编程式事务执行，
     * 以便在并发同业务号撞唯一约束时能用一个全新的事务完成幂等重放。
     *
     * @return 已存在（重放）或新确认的处置；replayed=true 表示命中幂等
     */
    public SubmitResult submitDisposition(String businessNo, String ncNo, int reworkQty, int scrapQty,
                                          int concessionQty, List<String> requiredOperations) {
        validateRequest(businessNo, reworkQty, scrapQty, concessionQty, requiredOperations);
        try {
            return txTemplate.execute(status -> doSubmit(businessNo, ncNo, reworkQty, scrapQty,
                    concessionQty, requiredOperations));
        } catch (DataIntegrityViolationException duplicateKey) {
            // 并发下另一事务已用相同 businessNo 完成插入：在新事务中按幂等/冲突规则重放判定。
            return txTemplate.execute(status -> replay(businessNo, ncNo, reworkQty, scrapQty,
                    concessionQty, requiredOperations));
        }
    }

    private SubmitResult doSubmit(String businessNo, String ncNo, int reworkQty, int scrapQty,
                                  int concessionQty, List<String> requiredOperations) {
        Disposition existing = dispositionRepository.findWithLockByBusinessNo(businessNo).orElse(null);
        if (existing != null) {
            return replayExisting(existing, businessNo, ncNo, reworkQty, scrapQty, concessionQty,
                    requiredOperations);
        }

        Long ncId = ncRepository.findIdByNcNo(ncNo)
                .orElseThrow(() -> new NotFoundException("nonconforming record not found: " + ncNo));

        // 固定加锁顺序：先批次后不合格记录。只按标量 ID 取锁，
        // 避免“未加锁先读、再加锁重读”在并发提交时出现版本冲突。
        ProductionBatch batch = batchRepository.findWithLockById(
                ncRepository.findBatchIdByNcNo(ncNo).orElseThrow()).orElseThrow();
        NonconformingRecord nc = ncRepository.findWithLockById(ncId).orElseThrow();

        if (nc.getStatus() != NcStatus.OPEN) {
            // 可能是并发下相同 businessNo 的另一请求已抢先确认：拿到锁后重查一次，
            // 内容一致则幂等返回原结果，否则才按“重复消费”报冲突。
            Disposition winner = dispositionRepository.findByBusinessNo(businessNo).orElse(null);
            if (winner != null) {
                return replayExisting(winner, businessNo, ncNo, reworkQty, scrapQty, concessionQty,
                        requiredOperations);
            }
            throw new BusinessConflictException(
                    "nonconforming record already disposed: " + ncNo);
        }
        if (reworkQty + scrapQty + concessionQty != nc.getAffectedQuantity()) {
            throw new ValidationException(String.format(
                    "split quantities must sum to affectedQuantity %d: rework=%d scrap=%d concession=%d",
                    nc.getAffectedQuantity(), reworkQty, scrapQty, concessionQty));
        }

        // 先以 DRAFT 落库取得主键，再用它派生子批次/报废单号；未确认前事务不会提交，外部不可见。
        Disposition disposition = new Disposition(businessNo, nc, reworkQty, scrapQty, concessionQty,
                requiredOperations);
        disposition = dispositionRepository.saveAndFlush(disposition);

        ReworkSubBatch subBatch = null;
        ScrapRecord scrap = null;

        if (reworkQty > 0) {
            subBatch = new ReworkSubBatch("RW-" + disposition.getId(), batch, nc, reworkQty);
            subBatch = subBatchRepository.save(subBatch);
            movementRepository.save(new StockMovement(batch, nc, MovementType.TO_REWORK, reworkQty,
                    subBatch.getSubBatchNo()));
        }
        if (scrapQty > 0) {
            scrap = new ScrapRecord("SC-" + disposition.getId(), batch, nc, scrapQty,
                    "DISPOSITION:" + nc.getDefectCode());
            scrap = scrapRepository.save(scrap);
            movementRepository.save(new StockMovement(batch, nc, MovementType.SCRAP, scrapQty,
                    scrap.getScrapNo()));
        }
        if (concessionQty > 0) {
            // 让步接收：冻结数量直接回到可用库存。
            batch.setAvailableQuantity(batch.getAvailableQuantity() + concessionQty);
            movementRepository.save(new StockMovement(batch, nc, MovementType.CONCESSION_RELEASE,
                    concessionQty, businessNo));
        }

        nc.markDisposed();
        disposition.confirm(subBatch, scrap);
        dispositionRepository.save(disposition);
        return new SubmitResult(disposition, false);
    }

    private SubmitResult replay(String businessNo, String ncNo, int reworkQty, int scrapQty,
                                int concessionQty, List<String> requiredOperations) {
        Disposition existing = dispositionRepository.findWithLockByBusinessNo(businessNo)
                .orElseThrow(() -> new IllegalStateException(
                        "duplicate key reported but disposition missing: " + businessNo));
        return replayExisting(existing, businessNo, ncNo, reworkQty, scrapQty, concessionQty,
                requiredOperations);
    }

    private SubmitResult replayExisting(Disposition existing, String businessNo, String ncNo,
                                        int reworkQty, int scrapQty, int concessionQty,
                                        List<String> requiredOperations) {
        Long ncId = ncRepository.findIdByNcNo(ncNo)
                .orElseThrow(() -> new NotFoundException("nonconforming record not found: " + ncNo));
        if (!existing.contentEquals(ncId, reworkQty, scrapQty, concessionQty, requiredOperations)) {
            throw new BusinessConflictException(
                    "disposition businessNo already used with different content: " + businessNo);
        }
        return new SubmitResult(existing, true);
    }

    private void validateRequest(String businessNo, int reworkQty, int scrapQty, int concessionQty,
                                 List<String> requiredOperations) {
        if (businessNo == null || businessNo.isBlank()) {
            throw new ValidationException("businessNo must not be blank");
        }
        if (reworkQty < 0 || scrapQty < 0 || concessionQty < 0) {
            throw new ValidationException("split quantities must not be negative");
        }
        if (reworkQty + scrapQty + concessionQty <= 0) {
            throw new ValidationException("at least one split quantity must be positive");
        }
        List<String> ops = requiredOperations == null ? List.of() : requiredOperations;
        if (ops.stream().anyMatch(op -> op == null || op.isBlank())) {
            throw new ValidationException("requiredOperations must not contain blank codes");
        }
        if (reworkQty > 0 && ops.isEmpty()) {
            throw new ValidationException(
                    "requiredOperations must be specified when reworkQty > 0");
        }
    }

    /**
     * 对已确认处置追加纠正记录。处置内容不可修改，纠正措施只增不改，保留完整谱系。
     */
    public CorrectionEntry appendCorrection(String businessNo, String actionCode, String detail,
                                            String operator) {
        if (actionCode == null || actionCode.isBlank() || detail == null || detail.isBlank()) {
            throw new ValidationException("actionCode and detail must not be blank");
        }
        return txTemplate.execute(status -> {
            Disposition disposition = dispositionRepository.findWithLockByBusinessNo(businessNo)
                    .orElseThrow(() -> new NotFoundException(
                            "disposition not found: " + businessNo));
            if (disposition.getStatus() != DispositionStatus.CONFIRMED) {
                throw new BusinessConflictException(
                        "corrections can only be appended to a confirmed disposition: " + businessNo);
            }
            CorrectionEntry entry = new CorrectionEntry(disposition, actionCode, detail,
                    operator == null ? "system" : operator);
            disposition.addCorrection(entry);
            return correctionRepository.save(entry);
        });
    }

    /** 提交结果：disposition 为处置实体，replayed 标识是否为幂等重放命中。 */
    public record SubmitResult(Disposition disposition, boolean replayed) {
    }
}
