package com.chris64233.reworkroute.web;

import com.chris64233.reworkroute.domain.CorrectionEntry;
import com.chris64233.reworkroute.domain.Disposition;
import com.chris64233.reworkroute.domain.ReinspectionDecision;
import com.chris64233.reworkroute.domain.ReinspectionResult;
import com.chris64233.reworkroute.domain.ReworkSubBatch;
import com.chris64233.reworkroute.service.BusinessConflictException;
import com.chris64233.reworkroute.service.DispositionService;
import com.chris64233.reworkroute.service.ReworkService;
import com.chris64233.reworkroute.service.TraceQueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DispositionController {

    private final DispositionService dispositionService;
    private final ReworkService reworkService;
    private final TraceQueryService traceService;

    public DispositionController(DispositionService dispositionService, ReworkService reworkService,
                                 TraceQueryService traceService) {
        this.dispositionService = dispositionService;
        this.reworkService = reworkService;
        this.traceService = traceService;
    }

    /** 提交并确认处置（同业务号重放幂等，内容不同返回 409）。 */
    @PostMapping("/dispositions")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.DispositionView submit(@Valid @RequestBody Dtos.SubmitDispositionRequest request) {
        List<String> operations = request.requiredOperations() == null
                ? List.of() : request.requiredOperations();
        DispositionService.SubmitResult result = dispositionService.submitDisposition(
                request.businessNo(), request.ncNo(), request.reworkQty(), request.scrapQty(),
                request.concessionQty(), operations);
        return toView(result.disposition(), result.replayed());
    }

    /** 已确认处置只允许追加纠正记录。 */
    @PostMapping("/dispositions/{businessNo}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.CorrectionView appendCorrection(@PathVariable String businessNo,
                                                 @Valid @RequestBody Dtos.AppendCorrectionRequest request) {
        CorrectionEntry entry = dispositionService.appendCorrection(businessNo, request.actionCode(),
                request.detail(), request.operator());
        return new Views.CorrectionView(entry.getId(), businessNo, entry.getActionCode(),
                entry.getDetail(), entry.getOperator(), entry.getCreatedAt());
    }

    @PostMapping("/rework/operations/complete")
    @ResponseStatus(HttpStatus.OK)
    public Views.SubBatchView completeOperation(
            @Valid @RequestBody Dtos.CompleteOperationRequest request) {
        return toSubBatchView(reworkService.completeOperation(request.subBatchNo(),
                request.operationCode()));
    }

    @PostMapping("/rework/reinspections")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.ReinspectionView submitReinspection(
            @Valid @RequestBody Dtos.SubmitReinspectionRequest request) {
        ReinspectionDecision decision;
        try {
            decision = ReinspectionDecision.valueOf(request.decision());
        } catch (Exception e) {
            throw new BusinessConflictException("decision must be PASS or FAIL: " + request.decision());
        }
        ReinspectionResult result = reworkService.submitReinspection(request.subBatchNo(), decision,
                request.remark(), request.inspector());
        return new Views.ReinspectionView(result.getResultVersion(), result.getDecision().name(),
                result.getRemark(), result.getInspector(), result.getCreatedAt());
    }

    /** 复验合格并回可用库存；可带 expectedVersion 防止依据旧版本结果放行。 */
    @PostMapping("/rework/merge-back")
    @ResponseStatus(HttpStatus.OK)
    public Views.SubBatchView mergeBack(@RequestBody Dtos.MergeBackRequest request) {
        if (request.subBatchNo() == null || request.subBatchNo().isBlank()) {
            throw new BusinessConflictException("subBatchNo must not be blank");
        }
        return toSubBatchView(reworkService.mergeBack(request.subBatchNo(), request.expectedVersion()));
    }

    // ---- 查询 ----

    @GetMapping("/nonconformances/{ncNo}/quantity-trace")
    public TraceQueryService.QuantityTrace quantityTrace(@PathVariable String ncNo) {
        return traceService.traceQuantity(ncNo);
    }

    @GetMapping("/rework/{subBatchNo}/progress")
    public TraceQueryService.ReworkProgress reworkProgress(@PathVariable String subBatchNo) {
        return traceService.getReworkProgress(subBatchNo);
    }

    @GetMapping("/rework/{subBatchNo}/reinspections")
    public TraceQueryService.ReinspectionHistory reinspections(@PathVariable String subBatchNo) {
        return traceService.getReinspectionHistory(subBatchNo);
    }

    @GetMapping("/batches/{batchNo}/genealogy")
    public TraceQueryService.BatchGenealogy genealogy(@PathVariable String batchNo) {
        return traceService.getBatchGenealogy(batchNo);
    }

    private static Views.DispositionView toView(Disposition d, boolean replayed) {
        return new Views.DispositionView(d.getBusinessNo(), d.getNc().getNcNo(), d.getReworkQty(),
                d.getScrapQty(), d.getConcessionQty(),
                d.getRequiredOperations().stream().map(op -> op.getOperationCode()).toList(),
                d.getStatus().name(),
                d.getReworkSubBatch() == null ? null : d.getReworkSubBatch().getSubBatchNo(),
                d.getScrapRecord() == null ? null : d.getScrapRecord().getScrapNo(),
                replayed, d.getConfirmedAt());
    }

    private static Views.SubBatchView toSubBatchView(ReworkSubBatch s) {
        return new Views.SubBatchView(s.getSubBatchNo(), s.getParentBatch().getBatchNo(),
                s.getNc().getNcNo(), s.getQuantity(), s.getStatus().name(),
                s.getDisposition().getRequiredOperations().stream()
                        .map(op -> op.getOperationCode()).toList(),
                s.getCompletedSteps().stream().map(ReworkSubBatch.ReworkStep::getOperationCode).toList(),
                s.getCreatedAt(), s.getMergedAt());
    }
}
