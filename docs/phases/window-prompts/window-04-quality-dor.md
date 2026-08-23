# 窗口 04 提示词：DoR、验证和验收设计

```text
你负责 AI Interview Coach 的 DoR、验证与验收设计，只写计划，不创建或运行测试。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

完整读取：项目/用户 AGENTS.md、docs/specs/README.md、docs/product/prd.md、docs/phases/README.md、dependency-matrix.md、review-checklist.md、implementation-readiness.md、phase-01 至 phase-18，以及 agent-governance、quality-delivery-spec、development-spec、documentation-spec。

独占输出：
docs/phases/dor-and-verification-plan.md

必须包含：
- 进入 Phase 01 编码的 DoR 清单，明确当前每项 Pass/Blocked/NotRun；不能把“计划存在”记 Pass。
- REQ/AC → 未来 EV 类型矩阵，至少逐项覆盖当前 P0 AC-01–12。
- 测试分层：domain unit、module boundary、repository/Testcontainers、Provider contract、API/AsyncAPI、React、Playwright、Golden Set、安全/隐私、故障注入、性能。
- 每层建议文件范围、命令候选、环境影响、外部数据/费用、不能证明什么。
- UAT 场景：题库、文本面试、麦克风拒绝、ASR 修正、TTS失败降级、报告复练、额度、删除部分失败。
- 失败退回阶段、停止条件、证据位置和独立授权矩阵。

禁止：不新增测试代码，不执行命令，不操作浏览器/Provider/数据库，不写 Pass，不修改 PRD/架构/Phase/源码。

完成时交接实际文件、当前 DoR 阻断项、未来最小验证包和需用户另行授权的动作。
```
