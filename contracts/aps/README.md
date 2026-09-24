# APS v1 机器契约

> 契约状态：IMP-07 已实现基线，IMP-08 扩展中；版本：1.0；最后更新：2026-09-13  
> 实现状态：资源日历、产品路线/订单、输入校验、计划请求/状态/取消/SSE、基础 CP-SAT、数量/物料释放、人工固定共享批、受控可中断 RUN 阶段、可信基线、稳定重排、三级锁、计划版本详情/比较、候选锁管理、版本化调整及系统内原子发布已实现；外部 MES 下达不在 P0。

本目录是生产排程 API、独立校验器和求解 Worker 的共享机器契约。Java DTO、前端类型和求解器适配器应从这里消费版本化结构，不得各自维护含义不同的副本。

## 1. 文件

| 文件 | 用途 |
| --- | --- |
| `openapi/aps-api-v1.yaml` | 已实现健康、能力、资源日历、产品路线、订单任务网络、输入校验、计划请求创建（含固定共享批和基线请求）/状态/取消/SSE，以及计划版本 M19～M24 详情、基线比较、候选锁增删、版本化调整和系统内发布；外部下达边界保留到 P1 |
| `schemas/solver-input-v1.schema.json` | 不可变求解输入快照 |
| `schemas/solver-result-v1.schema.json` | 求解原生状态、业务结果种类和候选计划 |
| `schemas/validation-result-v1.schema.json` | 独立校验结果与发布门禁 |
| `schemas/problem-v1.schema.json` | 可定位的业务问题；不承载内部异常堆栈 |
| `examples/golden/` | 最小合法、结构非法和业务不支持样例 |

## 2. v1 不变量

