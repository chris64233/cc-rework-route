package com.chris64233.reworkroute.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import com.chris64233.reworkroute.domain.BatchGenealogyEdge;
import com.chris64233.reworkroute.domain.CorrectionRecord;
import com.chris64233.reworkroute.domain.DispositionLine;
import com.chris64233.reworkroute.domain.DispositionOrder;
import com.chris64233.reworkroute.domain.DispositionStatus;
import com.chris64233.reworkroute.domain.DispositionType;
import com.chris64233.reworkroute.domain.GenealogyType;
import com.chris64233.reworkroute.domain.NcStatus;
import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.domain.ReworkSubBatch;
import com.chris64233.reworkroute.domain.ScrapRecord;
import com.chris64233.reworkroute.repo.BatchGenealogyEdgeRepository;
import com.chris64233.reworkroute.repo.DispositionOrderRepository;
import com.chris64233.reworkroute.repo.NonConformingRecordRepository;
import com.chris64233.reworkroute.repo.ProductionBatchRepository;
import com.chris64233.reworkroute.repo.ReworkSubBatchRepository;
import com.chris64233.reworkroute.repo.ScrapRecordRepository;
import com.chris64233.reworkroute.support.BusinessRuleException;
import com.chris64233.reworkroute.support.ContentHasher;
import com.chris64233.reworkroute.support.IdempotencyConflictException;
import com.chris64233.reworkroute.support.NotFoundException;
import com.chris64233.reworkroute.support.Quantities;
import com.chris64233.reworkroute.web.dto.AddCorrectionRequest;
import com.chris64233.reworkroute.web.dto.CreateDispositionRequest;

@Service
public class DispositionService {

    private final DispositionOrderRepository dispositionRepository;
    private final NonConformingRecordRepository ncRepository;
    private final ProductionBatchRepository batchRepository;
    private final BatchGenealogyEdgeRepository genealogyRepository;
    private final ReworkSubBatchRepository reworkRepository;
    private final ScrapRecordRepository scrapRepository;
    private final TransactionTemplate transactionTemplate;

