# cc-rework-route

不合格品与返工过程管理服务：从不合格登记、处置决定拆分、原子确认，到返工工序、复验与重新入库的完整闭环。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring Data JPA + Bean Validation + WebMVC，H2 内存库）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 业务模型

| 实体 | 说明 |
| --- | --- |
| `ProductionBatch` | 生产批次，维护总数量与当前可用数量 |
| `NonConformingRecord` | 不合格记录，**必须关联生产批次**，单独记录受影响数量与已消费数量 |
| `DispositionOrder` | 处置决定，以业务号为幂等键，含返工/报废/让步三部分数量 |
| `DispositionLine` | 处置拆分行，每个非零去向一行 |
| `ReworkSubBatch` | 返工子批次，确认处置时原子生成，带指定工序与复验版本 |
| `OperationCompletion` | 返工工序完成记录，(子批次, 工序序号) 唯一 |
| `InspectionResult` | 复验结果，(子批次, 版本号) 唯一 |
| `ScrapRecord` | 报废记录，确认处置时原子生成 |
| `CorrectionRecord` | 纠正记录，只能对已确认处置追加 |
| `BatchGenealogyEdge` | 批次谱系边：REWORK / SCRAP / CONCESSION / REINTEGRATE |

## 主要业务规则

### 1. 不合格登记

- 不合格记录必须关联一个已存在的生产批次，并单独记录受影响数量。
- 登记时受影响数量立即从批次可用库存冻结，登记数量不得超过批次可用数量。
- 不合格记录状态：`OPEN` → `IN_REWORK`（有返工部分）→ `CLOSED`；无返工部分确认即 `CLOSED`。

### 2. 处置决定三向拆分

- 受影响数量拆分为 **返工 / 报废 / 让步接收** 三部分，三者均为非负数，
  **三部分之和必须与原记录受影响数量完全一致**，否则拒绝（HTTP 422）。
- 返工数量大于 0 时必须指定至少一道返工工序。
- 处置可以先提出（`PENDING`）再确认，也可以在提出时带 `confirm=true` 一步确认。

### 3. 原子确认与不重复消费

- 确认在**单一数据库事务**内完成：生成返工子批次、报废记录、让步数量回补可用库存、
  写谱系边、置不合格记录为已消费。任一数量或状态校验失败，整体回滚，**不留部分结果**。
- 确认前对不合格记录与批次加**行级写锁（悲观锁）**，并配合实体乐观版本号；
  两个并发处置不可能同时通过“数量未消费”检查——一个成功，另一个回滚。
- 已确认处置消费的数量等于原始受影响数量，**同一原始数量不能被两个处置重复消费**。

### 4. 返工工序顺序

- 返工子批次必须按指定工序序列**严格按顺序**完成：只能登记“下一道应完成工序”，
  跳序、重复、非序列内工序均拒绝。
- 并发完成工序时，子批次行锁串行化，(子批次, 工序序号) 唯一约束在数据库层兜底。
- 工序全部完成前不能提交复验、不能放行。

### 5. 复验版本与放行

- 复验结果按**单调递增版本号**登记（(子批次, 版本号) 唯一）。
- 提交时可带 `expectedVersion`（客户端持有的当前版本），过期版本提交返回 409，
  防止旧结果覆盖新结果。
- **只有最新版本复验合格才能放行**：旧版本曾合格、最新版本不合格时不得放行。
- 放行后返工数量重新并入原批次可用库存，生成 `REINTEGRATE` 谱系边；
  不合格记录下全部返工子批次放行后，记录闭环 `CLOSED`。放行本身幂等。

### 6. 业务号幂等与不可变历史

- 处置业务号全局唯一。相同业务号 + **相同内容**（三部分数量、工序序列）重放：
  返回原处置（响应中 `replayed=true`），不重复生成任何结果；数量等值的不同书写形式
  （如 `4` 与 `4.00`）视为相同内容。
- 相同业务号 + **不同内容**：返回冲突 HTTP 409。
- **已确认的处置不能修改**：数量、状态、去向都不可变；只能通过
  `POST /api/dispositions/{businessNo}/corrections` **追加纠正记录**，追加式保留、完整可查。

### 7. 库存数量口径

批次可用数量 = 总数量 − 未决不合格冻结数量 − 报废数量；让步数量在确认时回补，
返工数量复验合格放行时回补。

## HTTP 接口

