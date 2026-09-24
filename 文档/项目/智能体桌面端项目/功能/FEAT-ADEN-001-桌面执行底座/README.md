# Aden 桌面执行底座：控制页

> 文档类型：Feature 控制页；版本：0.13.0；文档状态：Approved  
> Feature：FEAT-ADEN-001；当前切片风险：L2；完整 Aden 产品风险：L3  
> 创建：2026-09-12；更新：2026-09-13  
> 当前标记：Stage 8 审查验证 / Completed；IMP-01～10 Completed；Stage 9 用户验收 NotStarted  
> owner：用户负责产品方向与高风险授权；Codex 负责方案、实现与技术验证；独立安全复核人待指定  
> 批准记录：2026-09-11 用户明确要求 Aden 后端采用 RuoYi；2026-09-12 用户要求先完成 Python Agent、智能体桌面端和 RuoYi 后端三份详细架构文档及十阶段实施文档；同日用户明确要求“收敛下，分这 10 个阶段进行开工，进入编码实现”。十阶段执行包和其中列明的安全本地源码、依赖、构建与验证范围已获批准；真实账号 / 数据、真实 Provider、桌面控制、外部发送、生产数据库、部署、发布与 Git 写操作仍未授权。

## 1. 目标与边界

本 Feature 只交付 Aden 的第一条可验证纵向切片：以 RuoYi 作为唯一中心后端，建立 Electron/Vue 桌面操作壳和确定性任务状态内核，随后用 Runner 模拟器打通“创建任务 → 领取 → 心跳 → 回执 → 状态展示”。本切片全部使用合成数据，不读取微信，不访问真实电商网站或 ERP，不联系供应商，也不执行任何真实外部写动作。

完整产品仍由 CORE 共用底座、WX 个人微信客服、PUR 采购询价、COL 电商信息采集三个能力包组成。当前 Feature 只拥有 CORE 合成闭环的实现状态；三项业务能力的 REQ / BR / AC 继续由[项目 PRD](../../产品/产品需求文档.md)维护，不能从本控制页推断已批准或已实现。

## 2. 唯一事实入口

| 事实 | 入口 | 当前状态 |
| --- | --- | --- |
| 本切片 REQ / BR / AC 与非目标 | [功能规格.md](功能规格.md) | Approved；当前只覆盖合成 CORE |
| 跨进程架构、工程数量和总体顺序 | [技术设计.md](技术设计.md) | Approved；目标为 4 个实现单元 + 1 个契约目录；`ruoyi-aden` 随 `ruoyi-admin` 运行 |
| Python Agent / Runner 内部方案 | [Python Agent架构与实现方案.md](Python%20Agent架构与实现方案.md) | Approved；当前只启用自有端口 + Fake；真实 Provider 未选择、未调用 |
| Electron 桌面端内部方案 | [桌面端架构与实现方案.md](桌面端架构与实现方案.md) | Approved；1 个 npm 工程、3 个信任层、9 个逻辑模块 |
| RuoYi 后端内部方案 | [RuoYi后端架构与实现方案.md](RuoYi后端架构与实现方案.md) | Approved；1 个 Maven 模块、6 个逻辑领域、当前闭环先实现 4 个 |
| 技术路线决定与退出条件 | [ADR-ADEN-001 工程与安全边界决策](../../决策/ADR-ADEN-001-工程与安全边界决策.md) | Approved；记录 5 项需要独立生命周期的决定 |
| 唯一实施任务与阶段门 | [任务清单.md](任务清单.md) | Approved；IMP-01～10 Completed |
| 已运行命令、结果与限制 | [验证记录.md](验证记录.md) | 技术验证 Completed；用户验收不在该文档代签 |
| 三模块产品范围 | [Aden 项目控制页](../../README.md) | Approved 文档基线 / 完整产品仍为 L3 |
| 当前源码入口 | [桌面工程](../../../../../aden-desktop/README.md)、[RuoYi Aden 模块](../../../../../platform-backend/ruoyi-aden/pom.xml) | 当前 L2 合成 CORE 已实现并通过技术验证；不代表真实连接器或生产能力 |

## 3. 当前真实状态

