package com.chris64233.reworkroute.web;

import com.chris64233.reworkroute.domain.NonconformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.service.BatchService;
import jakarta.validation.Valid;
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
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.BatchView createBatch(@Valid @RequestBody Dtos.CreateBatchRequest request) {
        ProductionBatch batch = batchService.createBatch(request.batchNo(), request.productCode(),
                request.initialQuantity());
        return toBatchView(batch);
    }

    @GetMapping("/batches/{batchNo}")
    public Views.BatchView getBatch(@PathVariable String batchNo) {
        return toBatchView(batchService.getBatch(batchNo));
    }

    @PostMapping("/nonconformances")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.NcView openNc(@Valid @RequestBody Dtos.OpenNcRequest request) {
        NonconformingRecord nc = batchService.openNonconformance(request.ncNo(), request.batchNo(),
                request.defectCode(), request.affectedQuantity());
        return toNcView(nc);
    }

    @GetMapping("/nonconformances/{ncNo}")
    public Views.NcView getNc(@PathVariable String ncNo) {
        return toNcView(batchService.getNc(ncNo));
    }

    private static Views.BatchView toBatchView(ProductionBatch b) {
        return new Views.BatchView(b.getBatchNo(), b.getProductCode(), b.getInitialQuantity(),
                b.getAvailableQuantity(), b.getCreatedAt());
    }

    private static Views.NcView toNcView(NonconformingRecord n) {
        return new Views.NcView(n.getNcNo(), n.getBatch().getBatchNo(), n.getDefectCode(),
                n.getAffectedQuantity(), n.getStatus().name(), n.getCreatedAt(), n.getDisposedAt());
    }
}
