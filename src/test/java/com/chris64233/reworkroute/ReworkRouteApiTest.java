package com.chris64233.reworkroute;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 端到端 HTTP 闭环：开立 -> 处置拆分 -> 返工工序 -> 复验 -> 并回 -> 谱系查询。 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(DatabaseCleaner.class)
class ReworkRouteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullClosedLoopOverHttp() throws Exception {
        String batchNo = "B-HTTP-1";
        String ncNo = "NC-HTTP-1";

        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"batchNo":"%s","productCode":"P","initialQuantity":100}
                                """.formatted(batchNo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableQuantity").value(100));

        mockMvc.perform(post("/api/nonconformances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ncNo":"%s","batchNo":"%s","defectCode":"D1","affectedQuantity":30}
                                """.formatted(ncNo, batchNo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        // 拆分和不等于受影响数量 -> 400。
        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNo":"DSP-HTTP-1","ncNo":"%s","reworkQty":12,"scrapQty":8,
                                 "concessionQty":9,"requiredOperations":["FIX"]}
                                """.formatted(ncNo)))
                .andExpect(status().isBadRequest());

        // 12 + 8 + 10 = 30 确认成功，从响应中取出原子生成的子批次号。
        MvcResult confirmed = mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNo":"DSP-HTTP-1","ncNo":"%s","reworkQty":12,"scrapQty":8,
                                 "concessionQty":10,"requiredOperations":["FIX"]}
                                """.formatted(ncNo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.reworkSubBatchNo").isNotEmpty())
                .andReturn();
        String subBatchNo = com.jayway.jsonpath.JsonPath
                .read(confirmed.getResponse().getContentAsString(), "$.reworkSubBatchNo");

        // 幂等重放。
        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNo":"DSP-HTTP-1","ncNo":"%s","reworkQty":12,"scrapQty":8,
                                 "concessionQty":10,"requiredOperations":["FIX"]}
                                """.formatted(ncNo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(true));

        // 同业务号不同内容 -> 409。
        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNo":"DSP-HTTP-1","ncNo":"%s","reworkQty":22,"scrapQty":8,
                                 "concessionQty":0,"requiredOperations":["FIX"]}
                                """.formatted(ncNo)))
                .andExpect(status().isConflict());

        // 工序未完成即复验 -> 409；完成工序后复验合格并并回。
        mockMvc.perform(post("/api/rework/reinspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reinspectionBody(subBatchNo)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/rework/operations/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subBatchNo":"%s","operationCode":"FIX"}""".formatted(subBatchNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/api/rework/reinspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reinspectionBody(subBatchNo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultVersion").value(1));

        // 旧版本（v0/不存在）不放行。
        mockMvc.perform(post("/api/rework/merge-back")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subBatchNo":"%s","expectedVersion":0}""".formatted(subBatchNo)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/rework/merge-back")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subBatchNo":"%s","expectedVersion":1}""".formatted(subBatchNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MERGED"));

        mockMvc.perform(post("/api/dispositions/DSP-HTTP-1/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionCode":"CAUSE","detail":"root cause","operator":"qa"}"""))
                .andExpect(status().isCreated());

        MvcResult genealogy = mockMvc.perform(get("/api/batches/{batchNo}/genealogy", batchNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(92))
                .andExpect(jsonPath("$.nonconformances[0].rework.status").value("MERGED"))
                .andExpect(jsonPath("$.nonconformances[0].rework.reinspections[0].version")
                        .value(1))
                .andExpect(jsonPath("$.nonconformances[0].corrections[0].actionCode").value("CAUSE"))
                .andReturn();

        mockMvc.perform(get("/api/nonconformances/{ncNo}/quantity-trace", ncNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reworkMergedQty").value(12))
                .andExpect(jsonPath("$.scrapQty").value(8))
                .andExpect(jsonPath("$.concessionQty").value(10));

        mockMvc.perform(get("/api/rework/{subBatchNo}/progress", subBatchNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedOperations[0]").value("FIX"));

        // 不存在资源 -> 404。
        mockMvc.perform(get("/api/batches/NO-SUCH-BATCH/genealogy"))
                .andExpect(status().isNotFound());
    }

    private static String reinspectionBody(String subBatchNo) {
        return """
                {"subBatchNo":"%s","decision":"PASS","inspector":"qa"}""".formatted(subBatchNo);
    }
}