1. 每个顶层文档都携带 `schemaVersion: "1.0"` 和固定 `contractType`；破坏兼容的字段、枚举或语义变化必须发布新主版本文件。
2. 标识符使用 RFC 4122 UUID 字符串。`requestId` 是一次幂等计划请求的稳定身份，不是 Worker attempt 或租约身份。
3. 绝对时间必须是 UTC、毫秒精度的 RFC 3339 字符串，例如 `2026-09-15T01:00:00.000Z`。所有区间统一为半开区间 `[startAt,endAt)`；Schema 只验证形状，`endAt > startAt` 及跨对象包含关系由确定性校验器验证。
4. `definitionRevision` 与 `executionRevision` 分别冻结主数据/规则水位和现场执行水位。`inputHash` 是输入快照的 SHA-256 小写十六进制摘要；哈希生成、持久化和发布复验必须使用同一个规范化实现。
5. `horizon.detailEndAt` 之前属于 `MANDATORY_DETAIL`，之后允许 `FUTURE_CARRY_FORWARD`。`baseVersion` 为 `null` 表示没有正式基线；它不是当前正式版本指针。
6. `planStatus` 只使用 M19 已批准的状态；`solverStatus` 保存求解器原生状态；`resultKind` 保存业务解释；`reasonCode` 只描述校验或冲突原因。四者不得互相替代。
7. v1 允许 `SAME_START` 作为来源依赖进入和留存在 `SolverInput`，但确定性校验必须返回 `validationStatus=FAIL`、`publishable=false` 和 `reasonCode=UNSUPPORTED_SYNC_RULE`。不得丢弃、改写为完成依赖或送入求解器。
8. 当前拓扑严格为单 Worker。契约没有 lease、heartbeat、attempt fencing、Inbox、Outbox 或外部下达 ACK 字段；这些能力进入范围时必须先做新契约和数据模型评审。
9. `SolverResult` 中 `planStatus=FEASIBLE` 不等于求解器证明最优。只有 `solverStatus=OPTIMAL`、未放宽 gap 且 objective 与 bound 一致时，展示层才可按设计口径描述为当前模型内精确最优。
10. Schema 通过仅表示结构合法。候选资源、日历相交、数量守恒、资源重叠、修订过期及发布资格必须由独立业务校验器判断。
11. 对应 MySQL `DECIMAL` 的数量、容量和单位工时均使用十进制定点字符串（例如 `"10"`、`"0.125000"`），避免 JavaScript 二进制浮点和 JCS 哈希破坏精度；消费者必须使用 `BigDecimal` 或等价十进制类型，不能先转成 `Number`。
12. `validationStatus=PASS` 只说明当前校验范围通过。`INPUT` 和 `PLAN_CANDIDATE` 即使 PASS，`publishable` 也必须为 `false`；只有带候选摘要的 `PUBLISH_PRECHECK + PASS` 才允许 `publishable=true`。
13. 开放实际占用用互斥形态表达：`TRUSTED` 必须给出当前阶段、可信 `releaseAt`，并以剩余时长或剩余数量二选一形成 carry；`UNKNOWN` 不得猜剩余量或释放时间，必须由编译器保守阻塞到 horizon 结束。
14. 技能不是字符串标签：每条资源技能都携带 1～10 级等级、状态和半开有效期，候选资格必须在计划执行时点复验；`minimumSkillLevel` 不得脱离技能等级单独解释。
15. `QUANTITY` 依赖以 `thresholdQty` 或 `thresholdRatio` 二选一确定首个释放门槛，`transferBatchQty` 可与其中任意一种同时存在；`consumesOutput` 决定是否消耗有限供给。转移批不是另一种依赖类型。
16. `materialSupplies`、`materialDemands` 和 `sharedBatchCandidates` 是快照内的有限供需及有限候选，不是库存台账或任意子集枚举。共享批至少有两个成员；当前 P0 固定批成员必须完整覆盖各自任务量，成员数量合计、单位、兼容键、固定周期和容量由编译器与确定性校验器复核。
17. 受控可中断只作用于获批工序的 `RUN` 阶段：`maxSegments` 限制物理段数，`minSegmentSeconds` 限制扣除后续 `resumeSetupSeconds` 后的生产时长，`segmentResourcePolicy` 决定跨段固定资源或允许重选；`holdOnPause` 只允许 `SAME_RESOURCES`，并要求独占资源在首段开始到末段结束期间持续保留。当前未经建模的组合必须失败关闭。
18. 客户端的基线请求只携带计划版本、预期输入 hash 和冻结边界；基线作业必须由服务端从当前 `PUBLISHED + is_current=1` 的 M19～M23 编译，锁从 M24 编译。冻结边界前启动的作业按成员集合映射并精确保持起止时间与资源集合；非冻结作业的时间/资源扰动是低于总延期的次级目标。旧 hash、非当前版本、缺失作业或部分共享作业必须失败关闭。
19. `JOB`、`SEGMENT`、`ALLOCATION` 的 `TIME`、`RESOURCE`、`FULL` 锁必须保持目标粒度；当前正式版本的 M24 锁由服务端按成员、阶段、分段、需求、席位和原资源映射。数量释放投影段使用与求解一致的转移批里程碑偏移；目标不存在、时间不在网格、资源不可用或目标不能稳定映射时返回 `LOCK_CONFLICT`，不得静默忽略、自动解锁或提升为整作业锁。

## 3. 哈希口径

IMP-01 冻结字段和算法标识，IMP-05 已加入 Java 规范化实现：

- `hashAlgorithm` 固定为 `SHA-256`；
- `canonicalization` 固定为 `JCS-RFC8785`；
- 计算 `inputHash` 时，从完整 `SolverInput` 顶层移除 `inputHash` 后按 RFC 8785 规范化，再对 UTF-8 字节求 SHA-256；
- `candidateHash` 只覆盖 `candidate` 对象；
- `tools/verify-jcs.mjs` 使用真实 ECMAScript 数字序列化、UTF-16 属性名排序和 Unicode 标量检查，验证指数、负零、Unicode、乱序键向量以及全部合法样例摘要；`aps-solver-contract` 使用固定版 `java-json-canonicalization`，并以相同向量和黄金输入摘要做 Java/Node 跨实现测试。

## 4. 兼容策略

本契约采用封闭世界策略，顶层和领域对象默认 `additionalProperties: false`。这是为了阻止字段拼错、消费者静默忽略新语义，以及求解输入在不同进程间产生不同 hash。