| 方法与路径 | 说明 |
| --- | --- |
| `POST /api/batches` | 创建生产批次 |
| `GET  /api/batches/{batchNo}` | 查询批次（含可用数量） |
| `POST /api/non-conformances` | 登记不合格记录（关联批次、受影响数量） |
| `GET  /api/non-conformances/{ncNo}` | 查询不合格记录 |
| `GET  /api/batches/{batchNo}/non-conformances` | 批次下不合格记录 |
| `POST /api/dispositions` | 提出处置决定（业务号幂等；`confirm=true` 同时确认） |
| `POST /api/dispositions/{businessNo}/confirm` | 确认处置（原子落账） |
| `GET  /api/dispositions/{businessNo}` | 处置明细（含拆分、纠正记录） |
| `POST /api/dispositions/{businessNo}/corrections` | 对已确认处置追加纠正记录 |
| `POST /api/rework-sub-batches/{subBatchNo}/operations` | 完成下一道返工工序 |
| `POST /api/rework-sub-batches/{subBatchNo}/inspections` | 提交复验结果（版本号） |
| `POST /api/rework-sub-batches/{subBatchNo}/release` | 复验合格放行，重新并入库存 |
| `GET  /api/rework-sub-batches/{subBatchNo}` | 返工进度 |
| `GET  /api/rework-sub-batches/{subBatchNo}/inspections` | 复验结果历史 |
| `GET  /api/non-conformances/{ncNo}/destinations` | 不合格数量去向 |
| `GET  /api/non-conformances/{ncNo}/rework-progress` | 返工进度汇总 |
| `GET  /api/non-conformances/{ncNo}/genealogy` | 批次谱系 |

错误状态码：`400` 参数校验失败、`404` 资源不存在、`409` 幂等冲突/并发版本冲突、
`422` 业务规则失败（数量不平、顺序违反、状态非法等）。

### 典型闭环示例

```bash
# 1. 批次与不合格登记
curl -XPOST localhost:8080/api/batches -H 'Content-Type: application/json' \
  -d '{"batchNo":"B-1","materialCode":"MAT-A","totalQuantity":100}'
curl -XPOST localhost:8080/api/non-conformances -H 'Content-Type: application/json' \
  -d '{"ncNo":"NC-1","batchNo":"B-1","affectedQuantity":10,"defectDescription":"尺寸超差"}'

# 2. 处置决定：返工5 / 报废3 / 让步2，一步确认（业务号 D-1 幂等）
curl -XPOST localhost:8080/api/dispositions -H 'Content-Type: application/json' \
  -d '{"businessNo":"D-1","ncNo":"NC-1","reworkQuantity":5,"scrapQuantity":3,
       "concessionQuantity":2,"operations":["OP-A","OP-B"],"confirm":true}'

# 3. 返工工序按顺序完成
curl -XPOST localhost:8080/api/rework-sub-batches/RW-D-1/operations -H 'Content-Type: application/json' \
  -d '{"operationCode":"OP-A","operator":"w1"}'
curl -XPOST localhost:8080/api/rework-sub-batches/RW-D-1/operations -H 'Content-Type: application/json' \
  -d '{"operationCode":"OP-B","operator":"w1"}'

# 4. 复验（expectedVersion 为当前最新版本，首次为 0）
curl -XPOST localhost:8080/api/rework-sub-batches/RW-D-1/inspections -H 'Content-Type: application/json' \
  -d '{"verdict":"PASSED","expectedVersion":0,"inspector":"qa-1"}'

# 5. 放行，5 件重新并入可用库存（最终可用 100-3=97）
curl -XPOST localhost:8080/api/rework-sub-batches/RW-D-1/release

# 6. 查询：去向 / 谱系
curl localhost:8080/api/non-conformances/NC-1/destinations
curl localhost:8080/api/non-conformances/NC-1/genealogy
```

## 测试

`src/test/java` 下的自动化测试覆盖：

- 三部分数量之和校验、负数/缺工序校验；
- 原子确认生成子批次/报废/让步、失败回滚不留部分结果；
- 两个处置并发确认只有一个消费成功（真实线程池并发）；
- 业务号幂等：相同内容重放（含并发重放）、不同内容冲突、确认后不可改；
- 工序顺序（跳序/重复/非序列）、并发完成同一工序只生效一次；
- 复验版本递增、过期版本冲突、旧版本合格但新版本不合格不得放行、合格放行回补库存；
- 纠正记录追加、数量去向/返工进度/复验历史/批次谱系查询；
- REST 层完整闭环与 400/404/409/422 状态码。
