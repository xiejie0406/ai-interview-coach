# AI Interview Coach

> 项目状态：规划主阶段未批准 / Foundation 与第一波领域源码实现中 / 未验证
> 文档状态：Draft
> 风险等级：L3
> 当前主阶段：3 功能规格（WaitingForApproval）
> 创建时间：2026-08-02
> 更新时间：2026-08-02

面向 Java 开发者转型 AI 应用与 Agent 开发的独立商业化面试系统。

## 项目独立性

本项目与 PaiCLI 是两个完全独立的项目：

- 不依赖 PaiCLI 源码、Jar、Maven module、Runtime API、SQLite、配置、环境变量、前端、部署或 Git 历史。
- 不通过 Git submodule、软链接、共享数据库或进程调用建立耦合。
- 只借鉴 PaiCLI 在 Agent、RAG、Memory、Provider、HITL、审计、长上下文和流式事件方面的设计经验。
- 如未来需要复制任何代码，必须先完成许可证、来源、维护边界和安全评审；当前未授权也未发生代码复制。

## 文档入口

- [市场与方案调研](docs/product/research.md)
- [产品 PRD（全量功能发散稿）](docs/product/prd.md)
- [技术架构（Draft）](docs/architecture/technical-architecture.md)
- [决策登记表（待用户拍板）](docs/decisions/decision-register.md)
- [十八期实践路线（Draft）](docs/phases/README.md)
- [完整编码目标与并行工作流（Draft）](docs/phases/development-goal-control.md)
- [完整产品 Feature 控制页（Draft）](docs/features/FEAT-INTERVIEW-001-ai-interview-coach/README.md)
- [静态高保真交互原型 v1（Draft / Mock）](prototype/html-v1/README.md)
- [项目规范索引（Draft）](docs/specs/README.md)
- [开发环境准备（规划）](docs/setup/environment-setup.md)
- [Foundation Wave 01 开发记录](docs/development-records/2026-08-02-foundation-wave-01.md)

## 当前控制信息

| 工作流 | 阶段 | 状态 | 下一门 |
|---|---|---|---|
| PRD-SCOPE | 3 功能规格 | WaitingForApproval | 收敛 P0/P1/P2/OUT 并批准 |
| PROTOTYPE | 4 原型验证（并行 Draft） | InProgress | 已有 Mock 原型；补资料包并由用户评审 |
| DES-ARCH | 5 技术设计（并行 Draft） | InProgress | 修复截断并批准技术战略和具体契约 |
| TASK-PACK | 6 任务拆分（提前准备） | WaitingForApproval | 候选 tasks 已建；等待上游批准和执行包确认 |
| IMPLEMENTATION-FND | 7 开发实现（局部前置工作流） | InProgress | 完成互斥源码交接；Feature 主阶段不由局部代码推进 |

当前已有静态 HTML Mock 原型，以及独立 Maven/React 工程、公共契约、Boot 安全壳和第一波领域/应用源码。它们均为“实现中、未验证”：没有执行依赖安装、构建、测试、服务启动、数据库 migration、真实 Provider/语音/支付、部署或 Git。文件存在和原型外观都不等于生产能力、EV 或用户验收。
