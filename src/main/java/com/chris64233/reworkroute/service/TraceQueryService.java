package com.chris64233.reworkroute.service;

import com.chris64233.reworkroute.domain.CorrectionEntry;
import com.chris64233.reworkroute.domain.Disposition;
import com.chris64233.reworkroute.domain.NonconformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.domain.ReinspectionResult;
import com.chris64233.reworkroute.domain.ReworkSubBatch;
import com.chris64233.reworkroute.domain.ScrapRecord;
import com.chris64233.reworkroute.domain.StockMovement;
import com.chris64233.reworkroute.repo.CorrectionEntryRepository;
import com.chris64233.reworkroute.repo.DispositionRepository;
import com.chris64233.reworkroute.repo.NonconformingRecordRepository;
import com.chris64233.reworkroute.repo.ProductionBatchRepository;
import com.chris64233.reworkroute.repo.ReinspectionResultRepository;
import com.chris64233.reworkroute.repo.ReworkSubBatchRepository;
import com.chris64233.reworkroute.repo.ScrapRecordRepository;
import com.chris64233.reworkroute.repo.StockMovementRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 只读查询：不合格数量去向、返工进度、复验结果与完整批次谱系。
 */
@Service
public class TraceQueryService {

    private final ProductionBatchRepository batchRepository;
    private final NonconformingRecordRepository ncRepository;
    private final DispositionRepository dispositionRepository;
    private final ReworkSubBatchRepository subBatchRepository;
    private final ReinspectionResultRepository resultRepository;
    private final ScrapRecordRepository scrapRepository;
    private final StockMovementRepository movementRepository;
    private final CorrectionEntryRepository correctionRepository;

    public TraceQueryService(ProductionBatchRepository batchRepository,
                             NonconformingRecordRepository ncRepository,
                             DispositionRepository dispositionRepository,
                             ReworkSubBatchRepository subBatchRepository,
                             ReinspectionResultRepository resultRepository,
                             ScrapRecordRepository scrapRepository,
                             StockMovementRepository movementRepository,
                             CorrectionEntryRepository correctionRepository) {
        this.batchRepository = batchRepository;
        this.ncRepository = ncRepository;
        this.dispositionRepository = dispositionRepository;
        this.subBatchRepository = subBatchRepository;
        this.resultRepository = resultRepository;
        this.scrapRepository = scrapRepository;
        this.movementRepository = movementRepository;
        this.correctionRepository = correctionRepository;
    }

    /** 不合格数量去向：冻结、返工（在途/已并回）、报废、让步释放的数量与移动明细。 */
    @Transactional(readOnly = true)
    public QuantityTrace traceQuantity(String ncNo) {
        NonconformingRecord nc = ncRepository.findByNcNo(ncNo)
                .orElseThrow(() -> new NotFoundException("nonconforming record not found: " + ncNo));
        List<StockMovement> movements = movementRepository.findByNcIdOrderByCreatedAtAsc(nc.getId());

        int rework = 0;
        int scrap = 0;
        int concession = 0;
        int mergedBack = 0;
        for (StockMovement m : movements) {
            switch (m.getType()) {
                case TO_REWORK -> rework += m.getQuantity();
                case SCRAP -> scrap += m.getQuantity();
                case CONCESSION_RELEASE -> concession += m.getQuantity();
                case REWORK_MERGE -> mergedBack += m.getQuantity();
                default -> { /* BLOCK 为冻结入口，不计入去向拆分 */ }
            }
        }
        int reworkInProgress = rework - mergedBack;
        return new QuantityTrace(ncNo, nc.getAffectedQuantity(), nc.getStatus().name(), rework,
                reworkInProgress, mergedBack, scrap, concession,
                movements.stream().map(this::toMovementView).toList());
    }

    /** 返工进度：指定工序、已完成工序、状态。 */
    @Transactional(readOnly = true)
    public ReworkProgress getReworkProgress(String subBatchNo) {
        ReworkSubBatch subBatch = subBatchRepository.findBySubBatchNo(subBatchNo)
                .orElseThrow(() -> new NotFoundException("rework sub batch not found: " + subBatchNo));
        Disposition disposition = subBatch.getDisposition();
        List<String> required = disposition.getRequiredOperations().stream()
                .map(op -> op.getOperationCode()).toList();
        List<String> completed = subBatch.getCompletedSteps().stream()
                .map(ReworkSubBatch.ReworkStep::getOperationCode).toList();
        return new ReworkProgress(subBatchNo, subBatch.getQuantity(), subBatch.getStatus().name(),
                required, completed,
                subBatch.getMergedAt());
    }

    /** 复验结果历史（按版本升序）与最新结论。 */
    @Transactional(readOnly = true)
    public ReinspectionHistory getReinspectionHistory(String subBatchNo) {
        ReworkSubBatch subBatch = subBatchRepository.findBySubBatchNo(subBatchNo)
                .orElseThrow(() -> new NotFoundException("rework sub batch not found: " + subBatchNo));
        List<ReinspectionResult> results =
                resultRepository.findBySubBatchIdOrderByResultVersionAsc(subBatch.getId());
        List<ReinspectionView> views = results.stream().map(r -> new ReinspectionView(
                r.getResultVersion(), r.getDecision().name(), r.getRemark(), r.getInspector(),
                r.getCreatedAt())).toList();
        ReinspectionView latest = views.isEmpty() ? null : views.get(views.size() - 1);
        return new ReinspectionHistory(subBatchNo, latest, views);
    }

