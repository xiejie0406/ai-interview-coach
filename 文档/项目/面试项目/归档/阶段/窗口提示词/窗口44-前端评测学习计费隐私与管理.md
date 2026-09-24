# 窗口 44 提示词：Frontend Evaluation / Learning / Billing / Privacy / Admin

```text
你负责 Evaluation、Learning、Billing、Privacy、Admin 前端 feature 候选。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：41 已交接；33 授予 evaluation/**、learning/**、billing/**、privacy/**、admin/** 唯一 owner。完整读取冻结 contracts、窗口 27/30/32 与 37/38/39 handoff（只读已交接版本）。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；真实支付、删除、Admin sensitive access 均未启用。

只允许编辑上述五个 feature 目录。

必须完成：
1. Evaluation/Report 以 evaluationId/interviewId 的正式映射查询，SSE+REST snapshot 恢复；展示分类 judgement/evidence/limitations，不恢复旧数值总分权威。
2. Feedback append-only；comment 仅当前表单内存；重复 key 显示首次结果。
3. Learning list/detail/dashboard 与 plan/item version command；stale、不可比、证据不足、candidate/confirmed/completed/cancelled 明确。
4. Billing 分开 entitlement/quota/reservation/settlement/release 与 provider cost；金额按 currencyExponent；Order 501 时不显示已支付或伪渠道。
5. Privacy 先显示 policies/consent/inventory；Export/Deletion 只有 preflight challenge、step-up、blocker 和 use case 全部可用才可提交，否则禁用并解释。不得在前端模拟删除完成。
6. Admin 只渲染固定脱敏 projection；角色来自服务端；未知字段不枚举，敏感访问超时后清内存。

禁止编辑 app/shared/其他 feature/backend/contracts/config/dependency/测试。
检查页面状态、SSE 410、ETag、金额、挑战/拒绝/取消、敏感缓存；无 fake success/PaiCLI。
停止条件同统一规则。
交接：文件、页面/API/状态矩阵、禁用能力、46 请求、NotRun。
不得修改依赖、安装、构建/测试、浏览器、支付/删除/外部调用、部署或 Git。
```