- IMP-01 结束并标记 v1 冻结后，`schemaVersion: "1.0"` 的字段、枚举、必填性和语义保持不可变。
- 新增可发送字段、枚举值或状态组合，即使字段看起来“可选”，旧 v1 消费者也会拒绝，因此不得复用 `1.0` 偷渡；必须发布新版本 Schema，并通过 capabilities/内容协商完成生产者后发、消费者先升。
- 只修正文案、增加不改变实例判定的注释或补充测试样例，可以保持同一机器版本，但仍要运行统一门禁。
- 放宽约束也可能使旧消费者拒绝新生产者输出，应按契约版本变化处理；不能只看新 Schema 是否向后接受旧实例。
- HTTP API 的顶层响应同样要求 `schemaVersion + contractType`。能力列表固定八种名称且每种恰好一次，调用方仍必须读取实际 `status`；当前 `RESOURCE_CALENDAR`、`ROUTING_ORDER` 与 `INPUT_VALIDATION` 为 `AVAILABLE`。

## 5. 黄金样例预期

| 样例 | Schema | 业务校验预期 |
| --- | --- | --- |
| `valid-minimal-solver-input.json` | 通过 SolverInput v1 | 可进入后续校验；不代表已有求解结果 |
| `valid-minimal-solver-result.json` | 通过 SolverResult v1 | `FEASIBLE / FEASIBLE / FEASIBLE` 三层状态一致 |
| `valid-material-quantity-shared-batch-input.json` | 通过 SolverInput v1 | 数量门槛与转移批并存，并携带供需和共享批候选 |
| `valid-baseline-replan-input.json` | 通过 SolverInput v1 | 当前正式基线、冻结边界及作业成员/时间/资源快照结构合法 |
| `valid-input-pass-validation-result.json` | 通过 ValidationResult v1 | 输入 PASS，但 `publishable=false` |
| `valid-publish-precheck-pass-validation-result.json` | 通过 ValidationResult v1 | 只有此范围的 PASS 才允许发布 |
| `valid-same-start-input.json` | 通过 SolverInput v1 | 必须阻断，不启动求解 |
| `valid-same-start-validation-result.json` | 通过 ValidationResult v1 | `UNSUPPORTED_SYNC_RULE` 且不可发布 |
| `invalid-missing-revisions-solver-input.json` | 必须失败 | 缺 `definitionRevision` 和 `executionRevision` |
| `invalid-trusted-carry-null-release-solver-input.json` | 必须失败 | `TRUSTED` 开放占用缺可信 `releaseAt` |
| `invalid-draft-optimal-solver-result.json` | 必须失败 | DRAFT 不允许伪造 OPTIMAL |
| `invalid-input-pass-publishable-validation-result.json` | 必须失败 | INPUT PASS 不能取得发布资格 |
| `invalid-capability-and-flow-solver-input.json` | 必须失败 | 同时覆盖技能缺等级、双阈值、任务供给缺来源和单成员共享批 |
| `invalid-solving-terminal-solver-result.json` | 必须失败 | SOLVING 不允许携带终态求解结果 |
| `invalid-conflict-empty-problems-solver-result.json` | 必须失败 | CONFLICT 必须至少有一个 ERROR 问题 |
| `invalid-feasible-error-problem-solver-result.json` | 必须失败 | 可审查或已发布候选不得携带 ERROR |

## 6. 统一契约门禁

精确 Python 依赖在 `requirements-contracts.txt`，Python/Node 精确版本在 `contract-test-manifest.json`。验证器只检查版本，不自动安装或修改环境；依赖安装应在获准的隔离环境或 CI 中显式执行。

在仓库根目录只需运行一个命令：

```powershell
python contracts/aps/tools/verify_contracts.py
```

统一入口实际执行：

- 校验清单中的 Python、Node 和四个契约依赖精确版本；
- 检查四个 JSON Schema 自身符合 Draft 2020-12；
- 要求 `examples/golden/*.json` 与 manifest 一一对应，禁止漏验新增样例；
- 验证全部合法样例通过、全部非法样例失败，并核对非法样例的预期错误路径；
- 从文件 URI 校验 OpenAPI 3.1 和 `../schemas/*.json` 相对引用；
- 复核 SolverResult 与 OpenAPI 的 M19 状态集合、已实现 HTTP 路径白名单、顶层 `contractType` 及 `SAME_START` 阻断链；
- 调用 Node JCS 验证器检查 RFC 8785 关键向量和全部声明的 input/candidate hash。

OpenAPI 必须从文件路径或 URI 验证，才能保留相对 `$ref` 基址。Java/TypeScript 代码生成器兼容仍需等生成方案确定后执行；Java RFC 8785 实现已与 Node 基准及四份合法输入、候选摘要交叉复验。
