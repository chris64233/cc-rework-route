package com.chris64233.reworkroute.web.dto;

import java.util.List;

public record InspectionHistoryView(String subBatchNo, String status, List<ResultView> results,
                                    boolean released) {

    public record ResultView(int version, String verdict, String inspector, String remark,
                             String createdAt) {
    }
}
