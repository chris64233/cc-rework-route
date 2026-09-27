package com.chris64233.reworkroute.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.service.BatchService;
import com.chris64233.reworkroute.service.QueryService;
import com.chris64233.reworkroute.web.dto.BatchView;
import com.chris64233.reworkroute.web.dto.CreateBatchRequest;
import com.chris64233.reworkroute.web.dto.CreateNcRequest;
import com.chris64233.reworkroute.web.dto.NcView;

@RestController
@RequestMapping("/api")
public class BatchController {

    private final BatchService batchService;
    private final QueryService queryService;

    public BatchController(BatchService batchService, QueryService queryService) {
        this.batchService = batchService;
        this.queryService = queryService;
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchView createBatch(@Valid @RequestBody CreateBatchRequest request) {
        ProductionBatch batch = batchService.createBatch(request);
        return new BatchView(batch.getBatchNo(), batch.getMaterialCode(),
                batch.getTotalQuantity(), batch.getAvailableQuantity());
    }

    @GetMapping("/batches/{batchNo}")
    public BatchView getBatch(@PathVariable String batchNo) {
        return queryService.getBatch(batchNo);
    }

    @PostMapping("/non-conformances")
    @ResponseStatus(HttpStatus.CREATED)
    public NcView registerNc(@Valid @RequestBody CreateNcRequest request) {
        NonConformingRecord nc = batchService.registerNc(request);
        return queryService.getNc(nc.getNcNo());
    }

    @GetMapping("/non-conformances/{ncNo}")
    public NcView getNc(@PathVariable String ncNo) {
        return queryService.getNc(ncNo);
    }

    @GetMapping("/batches/{batchNo}/non-conformances")
    public List<NcView> listNcByBatch(@PathVariable String batchNo) {
        return queryService.listNcByBatch(batchNo);
    }
}