    /**
     * 批次谱系：批次 -> 不合格记录 -> 处置决定（拆分数量）-> 返工子批次（工序/复验/并回）
     * 与报废记录 -> 纠正记录，全链路返回。
     */
    @Transactional(readOnly = true)
    public BatchGenealogy getBatchGenealogy(String batchNo) {
        ProductionBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("batch not found: " + batchNo));
        List<StockMovement> movements =
                movementRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId());
        List<NcGenealogy> ncs = movements.stream()
                .map(m -> m.getNc().getId())
                .distinct()
                .map(ncId -> buildNcGenealogy(ncId))
                .toList();
        return new BatchGenealogy(batchNo, batch.getProductCode(), batch.getInitialQuantity(),
                batch.getAvailableQuantity(), ncs,
                movements.stream().map(this::toMovementView).toList());
    }

    private NcGenealogy buildNcGenealogy(Long ncId) {
        // 谱系查询以移动台账中出现过的 NC 为准（已确认处置一定有移动记录）。
        NonconformingRecord nc = ncRepository.findById(ncId).orElseThrow();
        // 每个 NC 至多一个已确认处置（数量不可重复消费）。
        Disposition disposition = dispositionRepository.findByNcId(ncId).orElse(null);
        ReworkGenealogy rework = null;
        ScrapGenealogy scrap = null;
        List<CorrectionView> corrections = List.of();
        String businessNo = null;
        String dispositionStatus = null;
        if (disposition != null) {
            businessNo = disposition.getBusinessNo();
            dispositionStatus = disposition.getStatus().name();
            if (disposition.getReworkSubBatch() != null) {
                ReworkSubBatch rb = disposition.getReworkSubBatch();
                rework = new ReworkGenealogy(rb.getSubBatchNo(), rb.getQuantity(), rb.getStatus().name(),
                        rb.getCompletedSteps().stream()
                                .map(ReworkSubBatch.ReworkStep::getOperationCode).toList(),
                        resultRepository.findBySubBatchIdOrderByResultVersionAsc(rb.getId()).stream()
                                .map(r -> new ReinspectionView(r.getResultVersion(),
                                        r.getDecision().name(), r.getRemark(), r.getInspector(),
                                        r.getCreatedAt())).toList(),
                        rb.getMergedAt());
            }
            ScrapRecord sr = disposition.getScrapRecord();
            if (sr != null) {
                scrap = new ScrapGenealogy(sr.getScrapNo(), sr.getQuantity(), sr.getReasonCode());
            }
            corrections = correctionRepository.findByDispositionIdOrderByCreatedAtAsc(disposition.getId())
                    .stream().map(c -> new CorrectionView(c.getActionCode(), c.getDetail(),
                            c.getOperator(), c.getCreatedAt())).toList();
        }
        return new NcGenealogy(nc.getNcNo(), nc.getDefectCode(), nc.getAffectedQuantity(),
                nc.getStatus().name(), businessNo, dispositionStatus,
                disposition == null ? 0 : disposition.getReworkQty(),
                disposition == null ? 0 : disposition.getScrapQty(),
                disposition == null ? 0 : disposition.getConcessionQty(),
                rework, scrap, corrections);
    }

    private MovementView toMovementView(StockMovement m) {
        return new MovementView(m.getType().name(), m.getQuantity(), m.getRefNo(), m.getCreatedAt());
    }

    // ---- 查询视图记录 ----

    public record QuantityTrace(String ncNo, int affectedQuantity, String ncStatus, int reworkQty,
                                int reworkInProgressQty, int reworkMergedQty, int scrapQty,
                                int concessionQty, List<MovementView> movements) {
    }

    public record MovementView(String type, int quantity, String refNo, Instant at) {
    }

    public record ReworkProgress(String subBatchNo, int quantity, String status,
                                 List<String> requiredOperations, List<String> completedOperations,
                                 Instant mergedAt) {
    }

    public record ReinspectionView(int version, String decision, String remark, String inspector,
                                   Instant at) {
    }

    public record ReinspectionHistory(String subBatchNo, ReinspectionView latest,
                                      List<ReinspectionView> results) {
    }

    public record BatchGenealogy(String batchNo, String productCode, int initialQuantity,
                                 int availableQuantity, List<NcGenealogy> nonconformances,
                                 List<MovementView> movements) {
    }

    public record NcGenealogy(String ncNo, String defectCode, int affectedQuantity, String ncStatus,
                              String businessNo, String dispositionStatus, int reworkQty, int scrapQty,
                              int concessionQty, ReworkGenealogy rework, ScrapGenealogy scrap,
                              List<CorrectionView> corrections) {
    }

    public record ReworkGenealogy(String subBatchNo, int quantity, String status,
                                  List<String> completedOperations,
                                  List<ReinspectionView> reinspections, Instant mergedAt) {
    }

    public record ScrapGenealogy(String scrapNo, int quantity, String reasonCode) {
    }

    public record CorrectionView(String actionCode, String detail, String operator, Instant at) {
    }
}
