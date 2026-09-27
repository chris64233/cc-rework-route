package com.chris64233.reworkroute.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.reworkroute.domain.BatchGenealogyEdge;
import com.chris64233.reworkroute.domain.DispositionLine;
import com.chris64233.reworkroute.domain.DispositionOrder;
import com.chris64233.reworkroute.domain.DispositionType;
import com.chris64233.reworkroute.domain.InspectionResult;
import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.domain.ReworkStatus;
import com.chris64233.reworkroute.domain.ReworkSubBatch;
import com.chris64233.reworkroute.repo.BatchGenealogyEdgeRepository;
import com.chris64233.reworkroute.repo.DispositionOrderRepository;
import com.chris64233.reworkroute.repo.InspectionResultRepository;
import com.chris64233.reworkroute.repo.NonConformingRecordRepository;
import com.chris64233.reworkroute.repo.OperationCompletionRepository;
import com.chris64233.reworkroute.repo.ProductionBatchRepository;
import com.chris64233.reworkroute.repo.ReworkSubBatchRepository;
import com.chris64233.reworkroute.support.NotFoundException;
import com.chris64233.reworkroute.web.dto.BatchView;
import com.chris64233.reworkroute.web.dto.CorrectionView;
import com.chris64233.reworkroute.web.dto.DispositionView;
import com.chris64233.reworkroute.web.dto.GenealogyView;
import com.chris64233.reworkroute.web.dto.InspectionHistoryView;
import com.chris64233.reworkroute.web.dto.NcView;
import com.chris64233.reworkroute.web.dto.QuantityDestinationView;
import com.chris64233.reworkroute.web.dto.ReworkProgressView;

/**
 * 只读查询：不合格数量去向、返工进度、复验结果、批次谱系与处置明细。
 */
@Service
public class QueryService {

    private final ProductionBatchRepository batchRepository;
    private final NonConformingRecordRepository ncRepository;
    private final DispositionOrderRepository dispositionRepository;
    private final ReworkSubBatchRepository reworkRepository;
    private final OperationCompletionRepository operationRepository;
    private final InspectionResultRepository inspectionRepository;
    private final BatchGenealogyEdgeRepository genealogyRepository;

    public QueryService(ProductionBatchRepository batchRepository,
                        NonConformingRecordRepository ncRepository,
                        DispositionOrderRepository dispositionRepository,
                        ReworkSubBatchRepository reworkRepository,
                        OperationCompletionRepository operationRepository,
                        InspectionResultRepository inspectionRepository,
                        BatchGenealogyEdgeRepository genealogyRepository) {
        this.batchRepository = batchRepository;
        this.ncRepository = ncRepository;
        this.dispositionRepository = dispositionRepository;
        this.reworkRepository = reworkRepository;
        this.operationRepository = operationRepository;
        this.inspectionRepository = inspectionRepository;
        this.genealogyRepository = genealogyRepository;
    }

