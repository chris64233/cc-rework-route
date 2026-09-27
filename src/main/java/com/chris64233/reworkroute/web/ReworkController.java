package com.chris64233.reworkroute.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.reworkroute.service.QueryService;
import com.chris64233.reworkroute.service.ReworkService;
import com.chris64233.reworkroute.web.dto.CompleteOperationRequest;
import com.chris64233.reworkroute.web.dto.InspectionHistoryView;
import com.chris64233.reworkroute.web.dto.ReworkProgressView;
import com.chris64233.reworkroute.web.dto.SubmitInspectionRequest;

@RestController
@RequestMapping("/api/rework-sub-batches")
public class ReworkController {

    private final ReworkService reworkService;
    private final QueryService queryService;

    public ReworkController(ReworkService reworkService, QueryService queryService) {
        this.reworkService = reworkService;
        this.queryService = queryService;
    }

    /** 按顺序完成下一道指定返工工序。 */
    @PostMapping("/{subBatchNo}/operations")
    public ReworkProgressView completeOperation(@PathVariable String subBatchNo,
                                                @Valid @RequestBody CompleteOperationRequest request) {
        reworkService.completeOperation(subBatchNo, request);
        return queryService.getReworkProgress(subBatchNo);
    }

    /** 提交复验结果（带版本号，expectedVersion 为客户端持有的当前版本）。 */
    @PostMapping("/{subBatchNo}/inspections")
    public InspectionHistoryView submitInspection(@PathVariable String subBatchNo,
                                                  @Valid @RequestBody SubmitInspectionRequest request) {
        reworkService.submitInspection(subBatchNo, request);
        return queryService.getInspectionHistory(subBatchNo);
    }

    /** 最新版本复验合格后放行，数量重新并入原批次可用库存。 */
    @PostMapping("/{subBatchNo}/release")
    public ReworkProgressView release(@PathVariable String subBatchNo) {
        reworkService.release(subBatchNo);
        return queryService.getReworkProgress(subBatchNo);
    }

    @GetMapping("/{subBatchNo}")
    public ReworkProgressView getProgress(@PathVariable String subBatchNo) {
        return queryService.getReworkProgress(subBatchNo);
    }

    @GetMapping("/{subBatchNo}/inspections")
    public InspectionHistoryView getInspections(@PathVariable String subBatchNo) {
        return queryService.getInspectionHistory(subBatchNo);
    }
}
