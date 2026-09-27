package com.chris64233.reworkroute.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.reworkroute.domain.NcStatus;
import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.repo.NonConformingRecordRepository;
import com.chris64233.reworkroute.repo.ProductionBatchRepository;
import com.chris64233.reworkroute.support.BusinessRuleException;
import com.chris64233.reworkroute.support.NotFoundException;
import com.chris64233.reworkroute.support.Quantities;
import com.chris64233.reworkroute.web.dto.CreateBatchRequest;
import com.chris64233.reworkroute.web.dto.CreateNcRequest;

@Service
public class BatchService {

    private final ProductionBatchRepository batchRepository;
    private final NonConformingRecordRepository ncRepository;

    public BatchService(ProductionBatchRepository batchRepository,
                        NonConformingRecordRepository ncRepository) {
        this.batchRepository = batchRepository;
        this.ncRepository = ncRepository;
    }

    @Transactional
    public ProductionBatch createBatch(CreateBatchRequest request) {
        Quantities.requirePositive(request.totalQuantity(), "totalQuantity");
        batchRepository.findByBatchNo(request.batchNo()).ifPresent(b -> {
            throw new BusinessRuleException("批次编号已存在: " + request.batchNo());
        });
        return batchRepository.save(new ProductionBatch(
                request.batchNo(), request.materialCode(), request.totalQuantity()));
    }

    /**
     * 登记不合格记录：必须关联生产批次并单独记录受影响数量。
     * 受影响数量即刻从批次可用库存中冻结（行锁串行化），直到处置决定落账。
     */
    @Transactional
    public NonConformingRecord registerNc(CreateNcRequest request) {
        Quantities.requirePositive(request.affectedQuantity(), "affectedQuantity");
        ncRepository.findByNcNo(request.ncNo()).ifPresent(n -> {
            throw new BusinessRuleException("不合格记录编号已存在: " + request.ncNo());
        });
        ProductionBatch batch = batchRepository.findWithLockingById(
                        batchRepository.findByBatchNo(request.batchNo())
                                .orElseThrow(() -> new NotFoundException("批次不存在: " + request.batchNo()))
                                .getId())
                .orElseThrow(() -> new NotFoundException("批次不存在: " + request.batchNo()));
        if (batch.getAvailableQuantity().compareTo(request.affectedQuantity()) < 0) {
            throw new BusinessRuleException("受影响数量 " + request.affectedQuantity()
                    + " 超过批次可用数量 " + batch.getAvailableQuantity());
        }
        batch.setAvailableQuantity(batch.getAvailableQuantity().subtract(request.affectedQuantity()));
        return ncRepository.save(new NonConformingRecord(
                request.ncNo(), batch, request.affectedQuantity(), request.defectDescription()));
    }

    @Transactional(readOnly = true)
    public ProductionBatch requireBatch(String batchNo) {
        return batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + batchNo));
    }

    @Transactional(readOnly = true)
    public NonConformingRecord requireNc(String ncNo) {
        return ncRepository.findByNcNo(ncNo)
                .orElseThrow(() -> new NotFoundException("不合格记录不存在: " + ncNo));
    }

    @Transactional(readOnly = true)
    public BigDecimal availableQuantity(String batchNo) {
        return requireBatch(batchNo).getAvailableQuantity();
    }

    @Transactional(readOnly = true)
    public NcStatus ncStatus(String ncNo) {
        return requireNc(ncNo).getStatus();
    }
}
