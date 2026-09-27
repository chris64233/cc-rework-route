package com.chris64233.reworkroute.service;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.reworkroute.domain.BatchGenealogyEdge;
import com.chris64233.reworkroute.domain.GenealogyType;
import com.chris64233.reworkroute.domain.InspectionResult;
import com.chris64233.reworkroute.domain.InspectionVerdict;
import com.chris64233.reworkroute.domain.NcStatus;
import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.OperationCompletion;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.domain.ReworkStatus;
import com.chris64233.reworkroute.domain.ReworkSubBatch;
import com.chris64233.reworkroute.repo.BatchGenealogyEdgeRepository;
import com.chris64233.reworkroute.repo.InspectionResultRepository;
import com.chris64233.reworkroute.repo.NonConformingRecordRepository;
import com.chris64233.reworkroute.repo.OperationCompletionRepository;
import com.chris64233.reworkroute.repo.ProductionBatchRepository;
import com.chris64233.reworkroute.repo.ReworkSubBatchRepository;
import com.chris64233.reworkroute.support.BusinessRuleException;
import com.chris64233.reworkroute.support.ConcurrentUpdateException;
import com.chris64233.reworkroute.support.NotFoundException;
import com.chris64233.reworkroute.web.dto.CompleteOperationRequest;
import com.chris64233.reworkroute.web.dto.SubmitInspectionRequest;

/**
 * 返工与复验闭环：
 * 工序必须按指定顺序完成 → 全部完成后才能提交复验 → 复验结果按版本登记 →
 * 只有最新版本复验合格才能放行并重新并入可用库存。
 */
@Service
public class ReworkService {

    private final ReworkSubBatchRepository reworkRepository;
    private final OperationCompletionRepository operationRepository;
    private final InspectionResultRepository inspectionRepository;
    private final ProductionBatchRepository batchRepository;
    private final NonConformingRecordRepository ncRepository;
    private final BatchGenealogyEdgeRepository genealogyRepository;

    public ReworkService(ReworkSubBatchRepository reworkRepository,
                         OperationCompletionRepository operationRepository,
                         InspectionResultRepository inspectionRepository,
                         ProductionBatchRepository batchRepository,
                         NonConformingRecordRepository ncRepository,
                         BatchGenealogyEdgeRepository genealogyRepository) {
        this.reworkRepository = reworkRepository;
        this.operationRepository = operationRepository;
        this.inspectionRepository = inspectionRepository;
        this.batchRepository = batchRepository;
        this.ncRepository = ncRepository;
        this.genealogyRepository = genealogyRepository;
    }

    /**
     * 完成一道返工工序。并发完成同一子批次的工序时，行锁串行化，
     * 只有“下一道应完成工序”可以登记，跳序、重复均被拒绝。
     */
    @Transactional
    public OperationCompletion completeOperation(String subBatchNo, CompleteOperationRequest request) {
        ReworkSubBatch subBatch = lockSubBatch(subBatchNo);
        if (subBatch.getStatus() == ReworkStatus.REINTEGRATED) {
            throw new BusinessRuleException("返工子批次已放行，不能再登记工序");
        }
        List<String> required = splitOperations(subBatch.getRequiredOperations());
        int stepIndex = required.indexOf(request.operationCode());
        if (stepIndex < 0) {
            throw new BusinessRuleException("工序 " + request.operationCode() + " 不在指定返工工序序列中");
        }
        if (stepIndex != subBatch.getCompletedOperationCount()) {
            throw new BusinessRuleException(String.format(
                    "工序顺序违反：下一道应完成工序为 [%d]=%s，而提交的是 [%d]=%s",
                    subBatch.getCompletedOperationCount(),
                    required.get(subBatch.getCompletedOperationCount()),
                    stepIndex, request.operationCode()));
        }
        OperationCompletion completion;
        try {
            completion = operationRepository.save(new OperationCompletion(
                    subBatch, stepIndex, request.operationCode(), request.operator()));
            operationRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConcurrentUpdateException("工序已被并发完成: " + request.operationCode(), e);
        }
        subBatch.setCompletedOperationCount(stepIndex + 1);
        if (subBatch.allOperationsCompleted()) {
            subBatch.setStatus(ReworkStatus.OPERATIONS_DONE);
        }
        reworkRepository.save(subBatch);
        return completion;
    }

