package com.chris64233.reworkroute.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.reworkroute.service.QueryService;
import com.chris64233.reworkroute.web.dto.GenealogyView;
import com.chris64233.reworkroute.web.dto.QuantityDestinationView;
import com.chris64233.reworkroute.web.dto.ReworkProgressView;

@RestController
@RequestMapping("/api/non-conformances/{ncNo}")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /** 不合格数量去向：返工 / 报废 / 让步三部分及落账结果。 */
    @GetMapping("/destinations")
    public QuantityDestinationView destinations(@PathVariable String ncNo) {
        return queryService.getQuantityDestination(ncNo);
    }

    /** 不合格记录下全部返工子批次的进度。 */
    @GetMapping("/rework-progress")
    public List<ReworkProgressView> reworkProgress(@PathVariable String ncNo) {
        return queryService.listReworkByNc(ncNo);
    }

    /** 批次谱系：返工 / 报废 / 让步 / 复验回补的完整流转边。 */
    @GetMapping("/genealogy")
    public GenealogyView genealogy(@PathVariable String ncNo) {
        return queryService.getGenealogy(ncNo);
    }
}
