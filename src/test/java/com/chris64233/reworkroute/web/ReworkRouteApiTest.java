package com.chris64233.reworkroute.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ReworkRouteApiTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static String uniq(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet();
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof String s && s.matches("-?\\d+(\\.\\d+)?")) {
                v = new BigDecimal(s);
            }
            map.put((String) kv[i], v);
        }
        return map;
    }

    private MvcResult postJson(String path, Object request) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andReturn();
    }

    /** 建立批次并登记不合格，返回 [batchNo, ncNo]。 */
    private String[] seedBatchWithNc(String total, String affected) throws Exception {
        String batchNo = uniq("B");
        mockMvc.perform(post("/api/batches").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("batchNo", batchNo, "materialCode", "MAT",
                                "totalQuantity", total))))
                .andExpect(status().isCreated());
        String ncNo = uniq("NC");
        mockMvc.perform(post("/api/non-conformances").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("ncNo", ncNo, "batchNo", batchNo,
                                "affectedQuantity", affected, "defectDescription", "外观不良"))))
                .andExpect(status().isCreated());
        return new String[] {batchNo, ncNo};
    }

    private Object dispositionRequest(String businessNo, String ncNo,
                                      String rework, String scrap, String concession,
                                      List<String> ops, boolean confirm) {
        return body("businessNo", businessNo, "ncNo", ncNo,
                "reworkQuantity", rework, "scrapQuantity", scrap, "concessionQuantity", concession,
                "operations", ops, "confirm", confirm);
    }

    @Test
    void 登记不合格必须关联存在的批次() throws Exception {
        mockMvc.perform(post("/api/non-conformances").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("ncNo", uniq("NC"), "batchNo", "B-NOPE",
                                "affectedQuantity", "1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 参数校验失败返回400() throws Exception {
        mockMvc.perform(post("/api/batches").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("materialCode", "MAT", "totalQuantity", "10"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 拆分数量不平返回422() throws Exception {
        String[] nc = seedBatchWithNc("100", "10");
        mockMvc.perform(post("/api/dispositions").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dispositionRequest(uniq("D"), nc[1], "4", "4", "1",
                                List.of("OP1"), false))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("必须与原记录")));
    }

    @Test
    void 完整闭环_提出确认_返工复验放行_幂等与冲突() throws Exception {
        String[] nc = seedBatchWithNc("100", "10");
        String bizNo = uniq("D");

        // 提出处置（不确认）
        mockMvc.perform(post("/api/dispositions").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dispositionRequest(bizNo, nc[1], "5", "3", "2",
                                List.of("OP1", "OP2"), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.replayed").value(false));

        // 相同内容重放 → 原结果
        mockMvc.perform(post("/api/dispositions").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dispositionRequest(bizNo, nc[1], "5", "3", "2",
                                List.of("OP1", "OP2"), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        // 内容不同 → 409
        mockMvc.perform(post("/api/dispositions").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dispositionRequest(bizNo, nc[1], "6", "3", "1",
                                List.of("OP1", "OP2"), false))))
                .andExpect(status().isConflict());

        // 确认
        mockMvc.perform(post("/api/dispositions/" + bizNo + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines[0].subBatchNo").value("RW-" + bizNo))
                .andExpect(jsonPath("$.lines[1].scrapNo").value("SCRAP-" + bizNo));

        // 已确认后同号不同内容仍然 409，不能修改
        mockMvc.perform(post("/api/dispositions").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dispositionRequest(bizNo, nc[1], "6", "3", "1",
                                List.of("OP1", "OP2"), true))))
                .andExpect(status().isConflict());

        String subBatchNo = "RW-" + bizNo;

        // 工序跳序 → 422
        mockMvc.perform(post("/api/rework-sub-batches/" + subBatchNo + "/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("operationCode", "OP2", "operator", "w"))))
                .andExpect(status().isUnprocessableEntity());

        // 顺序完成 OP1、OP2
        mockMvc.perform(post("/api/rework-sub-batches/" + subBatchNo + "/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("operationCode", "OP1", "operator", "w"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedOperationCount").value(1))
                .andExpect(jsonPath("$.currentOperation").value("OP2"));
        mockMvc.perform(post("/api/rework-sub-batches/" + subBatchNo + "/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("operationCode", "OP2", "operator", "w"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPERATIONS_DONE"))
                .andExpect(jsonPath("$.currentOperation").value(org.hamcrest.Matchers.nullValue()));

        // 工序完成前不能复验（此处工序已完成）；提交 v1 不合格
        mockMvc.perform(post("/api/rework-sub-batches/" + subBatchNo + "/inspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("verdict", "FAILED", "expectedVersion", 0,
                                "inspector", "qa", "remark", "仍超差"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1));

        // 最新版本不合格 → 放行 422
        mockMvc.perform(post("/api/rework-sub-batches/" + subBatchNo + "/release"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不得放行")));

        // 过期版本提交 → 409
        mockMvc.perform(post("/api/rework-sub-batches/" + subBatchNo + "/inspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("verdict", "PASSED", "expectedVersion", 0,
                                "inspector", "qa"))))
                .andExpect(status().isConflict());

        // v2 合格并放行
        mockMvc.perform(post("/api/rework-sub-batches/" + subBatchNo + "/inspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("verdict", "PASSED", "expectedVersion", 1,
                                "inspector", "qa"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2));
        mockMvc.perform(post("/api/rework-sub-batches/" + subBatchNo + "/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REINTEGRATED"));

        // 库存：100 - 10 + 让步2 + 返工5 = 97
        MvcResult batchResult = mockMvc.perform(get("/api/batches/" + nc[0])).andReturn();
        JsonNode batchJson = objectMapper.readTree(batchResult.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(batchJson.get("availableQuantity").decimalValue())
                .isEqualByComparingTo(new BigDecimal("97"));

        // 不合格记录关闭
        MvcResult ncResult = mockMvc.perform(get("/api/non-conformances/" + nc[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andReturn();
        JsonNode ncJson = objectMapper.readTree(ncResult.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(ncJson.get("consumedQuantity").decimalValue())
                .isEqualByComparingTo(new BigDecimal("10"));

        // 数量去向查询
        MvcResult destResult = mockMvc.perform(get("/api/non-conformances/" + nc[1] + "/destinations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn();
        JsonNode destJson = objectMapper.readTree(destResult.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(destJson.get("reworkQuantity").decimalValue())
                .isEqualByComparingTo("5");
        org.assertj.core.api.Assertions.assertThat(destJson.get("scrapQuantity").decimalValue())
                .isEqualByComparingTo("3");
        org.assertj.core.api.Assertions.assertThat(destJson.get("concessionQuantity").decimalValue())
                .isEqualByComparingTo("2");

        // 谱系查询
        mockMvc.perform(get("/api/non-conformances/" + nc[1] + "/genealogy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges.length()").value(4))
                .andExpect(jsonPath("$.edges[3].type").value("REINTEGRATE"))
                .andExpect(jsonPath("$.edges[3].target").value(nc[0]));

        // 追加纠正记录
        mockMvc.perform(post("/api/dispositions/" + bizNo + "/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body("correctionType", "ROOT_CAUSE",
                                "content", "定位偏差", "operator", "qa"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.corrections.length()").value(1));

        // 复验历史
        mockMvc.perform(get("/api/rework-sub-batches/" + subBatchNo + "/inspections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(true))
                .andExpect(jsonPath("$.results[0].version").value(1))
                .andExpect(jsonPath("$.results[1].version").value(2));
    }

    @Test
    void 查询不存在的资源返回404() throws Exception {
        mockMvc.perform(get("/api/dispositions/NOPE")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/rework-sub-batches/NOPE")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/non-conformances/NOPE/destinations")).andExpect(status().isNotFound());
    }
}
