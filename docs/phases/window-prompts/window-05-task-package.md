# 窗口 05 提示词：Feature 控制页、tasks.md 与执行包

```text
你负责把 AI Interview Coach Phase 01 转成正式 L3 Feature 任务包。此提示词现在是“待激活模板”。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

激活检查（全部满足才写文件）：
- PRD 与技术架构无截断。
- Gate A 已关闭，Gate C 已明确延后/排除。
- Canonical PRD、阶段 4 原型和技术设计均为 Approved。
- 用户明确要求创建 Phase 01 Feature 任务包。

任一项不满足：只读报告阻断，不创建 tasks.md，不创建源码。

激活后完整读取：项目/用户 AGENTS.md、项目规范索引、Approved PRD/原型/技术设计、docs/phases/phase-01-independent-foundation.md、parallel-development-plan.md、dor-and-verification-plan.md，以及 agent-governance、development-spec、quality-delivery-spec、documentation-spec。

Feature ID 不能自行猜。如果批准材料已有 Foundation Feature ID，使用它；否则先请求用户/产品 owner 决定。

独占输出：
docs/features/<APPROVED-FEAT-ID>-<slug>/

至少建立：README.md、feature-spec.md（只链接/提取批准范围，不复制整个产品 PRD）、design.md、tasks.md、verification.md 空的计划结构。L3 跳过任何产物需写理由。

tasks.md 必须：
- 使用稳定 TASK ID，覆盖根构建、五 module、Boot、前端壳、契约、Flyway、test-support、文档。
- 将共享脚手架与 4 个并行代码窗口拆成依赖波次。
- 为每个 TASK 写允许文件、禁止文件、验证建议、完成条件、停止/恢复。
- 列出精确命令候选、环境/端口/进程/资源影响、证据位置。
- 独立记录代码、测试代码、构建/测试/启动、外部调用、Git 的授权状态；不得自行设 Approved。

禁止创建源码、测试代码或执行命令。完成后交接任务图、共享文件 owner、并行窗口激活条件和需要用户批准的执行包。
```
