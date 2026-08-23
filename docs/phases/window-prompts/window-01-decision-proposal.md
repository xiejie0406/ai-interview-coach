# 窗口 01 提示词：Gate A/Gate C 决策提案

```text
你负责 AI Interview Coach 的 Gate A/Gate C 决策收敛提案，只写文档，不替用户作决定。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

必须完整读取：项目/用户 AGENTS.md、docs/specs/README.md、docs/product/prd.md、docs/product/research.md、docs/review/canonical-product-recovery-draft.md、docs/decisions/decision-register.md、docs/phases/implementation-readiness.md，以及 product-spec、feature-spec、agent-governance、documentation-spec。

独占输出：
docs/review/gate-a-resolution-proposal.md

目标：
- 把 CAND-DEC-PROD 与 DEC-002–032、DEC-059–068 对齐，消除重复问题。
- 给出“一键推荐包”和有实质差异的少量备选，说明对 P0、页面、数据、架构、成本、隐私和18期顺序的影响。
- 单列 PRD/技术架构截断恢复的三个选项：找回原文、用户授权按候选重建、继续阻塞；推荐找回原文。
- 生成用户可直接回复的决定文本，但所有状态保持 Pending，不能回写 Accepted。
- 列出决定后需要由单一 owner 回写的 PRD/架构/决策段落。

禁止修改：docs/product/prd.md、docs/architecture/technical-architecture.md、docs/decisions/decision-register.md、docs/phases/**、任何源码。

禁止猜供应商、框架版本、支付渠道、删除 SLA；这些保持 Gate B。

完成时交接：实际文件、建议用户回复文本、仍未关闭的决定、与现有路线的冲突。不得执行构建、测试、外部调用或 Git。
```