    @Transactional(readOnly = true)
    public BatchView getBatch(String batchNo) {
        ProductionBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + batchNo));
        return new BatchView(batch.getBatchNo(), batch.getMaterialCode(),
                batch.getTotalQuantity(), batch.getAvailableQuantity());
    }

    @Transactional(readOnly = true)
    public NcView getNc(String ncNo) {
        NonConformingRecord nc = ncRepository.findByNcNo(ncNo)
                .orElseThrow(() -> new NotFoundException("不合格记录不存在: " + ncNo));
        return toNcView(nc);
    }

    private NcView toNcView(NonConformingRecord nc) {
        return new NcView(nc.getNcNo(), nc.getBatch().getBatchNo(), nc.getBatch().getMaterialCode(),
                nc.getAffectedQuantity(), nc.getConsumedQuantity(), nc.remainingQuantity(),
                nc.getStatus().name(), nc.getDefectDescription());
    }

    /** 不合格数量去向：汇总返工/报废/让步三部分及每条去向的落账结果。 */
    @Transactional(readOnly = true)
    public QuantityDestinationView getQuantityDestination(String ncNo) {
        NonConformingRecord nc = ncRepository.findByNcNo(ncNo)
                .orElseThrow(() -> new NotFoundException("不合格记录不存在: " + ncNo));

        var rework = java.math.BigDecimal.ZERO;
        var scrap = java.math.BigDecimal.ZERO;
        var concession = java.math.BigDecimal.ZERO;
        java.util.List<QuantityDestinationView.Item> items = new java.util.ArrayList<>();

        for (DispositionOrder order : dispositionRepository.findByNcRecordNcNoOrderByIdAsc(ncNo)) {
            for (DispositionLine line : order.getLines()) {
                switch (line.getType()) {
                    case REWORK -> {
                        rework = rework.add(line.getQuantity());
                        ReworkSubBatch rb = line.getReworkSubBatch();
                        items.add(new QuantityDestinationView.Item(
                                DispositionType.REWORK.name(), line.getQuantity(),
                                rb == null ? null : rb.getSubBatchNo(),
                                rb == null ? "PENDING" : rb.getStatus().name()));
                    }
                    case SCRAP -> {
                        scrap = scrap.add(line.getQuantity());
                        var sr = line.getScrapRecord();
                        items.add(new QuantityDestinationView.Item(
                                DispositionType.SCRAP.name(), line.getQuantity(),
                                sr == null ? null : sr.getScrapNo(),
                                sr == null ? "PENDING" : "SCRAPPED"));
                    }
                    case CONCESSION -> {
                        concession = concession.add(line.getQuantity());
                        items.add(new QuantityDestinationView.Item(
                                DispositionType.CONCESSION.name(), line.getQuantity(),
                                order.getBusinessNo(), "ACCEPTED"));
                    }
                }
            }
        }
        return new QuantityDestinationView(ncNo, nc.getBatch().getBatchNo(),
                nc.getAffectedQuantity(), nc.getConsumedQuantity(), nc.remainingQuantity(),
                rework, scrap, concession, items);
    }

    /** 返工进度：指定工序序列、已完成工序、当前应完成工序及复验状态。 */
    @Transactional(readOnly = true)
    public ReworkProgressView getReworkProgress(String subBatchNo) {
        ReworkSubBatch rb = reworkRepository.findBySubBatchNo(subBatchNo)
                .orElseThrow(() -> new NotFoundException("返工子批次不存在: " + subBatchNo));
        List<String> required = List.of(rb.getRequiredOperations().split(","));
        String currentOperation = rb.allOperationsCompleted()
                ? null : required.get(rb.getCompletedOperationCount());
        return new ReworkProgressView(rb.getSubBatchNo(), rb.getNcRecord().getNcNo(),
                rb.getDisposition().getBusinessNo(), rb.getQuantity(), required,
                rb.getCompletedOperationCount(), rb.getRequiredOperationCount(), currentOperation,
                rb.getStatus().name(), rb.getInspectionVersion(),
                rb.getLatestVerdict() == null ? null : rb.getLatestVerdict().name(),
                rb.getReintegratedAt() == null ? null : rb.getReintegratedAt().toString());
    }

    /** 复验结果：按版本顺序返回全部复验结果，并标注当前是否允许/已经放行。 */
    @Transactional(readOnly = true)
    public InspectionHistoryView getInspectionHistory(String subBatchNo) {
        ReworkSubBatch rb = reworkRepository.findBySubBatchNo(subBatchNo)
                .orElseThrow(() -> new NotFoundException("返工子批次不存在: " + subBatchNo));
        List<InspectionResult> results =
                inspectionRepository.findByReworkSubBatchIdOrderByVersionAsc(rb.getId());
        List<InspectionHistoryView.ResultView> views = results.stream()
                .map(r -> new InspectionHistoryView.ResultView(r.getVersion(), r.getVerdict().name(),
                        r.getInspector(), r.getRemark(), r.getCreatedAt().toString()))
                .toList();
        boolean released = rb.getStatus() == ReworkStatus.REINTEGRATED;
        return new InspectionHistoryView(rb.getSubBatchNo(), rb.getStatus().name(), views, released);
    }

    @Transactional(readOnly = true)
    public DispositionView getDisposition(String businessNo) {
        DispositionOrder order = dispositionRepository.findByBusinessNo(businessNo)
                .orElseThrow(() -> new NotFoundException("处置单不存在: " + businessNo));
        return toDispositionView(order, false);
    }

    /** 在只读事务内装配处置视图（lines / corrections 为懒加载集合）。 */
    @Transactional(readOnly = true)
    public DispositionView toDispositionView(DispositionOrder order, boolean replayed) {
        // 调用方持有的可能是事务提交后的游离实体，这里重新加载为托管实例以初始化集合
        DispositionOrder attached = dispositionRepository.findById(order.getId()).orElse(order);
        List<String> operations = attached.getReworkOperations() == null
                || attached.getReworkOperations().isEmpty()
                ? List.of() : List.of(attached.getReworkOperations().split(","));
        List<DispositionView.LineView> lines = attached.getLines().stream()
                .map(l -> new DispositionView.LineView(l.getType().name(), l.getQuantity(),
                        l.getReworkSubBatch() == null ? null : l.getReworkSubBatch().getSubBatchNo(),
                        l.getScrapRecord() == null ? null : l.getScrapRecord().getScrapNo()))
                .toList();
        List<CorrectionView> corrections = attached.getCorrections().stream()
                .map(c -> new CorrectionView(c.getCorrectionType(), c.getContent(),
                        c.getOperator(), c.getCreatedAt().toString()))
                .toList();
        return new DispositionView(attached.getBusinessNo(), attached.getNcRecord().getNcNo(),
                attached.getStatus().name(), attached.getReworkQuantity(), attached.getScrapQuantity(),
                attached.getConcessionQuantity(), operations, lines, corrections, replayed);
    }

    /** 批次谱系：不合格数量从原批次出发的全部流转边（返工/报废/让步/复验回补）。 */
    @Transactional(readOnly = true)
    public GenealogyView getGenealogy(String ncNo) {
        NonConformingRecord nc = ncRepository.findByNcNo(ncNo)
                .orElseThrow(() -> new NotFoundException("不合格记录不存在: " + ncNo));
        List<BatchGenealogyEdge> edges = genealogyRepository.findByNcRecordNcNoOrderByIdAsc(ncNo);
        List<GenealogyView.EdgeView> views = edges.stream().map(e -> {
            String source = e.getSourceBatch() != null ? e.getSourceBatch().getBatchNo()
                    : e.getReworkSubBatch() != null ? e.getReworkSubBatch().getSubBatchNo() : null;
            String target = e.getTargetBatch() != null ? e.getTargetBatch().getBatchNo()
                    : switch (e.getType()) {
                        case SCRAP -> e.getScrapRecord() != null ? e.getScrapRecord().getScrapNo() : "SCRAP";
                        case CONCESSION -> "CONCESSION_ACCEPTED";
                        default -> null;
                    };
            return new GenealogyView.EdgeView(e.getType().name(), source, target,
                    e.getNcRecord().getNcNo(),
                    e.getDisposition() == null ? null : e.getDisposition().getBusinessNo(),
                    e.getQuantity().toPlainString(),
                    e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        }).toList();
        return new GenealogyView(nc.getBatch().getBatchNo(), views);
    }

    /** 按批次列出其全部不合格记录，供总览查询。 */
    @Transactional(readOnly = true)
    public List<NcView> listNcByBatch(String batchNo) {
        batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("批次不存在: " + batchNo));
        return ncRepository.findByBatchBatchNo(batchNo).stream().map(this::toNcView).toList();
    }

    /** 按不合格记录列出全部返工子批次进度。 */
    @Transactional(readOnly = true)
    public List<ReworkProgressView> listReworkByNc(String ncNo) {
        ncRepository.findByNcNo(ncNo)
                .orElseThrow(() -> new NotFoundException("不合格记录不存在: " + ncNo));
        return reworkRepository.findByNcRecordNcNo(ncNo).stream().map(rb -> {
            List<String> required = List.of(rb.getRequiredOperations().split(","));
            String currentOperation = rb.allOperationsCompleted()
                    ? null : required.get(rb.getCompletedOperationCount());
            return new ReworkProgressView(rb.getSubBatchNo(), ncNo,
                    rb.getDisposition().getBusinessNo(), rb.getQuantity(), required,
                    rb.getCompletedOperationCount(), rb.getRequiredOperationCount(), currentOperation,
                    rb.getStatus().name(), rb.getInspectionVersion(),
                    rb.getLatestVerdict() == null ? null : rb.getLatestVerdict().name(),
                    rb.getReintegratedAt() == null ? null : rb.getReintegratedAt().toString());
        }).toList();
    }
}
