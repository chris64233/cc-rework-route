package com.chris64233.reworkroute.service;

import com.chris64233.reworkroute.domain.Disposition;
import com.chris64233.reworkroute.domain.MovementType;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.domain.ReinspectionDecision;
import com.chris64233.reworkroute.domain.ReinspectionResult;
import com.chris64233.reworkroute.domain.ReworkStatus;
import com.chris64233.reworkroute.domain.ReworkSubBatch;
import com.chris64233.reworkroute.domain.StockMovement;
import com.chris64233.reworkroute.repo.ProductionBatchRepository;
import com.chris64233.reworkroute.repo.ReinspectionResultRepository;
import com.chris64233.reworkroute.repo.ReworkSubBatchRepository;
import com.chris64233.reworkroute.repo.StockMovementRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 返工执行、复验与并回库存。
 *
 * 顺序保证：所有状态推进都在“子批次行锁”内串行（并回时再加批次行锁），
 * 因此“完成工序”与“提交复验”即使并发调用也严格满足：
 * 指定工序按序全部完成 -> 才能提交复验 -> 最新版本复验合格 -> 才能并回可用库存。
 * 复验结果带单调版本号，放行只认最新版本，旧结果（包括旧的合格结果）一律不得放行。
 */
@Service
public class ReworkService {

    private final ReworkSubBatchRepository subBatchRepository;
    private final ReinspectionResultRepository resultRepository;
    private final ProductionBatchRepository batchRepository;
    private final StockMovementRepository movementRepository;

    public ReworkService(ReworkSubBatchRepository subBatchRepository,
                         ReinspectionResultRepository resultRepository,
                         ProductionBatchRepository batchRepository,
                         StockMovementRepository movementRepository) {
        this.subBatchRepository = subBatchRepository;
        this.resultRepository = resultRepository;
        this.batchRepository = batchRepository;
        this.movementRepository = movementRepository;
    }

    /**
     * 完成一道返工工序。必须按处置指定的顺序提交，跳序、重复、未知工序均冲突。
     */
    @Transactional
    public ReworkSubBatch completeOperation(String subBatchNo, String operationCode) {
        if (operationCode == null || operationCode.isBlank()) {
            throw new ValidationException("operationCode must not be blank");
        }
        ReworkSubBatch subBatch = lockSubBatch(subBatchNo);
        if (subBatch.getStatus() == ReworkStatus.MERGED) {
            throw new BusinessConflictException("sub batch already merged: " + subBatchNo);
        }

        Disposition disposition = subBatch.getDisposition();
        List<String> required = disposition.getRequiredOperations().stream()
                .map(op -> op.getOperationCode()).toList();
        int done = subBatch.getCompletedSteps().size();
        if (done >= required.size()) {
            throw new BusinessConflictException(
                    "all required operations already completed for sub batch: " + subBatchNo);
        }
        String expected = required.get(done);
        if (!expected.equals(operationCode)) {
            throw new BusinessConflictException(String.format(
                    "operation out of order: subBatch=%s expected=%s actual=%s",
                    subBatchNo, expected, operationCode));
        }

        if (subBatch.getStatus() == ReworkStatus.CREATED) {
            subBatch.markInProgress();
        }
        subBatch.getCompletedSteps().add(new ReworkSubBatch.ReworkStep(operationCode, java.time.Instant.now()));
        if (subBatch.getCompletedSteps().size() == required.size()) {
            subBatch.markCompleted();
        }
        return subBatchRepository.save(subBatch);
    }

