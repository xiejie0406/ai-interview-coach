# Aden 跨边界契约

> 契约版本：1.0.0  
> 状态：current Operator / Runner；experimental Agent provider-disabled  
> 唯一机器事实源：`schemas/**/*.schema.json`

本目录是 FEAT-ADEN-001 在 Java、Electron/TypeScript 与 Python 之间交换数据的唯一机器契约。`openapi/` 只定义 HTTP path、状态码、header、security 与 path/query parameter；所有 request/response/event body 都通过相对 `$ref` 指向 `schemas/`，不得在 OpenAPI 或消费者中再手写一套传输 DTO Schema。

## 目录

```text
contracts/aden/
├── configuration/current-v1.json              # 类型化外部配置键、生产默认值与故障测试值
├── dictionaries/current-v1.json               # 状态、主体、命令、权限、错误与兼容词典
├── openapi/
│   ├── operator-v1.openapi.json               # 当前操作员 API
│   └── runner-v1.openapi.json                 # 当前 Runner API
├── schemas/
│   ├── current/{common,operator,runner}.schema.json
│   └── experimental/agent-provider-disabled.schema.json
├── examples/
│   ├── current/{operator,runner}/
│   ├── experimental/
│   ├── invalid/
│   └── manifest.json
├── generation-policy.json
└── scripts/{validate_contracts,probe_consumers}.py
```

`current` 只发布 RuoYi Operator 与 Runner simulator 的合成 CORE 闭环。`AgentJob`、`RunSpec`、`ContextView`、`ToolManifest`、`Candidate` 仅位于 `experimental/agent-provider-disabled.schema.json`；当前 OpenAPI 没有 Agent Worker path、security scheme 或可调用 operation。

## 冻结语义

- 公开 `OperatorTaskCommand` 仅允许 `SUBMIT_FOR_VALIDATION` 与 `REQUEST_CANCEL`，分别要求 `aden:task:command` 与 `aden:task:cancel`。内部 Validator、Coordinator、Runner receipt adapter 命令不进入请求 Schema。
- 合法 `SUBMIT_FOR_VALIDATION` 会在同一事务提交 `DRAFT → VALIDATING → QUEUED/FAILED`。业务校验失败已经改变 Task，返回 HTTP 200、`FAILED` Task 与 ETag；HTTP 422 仅用于 Task 未改变的前置输入拒绝。
- Task/aggregate version、workspace event sequence、Runner/credential/Session epoch、fencing token、receipt sequence及公开 BIGINT id，在 wire 上都是 `0..9223372036854775807` 的 canonical 十进制字符串。禁止 JSON number、前导零、符号、小数、指数和溢出。
- `EventEnvelope` 必须包含 `workspaceId`；SSE `id` / `streamCursor`、ETag 都是不透明字符串，不得当作十进制版本解析。
- Bootstrap 是 Task 页、Runner 摘要、四项 CapabilityProjection 与 workspace-wide 水位的同一一致性快照；`streamFilter` 当前固定为 `workspace-all-v1`。
- 所有对象默认 `additionalProperties: false`；未知字段、未知枚举与未知 `schemaVersion` fail closed。

## 唯一校验命令

在仓库根目录执行：

```powershell
python contracts/aden/scripts/validate_contracts.py
python contracts/aden/scripts/probe_consumers.py
```

第一条命令会校验 JSON / JSON Schema 2020-12、递归 resolve 所有 `$ref`、在临时目录实际 bundle 两份 OpenAPI、检查 OpenAPI 没有重复手写 DTO，并让 manifest 中同一个 example 同时通过（或同时拒绝于）其直接 JSON Schema 与 OpenAPI operation body Schema。第二条命令用本机 Python/Pydantic、Node/TypeScript 和 JDK 对 64-bit wire scalar、公开命令枚举及未知值拒绝做兼容探针；缺少工具时明确失败，不降级成 Pass。

可使用 `--bundle-dir <目录>` 保留去引用后的诊断 bundle。bundle 只是诊断产物，不是新的契约事实源。

## 生成与升级规则

`generation-policy.json` 是消费者工作目录、唯一校验命令和生成策略的机器清单。Java 与两个 Python 消费者采用“canonical Schema 校验 + 显式边界 mapping”；Electron/TypeScript 额外把 current Schema 确定性生成到 `aden-desktop/src/shared/generated/`，并在 build 前检查漂移。仍然**禁止手工修改生成物**，并且任何落盘生成文件首行必须包含：

```text
GENERATED FROM contracts/aden — DO NOT EDIT
```

显式 mapping 只负责把领域对象映射为传输对象，不能复制 Schema、枚举或放宽边界。未来生成目录也不是 Schema owner。变更顺序固定为：修改 current Schema → 更新词典与正负 example → 运行两条门禁 → 更新 Java/TypeScript/Python 消费者 → 运行消费者 contract tests。current 的破坏性变化必须提高主版本并获重新批准；兼容新增提高次版本；文档或 example 修正提高补丁版本。experimental 不能被 current OpenAPI `$ref`。

Secret、Token、验证码、真实账号、真实域名与业务正文不得进入 example。当前 example 全部使用版本化合成 UUID、固定摘要和 `.invalid` 测试域名。

## 类型化运行配置

`configuration/current-v1.json` 是首版数值配置键的单一机器清单，覆盖 Runner Session TTL/heartbeat、Delivery lease、claim batch/capacity、SSE heartbeat/connection TTL/replay/queue/send timeout、Outbox claim/retry，以及幂等与事件保留期。每项都明确外部键、整数类型、单位、生产默认值、故障测试值和上下界；实现必须通过外部配置绑定，不能把这些值散落为业务常量。

`testValue` 只允许隔离测试 profile 显式选用，用于在秒级触发到期、重试、慢消费者和保留窗故障；普通启动仍使用 `productionDefault`。若修改值会改变安全或数据留存语义，需要重新审查决定，不能仅靠配置热改绕过。
