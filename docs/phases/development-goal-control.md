# 完整编码目标控制页

> 文档类型：Phase / 目标控制  
> 文档状态：Draft  
> owner / 责任边界：主协调 Agent 维护目标、波次、阻断和实际证据；用户批准产品、执行、验证与发布边界  
> 创建时间：2026-08-02  
> 更新时间：2026-08-02  
> Roadmap ID：ROADMAP-INTERVIEW-001  
> 风险等级：L3  
> 当前主阶段：3 功能规格  
> 阶段状态：InProgress  
> 证据结果：NotRun  
> 发布事实：NotReleased

## 1. 最终目标

按批准后的 AI Interview Coach 产品边界完成独立项目的全部编码，包括工程地基、身份与 tenant、题库、练习、文本/级联语音面试、Agent、评测报告、学习闭环、权益支付、隐私审计、可靠性和运维准备。完成定义仍需实际审查、验证和用户验收证明，不能由代码文件数量推导。

## 2. 当前工作流

| 工作流 | owner | 局部阶段 | 状态 | 独占范围 | 下一门 |
|---|---|---|---|---|---|
| WF-PRODUCT-RECOVERY | 子 Agent product_recovery | 3 功能规格 | InProgress | `docs/review/canonical-product-recovery-draft.md` | 产品候选可供用户批准 |
| WF-PROTOTYPE | 子 Agent prototype_pack | 4 原型验证 | InProgress | `docs/prototypes/FEAT-INTERVIEW-001/**` | 状态矩阵完整 |
| WF-ARCH-CONTRACTS | 子 Agent architecture_contracts | 5 技术设计 | InProgress | `docs/architecture/implementation-contract-pack.md` | 数据/API/状态候选完整 |
| WF-EXECUTION-PACK | 主 Agent | 6 任务拆分准备 | InProgress | 控制页、Feature 包候选、公共边界 | 上游候选合并后形成执行基线 |
| WF-CODE-FND | 主 Agent + 并行子 Agent | 7 开发实现（局部前置） | InProgress | 根构建、contracts、domain/application、adapters/boot、frontend 的互斥范围 | 完成源码交接与只读集成审查；运行仍待授权 |

## 3. 当前阻断

- PRD 和技术架构各有字面截断，原文未找回。
- Gate A/C 尚未形成用户决定；本轮先生成推荐重建候选，不伪造 Accepted。
- 尚无 Approved 原型、技术设计、Feature `tasks.md`；当前代码是用户要求持续推进后的局部实现工作流，不推进 Feature 主阶段。
- 本地生产源码/配置修改已由用户的“编码全部完成”目标激活；测试代码、构建、测试、启动、真实 Provider、支付、部署和 Git 仍未独立授权。

## 4. 波次

1. 产品恢复、探索原型、技术契约并行。
2. 主协调 Agent 合并候选，形成最小用户决策与 Feature 执行包。
3. 共享脚手架串行；随后按 `domain/application`、`adapters/boot`、`frontend`、`contracts/test-support` 并行编码。
4. 按依赖矩阵逐期推进 T0、T1、T2；每波完成后只读集成审查。
5. 获授权后执行构建/测试/真实链路/UAT，完成逐要求审计。

## 5. 禁止推导

- 子 Agent 文档完成不等于 Gate Accepted。
- Draft 设计不等于允许编码。
- 编码完成不等于验证、验收、ReleaseReady 或 Released。