    /**
     * 提交复验结果。仅在全部指定工序完成（COMPLETED）后允许；版本号在子批次锁内单调分配。
     */
    @Transactional
    public ReinspectionResult submitReinspection(String subBatchNo, ReinspectionDecision decision,
                                                 String remark, String inspector) {
        if (decision == null) {
            throw new ValidationException("decision must not be null");
        }
        ReworkSubBatch subBatch = lockSubBatch(subBatchNo);
        if (subBatch.getStatus() == ReworkStatus.MERGED) {
            throw new BusinessConflictException("sub batch already merged: " + subBatchNo);
        }
        if (subBatch.getStatus() != ReworkStatus.COMPLETED) {
            throw new BusinessConflictException(String.format(
                    "reinspection requires all required operations completed: subBatch=%s status=%s",
                    subBatchNo, subBatch.getStatus()));
        }
        int nextVersion = resultRepository.findMaxVersion(subBatch.getId()) + 1;
        ReinspectionResult result = new ReinspectionResult(subBatch, nextVersion, decision, remark,
                inspector == null ? "system" : inspector);
        return resultRepository.save(result);
    }

    /**
     * 凭复验合格结果把返工子批次重新并入可用库存。
     *
     * @param expectedVersion 调用方持有的复验版本号；非空且不等于最新版本时拒绝（防止旧结果放行）。
     *                        为空时仍强制要求“最新版本结论为 PASS”。
     */
    @Transactional
    public ReworkSubBatch mergeBack(String subBatchNo, Integer expectedVersion) {
        Long subBatchId = subBatchRepository.findIdBySubBatchNo(subBatchNo)
                .orElseThrow(() -> new NotFoundException("rework sub batch not found: " + subBatchNo));
        // 全局加锁顺序：批次 -> 子批次，与处置确认一致，避免死锁。只按标量 ID 取锁。
        ProductionBatch batch = batchRepository.findWithLockById(
                subBatchRepository.findBatchIdBySubBatchNo(subBatchNo).orElseThrow()).orElseThrow();
        ReworkSubBatch subBatch = subBatchRepository.findWithLockById(subBatchId).orElseThrow();

        if (subBatch.getStatus() == ReworkStatus.MERGED) {
            throw new BusinessConflictException("sub batch already merged: " + subBatchNo);
        }
        if (subBatch.getStatus() != ReworkStatus.COMPLETED) {
            throw new BusinessConflictException(String.format(
                    "cannot merge before all required operations completed: subBatch=%s status=%s",
                    subBatchNo, subBatch.getStatus()));
        }

        ReinspectionResult latest = resultRepository
                .findTopBySubBatchIdOrderByResultVersionDesc(subBatch.getId())
                .orElseThrow(() -> new BusinessConflictException(
                        "no reinspection result for sub batch: " + subBatchNo));
        if (latest.getDecision() != ReinspectionDecision.PASS) {
            throw new BusinessConflictException(String.format(
                    "latest reinspection (v%d) is FAIL, cannot merge sub batch: %s",
                    latest.getResultVersion(), subBatchNo));
        }
        if (expectedVersion != null && expectedVersion != latest.getResultVersion()) {
            // 调用方依据的是旧版本结果：即使旧版本合格也不得放行。
            throw new BusinessConflictException(String.format(
                    "stale reinspection version: expected=%s latest=%d, subBatch=%s",
                    expectedVersion, latest.getResultVersion(), subBatchNo));
        }

        batch.setAvailableQuantity(batch.getAvailableQuantity() + subBatch.getQuantity());
        movementRepository.save(new StockMovement(batch, subBatch.getNc(), MovementType.REWORK_MERGE,
                subBatch.getQuantity(), subBatch.getSubBatchNo()));
        subBatch.markMerged();
        return subBatchRepository.save(subBatch);
    }

    private ReworkSubBatch lockSubBatch(String subBatchNo) {
        // 直接以标量 ID 取行锁，避免未加锁实体与加锁结果在持久化上下文中版本冲突。
        Long id = subBatchRepository.findIdBySubBatchNo(subBatchNo)
                .orElseThrow(() -> new NotFoundException("rework sub batch not found: " + subBatchNo));
        return subBatchRepository.findWithLockById(id).orElseThrow();
    }
}