| 组成 | 实现事实 | 验证事实 | 下一门 |
| --- | --- | --- | --- |
| `platform-backend/ruoyi-aden` | Workspace bootstrap、Task REST / ETag、Runner / Capability / Audit 投影、Outbox publisher、可恢复 SSE 与调度均已完成 | 554 个无数据库 Java 测试、20 个本机 MySQL 8.0.46 测试及 18 模块干净聚合构建通过，详见[验证记录](验证记录.md) | Stage 9 合成 UAT |
| `aden-desktop` | Electron main / preload 安全传输与 renderer 登录、Workspace、共享 Task Center、详情、能力总览已完成 | 12 个文件 / 55 个 Vitest、TypeScript strict、CJS preload bundle、实窗 smoke 和真实纵向 E2E 通过 | Stage 9 人工 captcha-enabled / 交互验收 |
| `aden-agent-runtime` | IMP-07 已完成九包离线 Fake 候选链、三类产品 Family 禁用注册、Scope / 引用 / 敏感字段策略与取消 / 失租 / fence 门禁 | pytest 31/31、Ruff、严格 Pyright、确定性 CLI 和 0.2.0 wheel / sdist 构建通过 | 当前保持 provider-disabled；真实 Agent 服务端链另开 Feature |
| `aden-runner` | 六包低权限 simulator 已完成 canonical Session / claim / split heartbeat / receipt、确定性故障点及独立进程 E2E | pytest 28/28、Ruff 全通过、Pyright 0 error / 0 warning | 真实 Runner 能力另开 L3 Feature |
| `contracts/aden` | current OpenAPI / JSON Schema 是三端唯一边界，Java、Python 与 TypeScript 已消费；TypeScript 类型可重复生成 | 4 Schema、2 OpenAPI、26 正例 / 9 反例、三语言探针与生成漂移门通过 | 后续版本继续做兼容审查，不反向漂移 |
| MySQL `aden_*` | 12 张业务表及专属 history migration 已实现；普通启动默认不迁移 | 隔离 MySQL 8.0.46 完成 baseline 0、V1～V4、重复迁移、校验与保护测试；临时实例已清理 | 后续阶段继续在隔离实例验证持久化，不写现有业务库 |
| API / SSE | Workspace、Operator Task、Runner v1、投影和 SSE 已实现；Electron main 已实现安全消费、恢复与 ack 背压 | 契约、权限、错误、游标、Outbox、慢消费者、桌面 transport、真实 MySQL 投影与跨进程 E2E 通过 | 多实例 fan-out 不在本 Feature |
| WX / PUR / COL 真实连接器 | 未纳入本切片 | NotRun | 分别通过 G0/G1 后另开执行包 |

本轮已经联启 RuoYi、Redis、MySQL、Python Runner 与 Electron，并完成本地合成成功/取消纵向 E2E；运行时按 PID + TCP listener 核验三项服务均仅监听随机 loopback 端口，结束后进程与一次性目录已清理，`MySQL80` 保持停止。该结论不包含人工 captcha-enabled UAT、实机安装、签名、升级、真实账号、UIA 或任何外部连接器验证。

## 4. 当前决定与限制

- RuoYi / Spring Boot 是 Aden 唯一服务端控制面；不得另建 FastAPI 业务后端或让 Python 形成第二套任务、审批和审计真相。
- Aden 作为仓库内独立业务项目，业务事实默认进入 MySQL `aden_*` 表；不与 AI Interview Coach 的 PostgreSQL 业务边界混用。
- Electron 是当前 PoC 的桌面壳选择，不是未经实证的永久锁定。Tauri 仅在包体、更新带宽、内存或安全基线出现量化不达标时用同一契约做对照。
- Python 拆为 `aden-agent-runtime` 和 `aden-runner` 两个独立工程；二者不共享机器凭据或发行包，不直连业务库、不持有操作员令牌、不自行推进业务终态。
- Java 只保留现有 1 个 `ruoyi-aden` Maven 模块；桌面端只保留现有 1 个 `aden-desktop` npm 工程，不按 Agent 名称拆微服务或平行前端。
- 第一主线为 CORE → COL 合成采集 → PUR 合成采购闭环；WX 保持独立 Experimental 轨，不能因其他模块通过而获得发送授权。
- Git、部署、发布、真实账号和外部调用均为 `NotRequested`。

## 5. 阶段出口

Stage 7 开发实现和 Stage 8 审查验证均已完成。[任务清单.md](任务清单.md)是唯一实施任务和授权事实源；`IMP-01`～`IMP-10` 均有实际产物与[验证记录](验证记录.md)。最终验证覆盖机器契约、MySQL / Java 控制面、Runner simulator、Python Agent provider-disabled Fake、Electron main / preload / renderer、故障与安全边界，以及真实本地合成闭环。缓存或历史产物没有被当作通过证据。

Stage 8 出口已满足：合成任务端到端可恢复；重复领取与回执不会重复推进；取消只在 Runner 安全点确认；Workspace 越权、断线恢复、静态与运行网络边界均有证据；P0 AC 的技术验证为 Pass。

下一门为 Stage 9：用户按合成场景验收桌面交互，其中真实 captcha-enabled 手工输入仍为 `NotRun`。用户验收决定为“待确认”；上线就绪因无部署目标为 `Skipped`；发布事实 `NotReleased`。