    /**
     * 提交复验结果。结果带单调递增版本号：
     * 必须所有工序先完成；expectedVersion 必须等于当前版本，旧版本提交被拒绝；
     * 唯一约束 (子批次, 版本号) 在数据库层面兜底并发提交。
     */
    @Transactional
    public InspectionResult submitInspection(String subBatchNo, SubmitInspectionRequest request) {
        ReworkSubBatch subBatch = lockSubBatch(subBatchNo);
        if (subBatch.getStatus() == ReworkStatus.REINTEGRATED) {
            throw new BusinessRuleException("返工子批次已放行，不能再提交复验结果");
        }
        if (!subBatch.allOperationsCompleted()) {
            throw new BusinessRuleException(String.format(
                    "指定返工工序尚未全部完成（%d/%d），不能提交复验",
                    subBatch.getCompletedOperationCount(), subBatch.getRequiredOperationCount()));
        }
        int currentVersion = subBatch.getInspectionVersion();
        if (request.expectedVersion() != null && request.expectedVersion() != currentVersion) {
            throw new ConcurrentUpdateException(
                    "复验版本冲突：提交基于版本 " + request.expectedVersion()
                            + "，当前最新版本为 " + currentVersion, null);
        }
        int nextVersion = currentVersion + 1;
        InspectionResult result;
        try {
            result = inspectionRepository.save(new InspectionResult(
                    subBatch, nextVersion, request.verdict(), request.inspector(), request.remark()));
            inspectionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConcurrentUpdateException(
                    "复验版本 " + nextVersion + " 已被并发提交，旧版本结果不得放行", e);
        }
        subBatch.setInspectionVersion(nextVersion);
        subBatch.setLatestVerdict(request.verdict());
        reworkRepository.save(subBatch);
        return result;
    }

    /**
     * 复验合格放行：将返工数量重新并入原批次可用库存。
     * 放行只认最新版本结论——旧版本合格但新版本不合格时不得放行。
     */
    @Transactional
    public ReworkSubBatch release(String subBatchNo) {
        ReworkSubBatch subBatch = lockSubBatch(subBatchNo);
        if (subBatch.getStatus() == ReworkStatus.REINTEGRATED) {
            return subBatch;
        }
        if (!subBatch.allOperationsCompleted()) {
            throw new BusinessRuleException(String.format(
                    "指定返工工序尚未全部完成（%d/%d），不能放行",
                    subBatch.getCompletedOperationCount(), subBatch.getRequiredOperationCount()));
        }
        if (subBatch.getInspectionVersion() == 0) {
            throw new BusinessRuleException("尚未取得复验结果，不能放行");
        }
        if (subBatch.getLatestVerdict() != InspectionVerdict.PASSED) {
            throw new BusinessRuleException("最新复验版本 v" + subBatch.getInspectionVersion()
                    + " 结论为 " + subBatch.getLatestVerdict() + "，旧版本合格也不得放行");
        }

        ProductionBatch batch = batchRepository.findWithLockingById(subBatch.getParentBatch().getId())
                .orElseThrow();
        batch.setAvailableQuantity(batch.getAvailableQuantity().add(subBatch.getQuantity()));

        subBatch.setStatus(ReworkStatus.REINTEGRATED);
        subBatch.setReintegratedAt(Instant.now());
        reworkRepository.save(subBatch);

        genealogyRepository.save(BatchGenealogyEdge
                .builder(GenealogyType.REINTEGRATE, subBatch.getNcRecord(), subBatch.getQuantity())
                .target(batch).disposition(subBatch.getDisposition()).rework(subBatch).build());

        closeNcIfAllReintegrated(subBatch.getNcRecord());
        return subBatch;
    }

    /** 不合格记录下所有返工子批次均放行后，闭环关闭。 */
    private void closeNcIfAllReintegrated(NonConformingRecord nc) {
        List<ReworkSubBatch> all = reworkRepository.findByNcRecordNcNo(nc.getNcNo());
        boolean allDone = !all.isEmpty()
                && all.stream().allMatch(s -> s.getStatus() == ReworkStatus.REINTEGRATED);
        if (allDone) {
            nc.setStatus(NcStatus.CLOSED);
            ncRepository.save(nc);
        }
    }

    private ReworkSubBatch lockSubBatch(String subBatchNo) {
        return reworkRepository.findWithLockingBySubBatchNo(subBatchNo)
                .orElseThrow(() -> new NotFoundException("返工子批次不存在: " + subBatchNo));
    }

    private List<String> splitOperations(String raw) {
        return List.of(raw.split(","));
    }

    @Transactional(readOnly = true)
    public ReworkSubBatch requireSubBatch(String subBatchNo) {
        return reworkRepository.findBySubBatchNo(subBatchNo)
                .orElseThrow(() -> new NotFoundException("返工子批次不存在: " + subBatchNo));
    }
}