    public DispositionService(DispositionOrderRepository dispositionRepository,
                              NonConformingRecordRepository ncRepository,
                              ProductionBatchRepository batchRepository,
                              BatchGenealogyEdgeRepository genealogyRepository,
                              ReworkSubBatchRepository reworkRepository,
                              ScrapRecordRepository scrapRepository,
                              TransactionTemplate transactionTemplate) {
        this.dispositionRepository = dispositionRepository;
        this.ncRepository = ncRepository;
        this.batchRepository = batchRepository;
        this.genealogyRepository = genealogyRepository;
        this.reworkRepository = reworkRepository;
        this.scrapRepository = scrapRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 按业务号分段的 JVM 内锁：相同业务号的并发提交在本进程内串行。
     * 事务在锁内通过 {@link TransactionTemplate} 提交，保证等待线程拿到锁时
     * 前一个事务已经提交可见；数据库唯一约束仍是跨实例的最终防线。
     */
    private final ConcurrentHashMap<String, Object> businessLocks = new ConcurrentHashMap<>();

    /**
     * 提出（可选地同时确认）处置决定。
     *
     * <p>业务号幂等：相同内容重放返回原处置（replayed=true），不重复消费数量；
     * 同业务号但内容不同抛 {@link IdempotencyConflictException}。</p>
     */
    public CreateResult createOrReplay(CreateDispositionRequest request) {
        Object lock = businessLocks.computeIfAbsent(request.businessNo(), k -> new Object());
        synchronized (lock) {
            try {
                return transactionTemplate.execute(status -> doCreateOrReplay(request));
            } catch (DataIntegrityViolationException duplicate) {
                // 跨实例并发时由业务号唯一约束兜底：事务已回滚，新开只读事务读取胜者并按幂等语义处理
                return transactionTemplate.execute(status -> handleDuplicate(request, duplicate));
            } finally {
                businessLocks.remove(request.businessNo(), lock);
            }
        }
    }

    private CreateResult handleDuplicate(CreateDispositionRequest request,
                                         DataIntegrityViolationException duplicate) {
        BigDecimal rework = Quantities.requireNonNegative(request.reworkQuantity(), "reworkQuantity");
        BigDecimal scrap = Quantities.requireNonNegative(request.scrapQuantity(), "scrapQuantity");
        BigDecimal concession = Quantities.requireNonNegative(request.concessionQuantity(), "concessionQuantity");
        List<String> operations = Quantities.parseOperations(
                request.operations() == null ? null : String.join(",", request.operations()),
                rework.signum() > 0);
        String hash = ContentHasher.hashDisposition(request.ncNo(), rework, scrap, concession, operations);
        DispositionOrder winner = dispositionRepository.findByBusinessNo(request.businessNo())
                .orElseThrow(() -> new IllegalStateException(
                        "处置业务号 " + request.businessNo() + " 唯一约束冲突但未找到已存在记录", duplicate));
        if (!winner.getContentHash().equals(hash)) {
            throw new IdempotencyConflictException(
                    "处置业务号 " + request.businessNo() + " 已存在但提交内容不同，拒绝重放");
        }
        return new CreateResult(winner, true);
    }

    private CreateResult doCreateOrReplay(CreateDispositionRequest request) {
        BigDecimal rework = Quantities.requireNonNegative(request.reworkQuantity(), "reworkQuantity");
        BigDecimal scrap = Quantities.requireNonNegative(request.scrapQuantity(), "scrapQuantity");
        BigDecimal concession = Quantities.requireNonNegative(request.concessionQuantity(), "concessionQuantity");
        List<String> operations = Quantities.parseOperations(
                request.operations() == null ? null : String.join(",", request.operations()),
                rework.signum() > 0);
        String hash = ContentHasher.hashDisposition(request.ncNo(), rework, scrap, concession, operations);

        DispositionOrder existing = dispositionRepository.findByBusinessNo(request.businessNo()).orElse(null);
        if (existing != null) {
            if (!existing.getContentHash().equals(hash)) {
                throw new IdempotencyConflictException(
                        "处置业务号 " + request.businessNo() + " 已存在但提交内容不同，拒绝重放");
            }
            if (request.confirm() && existing.getStatus() == DispositionStatus.PENDING) {
                confirmLocked(existing);
            }
            return new CreateResult(existing, true);
        }

        // 首次提交：校验三部分之和必须与原记录一致
        NonConformingRecord nc = ncRepository.findByNcNo(request.ncNo())
                .orElseThrow(() -> new NotFoundException("不合格记录不存在: " + request.ncNo()));
        if (nc.getStatus() != NcStatus.OPEN) {
            throw new BusinessRuleException("不合格记录 " + request.ncNo() + " 已处置，不能重复提出处置决定");
        }
        BigDecimal total = rework.add(scrap).add(concession);
        if (total.compareTo(nc.getAffectedQuantity()) != 0) {
            throw new BusinessRuleException("返工+报废+让步数量之和 " + total
                    + " 必须与原记录受影响数量 " + nc.getAffectedQuantity() + " 一致");
        }

        DispositionOrder order = new DispositionOrder(
                request.businessNo(), nc, hash, rework, scrap, concession,
                String.join(",", operations));
        addLine(order, DispositionType.REWORK, rework);
        addLine(order, DispositionType.SCRAP, scrap);
        addLine(order, DispositionType.CONCESSION, concession);
        dispositionRepository.save(order);

        if (request.confirm()) {
            confirmLocked(order);
        }
        return new CreateResult(order, false);
    }

    private void addLine(DispositionOrder order, DispositionType type, BigDecimal quantity) {
        if (quantity.signum() > 0) {
            order.getLines().add(new DispositionLine(order, type, quantity));
        }
    }

    /**
     * 确认已提出的处置决定。
     */
    @Transactional
    public DispositionOrder confirm(String businessNo) {
        DispositionOrder order = dispositionRepository.findByBusinessNo(businessNo)
                .orElseThrow(() -> new NotFoundException("处置单不存在: " + businessNo));
        if (order.getStatus() == DispositionStatus.CONFIRMED) {
            return order;
        }
        return confirmLocked(order);
    }

    /**
     * 原子确认：在同一事务内生成返工子批次、报废记录、让步并入库存并消费原始数量。
     *
     * <p>并发安全：先对不合格记录加行级写锁，再校验状态与数量；两个并发确认只有一个能通过，
     * 另一个看到记录已处置而回滚，不会留下任何部分结果。</p>
     */
    private DispositionOrder confirmLocked(DispositionOrder order) {
        NonConformingRecord nc = ncRepository.findWithLockingByNcNo(order.getNcRecord().getNcNo())
                .orElseThrow(() -> new NotFoundException("不合格记录不存在: " + order.getNcRecord().getNcNo()));
        if (nc.getStatus() != NcStatus.OPEN) {
            throw new BusinessRuleException("不合格记录 " + nc.getNcNo() + " 已被处置，原始数量不能重复消费");
        }
        if (nc.getConsumedQuantity().signum() != 0
                || nc.remainingQuantity().compareTo(order.totalQuantity()) != 0) {
            throw new BusinessRuleException("处置数量与未消费数量不一致，拒绝确认");
        }

        ProductionBatch batch = batchRepository.findWithLockingById(nc.getBatch().getId())
                .orElseThrow(() -> new NotFoundException("批次不存在: " + nc.getBatch().getBatchNo()));

        boolean hasRework = order.getReworkQuantity().signum() > 0;
        for (DispositionLine line : order.getLines()) {
            switch (line.getType()) {
                case REWORK -> {
                    List<String> ops = List.of(order.getReworkOperations().split(","));
                    ReworkSubBatch subBatch = reworkRepository.save(new ReworkSubBatch(
                            "RW-" + order.getBusinessNo(), batch, nc, order, line.getQuantity(),
                            order.getReworkOperations(), ops.size()));
                    line.setReworkSubBatch(subBatch);
                    genealogyRepository.save(BatchGenealogyEdge
                            .builder(GenealogyType.REWORK, nc, line.getQuantity())
                            .source(batch).disposition(order).rework(subBatch).build());
                }
                case SCRAP -> {
                    ScrapRecord scrap = scrapRepository.save(new ScrapRecord(
                            "SCRAP-" + order.getBusinessNo(), batch, order, line.getQuantity(),
                            "处置决定报废"));
                    line.setScrapRecord(scrap);
                    genealogyRepository.save(BatchGenealogyEdge
                            .builder(GenealogyType.SCRAP, nc, line.getQuantity())
                            .source(batch).disposition(order).scrap(scrap).build());
                }
                case CONCESSION -> {
                    // 让步接收：登记时冻结的数量重新回到可用库存
                    batch.setAvailableQuantity(
                            batch.getAvailableQuantity().add(line.getQuantity()));
                    genealogyRepository.save(BatchGenealogyEdge
                            .builder(GenealogyType.CONCESSION, nc, line.getQuantity())
                            .source(batch).disposition(order).build());
                }
            }
        }

        nc.setConsumedQuantity(nc.getAffectedQuantity());
        nc.setStatus(hasRework ? NcStatus.IN_REWORK : NcStatus.CLOSED);
        order.setStatus(DispositionStatus.CONFIRMED);
        dispositionRepository.save(order);
        return order;
    }

    /**
     * 对已确认处置追加纠正记录。已确认处置的数量与状态不可修改，只能追加、保留完整谱系。
     */
    @Transactional
    public CorrectionRecord addCorrection(String businessNo, AddCorrectionRequest request) {
        DispositionOrder order = dispositionRepository.findByBusinessNo(businessNo)
                .orElseThrow(() -> new NotFoundException("处置单不存在: " + businessNo));
        if (order.getStatus() != DispositionStatus.CONFIRMED) {
            throw new BusinessRuleException("仅已确认的处置可以追加纠正记录");
        }
        CorrectionRecord correction = new CorrectionRecord(
                order, request.correctionType(), request.content(), request.operator());
        order.getCorrections().add(correction);
        dispositionRepository.save(order);
        return correction;
    }

    @Transactional(readOnly = true)
    public DispositionOrder requireByBusinessNo(String businessNo) {
        return dispositionRepository.findByBusinessNo(businessNo)
                .orElseThrow(() -> new NotFoundException("处置单不存在: " + businessNo));
    }

    /** 创建/重放结果。replayed=true 表示命中幂等，返回的是原处置。 */
    public record CreateResult(DispositionOrder order, boolean replayed) {
    }
}
