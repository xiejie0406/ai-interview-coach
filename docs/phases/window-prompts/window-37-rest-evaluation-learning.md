# 窗口 37 提示词：REST Evaluation / Report / Learning

```text
你负责 Evaluation、Report、Feedback、Learning REST 入站候选。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：34 已交接；33 授予 rest/evaluation、rest/learning 唯一 owner。完整读取用户/项目 AGENTS/specs、evaluation-report.yaml、learning.yaml、四个 Agent JSON Schema、application evaluation/learning 公共接口、窗口 27/30/32 交接。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；PaiCLI 独立。

只允许编辑对应 inbound/rest/evaluation/** 与 inbound/rest/learning/**。

operation owner：requestAnswerEvaluation、getEvaluation、submitEvaluationFeedback、getReport、getInterviewReport、getLearningDashboard、listLearningPlans、getLearningPlan、createLearningPlanCandidate、applyLearningPlanCommand、applyLearningItemCommand。

必须完成：
1. Evaluation async receipt 精确返回第一次 idempotency operation/status/stream path；Practice source variant 未批准时不能伪造 interviewId。
2. getEvaluation/report 使用 tenant+owner 404；getInterviewReport 必须由 owner-scoped interviewId 解析 report，不猜 ID。
3. Feedback 按 evaluationId append-only，comment 正文不进日志/事件；重复 key 精确回放。
4. Learning Candidate 固定 ReportVersion/config/Prompt/Schema/Provider route；plan/item command 强制 If-Match/expected version；stale 返回统一 conflict。
5. Dashboard/source/policy port 缺 adapter 时对应 operation 501，不直接查询其他域 Repository。

禁止改 common/security/SSE、其他域、domain/application/persistence/boot/contracts/frontend/POM/Migration/测试。
只读检查：11 operation 映射；owner/version/idempotency/async path；无数值评分旧模型、mock success、敏感日志、PaiCLI。
停止条件同统一规则。
交接：operation 表、文件、Report/Learning 映射、501 port 清单、窗口 39/47 Bean、NotRun。
不得新增测试、安装依赖、构建/测试/启动、Migration、外部调用、部署或 Git。
```
