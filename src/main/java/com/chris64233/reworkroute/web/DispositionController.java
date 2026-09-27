package com.chris64233.reworkroute.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.reworkroute.domain.CorrectionRecord;
import com.chris64233.reworkroute.domain.DispositionOrder;
import com.chris64233.reworkroute.service.DispositionService;
import com.chris64233.reworkroute.service.QueryService;
import com.chris64233.reworkroute.web.dto.AddCorrectionRequest;
import com.chris64233.reworkroute.web.dto.CreateDispositionRequest;
import com.chris64233.reworkroute.web.dto.DispositionView;

@RestController
@RequestMapping("/api/dispositions")
public class DispositionController {

    private final DispositionService dispositionService;
    private final QueryService queryService;

    public DispositionController(DispositionService dispositionService, QueryService queryService) {
        this.dispositionService = dispositionService;
        this.queryService = queryService;
    }

    /**
     * 提出处置决定（confirm=true 时同时原子确认）。
     * 业务号幂等：相同内容重放 200 返回原结果（replayed=true），内容不同 409。
     */
    @PostMapping
    public DispositionView createOrReplay(@Valid @RequestBody CreateDispositionRequest request) {
        DispositionService.CreateResult result = dispositionService.createOrReplay(request);
        return queryService.toDispositionView(result.order(), result.replayed());
    }

    /** 确认处置：原子生成返工子批次 / 报废记录 / 让步并入库存。 */
    @PostMapping("/{businessNo}/confirm")
    public DispositionView confirm(@PathVariable String businessNo) {
        DispositionOrder order = dispositionService.confirm(businessNo);
        return queryService.toDispositionView(order, false);
    }

    @GetMapping("/{businessNo}")
    public DispositionView get(@PathVariable String businessNo) {
        return queryService.getDisposition(businessNo);
    }

    /** 对已确认处置追加纠正记录（不可修改原处置）。 */
    @PostMapping("/{businessNo}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public DispositionView addCorrection(@PathVariable String businessNo,
                                         @Valid @RequestBody AddCorrectionRequest request) {
        CorrectionRecord correction = dispositionService.addCorrection(businessNo, request);
        return queryService.toDispositionView(correction.getDisposition(), false);
    }
}
