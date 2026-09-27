package com.chris64233.reworkroute.service;

import com.chris64233.reworkroute.domain.MovementType;
import com.chris64233.reworkroute.domain.NcStatus;
import com.chris64233.reworkroute.domain.NonconformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.repo.NonconformingRecordRepository;
import com.chris64233.reworkroute.repo.ProductionBatchRepository;
import com.chris64233.reworkroute.repo.StockMovementRepository;
import com.chris64233.reworkroute.domain.StockMovement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批次登记与不合格记录开立。
 * 开立不合格记录时在批次行锁内冻结受影响数量（可用 -> 冻结），
 * 并写 BLOCK 移动台账，保证后续处置数量一定有出处。
 */
@Service
public class BatchService {

    private final ProductionBatchRepository batchRepository;
    private final NonconformingRecordRepository ncRepository;
    private final StockMovementRepository movementRepository;

    public BatchService(ProductionBatchRepository batchRepository,
                        NonconformingRecordRepository ncRepository,
                        StockMovementRepository movementRepository) {
        this.batchRepository = batchRepository;
        this.ncRepository = ncRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public ProductionBatch createBatch(String batchNo, String productCode, int initialQuantity) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new ValidationException("batchNo must not be blank");
        }
        if (initialQuantity <= 0) {
            throw new ValidationException("initialQuantity must be positive");
        }
        if (batchRepository.existsByBatchNo(batchNo)) {
            throw new BusinessConflictException("batch already exists: " + batchNo);
        }
        return batchRepository.save(new ProductionBatch(batchNo, productCode, initialQuantity));
    }

    @Transactional
    public NonconformingRecord openNonconformance(String ncNo, String batchNo, String defectCode,
                                                  int affectedQuantity) {
        if (ncNo == null || ncNo.isBlank()) {
            throw new ValidationException("ncNo must not be blank");
        }
        if (affectedQuantity <= 0) {
            throw new ValidationException("affectedQuantity must be positive");
        }
        ncRepository.findByNcNo(ncNo).ifPresent(existing -> {
            throw new BusinessConflictException("nonconforming record already exists: " + ncNo);
        });
        Long batchId = batchRepository.findByBatchNo(batchNo).map(ProductionBatch::getId)
                .orElseThrow(() -> new NotFoundException("batch not found: " + batchNo));

        // 行锁串行化同一批次的冻结/并回，避免超量冻结。
        ProductionBatch locked = batchRepository.findWithLockById(batchId).orElseThrow();
        if (locked.getAvailableQuantity() < affectedQuantity) {
            throw new BusinessConflictException(String.format(
                    "insufficient available quantity: available=%d, requested=%d",
                    locked.getAvailableQuantity(), affectedQuantity));
        }
        locked.setAvailableQuantity(locked.getAvailableQuantity() - affectedQuantity);

        NonconformingRecord nc = new NonconformingRecord(ncNo, locked, defectCode, affectedQuantity);
        nc = ncRepository.save(nc);
        movementRepository.save(new StockMovement(locked, nc, MovementType.BLOCK, affectedQuantity, ncNo));
        return nc;
    }

    @Transactional(readOnly = true)
    public ProductionBatch getBatch(String batchNo) {
        return batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("batch not found: " + batchNo));
    }

    @Transactional(readOnly = true)
    public NonconformingRecord getNc(String ncNo) {
        return ncRepository.findByNcNo(ncNo)
                .orElseThrow(() -> new NotFoundException("nonconforming record not found: " + ncNo));
    }

    @Transactional(readOnly = true)
    public NcStatus getNcStatus(String ncNo) {
        return getNc(ncNo).getStatus();
    }
}
