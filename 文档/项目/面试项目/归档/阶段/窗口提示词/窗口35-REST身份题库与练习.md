# 窗口 35 提示词：REST Identity / Catalog / Practice

```text
你负责 Identity、Catalog、Practice 的 REST 入站候选，只映射冻结 OpenAPI 到现有 application use case。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：34 已交接公共映射；33 授予下列目录唯一 owner。完整读取用户/项目 AGENTS 与相关 specs、identity/catalog/practice OpenAPI、application 对应公共接口和最新 persistence 交接。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun；PaiCLI 独立。

只允许编辑：
- .../adapters/inbound/rest/identity/**
- .../adapters/inbound/rest/catalog/**
- .../adapters/inbound/rest/practice/**
其中 `...` 为 backend/interview-adapters/src/main/java/com/aiinterviewcoach。

operation owner：
- registerAccount、login、logout、getCurrentAccount、updateProfile。
- searchPublishedQuestions、getPublishedQuestion、listAdminQuestions、createQuestionDraft、getAdminQuestion、createQuestionVersion、createRubricVersion、applyQuestionWorkflowCommand。
- startPractice、listPracticeHistory、savePracticeDraft、submitPracticeAnswer。

必须完成：
1. 现有 use case 精确映射 request/context/result；Idempotency-Key、If-Match、cursor/limit、owner 404 和 ETag 不丢失。
2. 匿名 register 当前 pre-tenant idempotency/policy owner 未闭合时映射 fail-closed，不创建 public tenant；login proof/session 不进日志。
3. Catalog published 与 admin route 权限分离；没有 Admin guard/use case 的 operation 返回稳定 501，不能枚举或直接调 Repository。
4. Practice cursor 保持 opaque；draft/answer 原文只进 command，不进异常 metadata/日志；异步 evaluation receipt 只返回第一次幂等结果。
5. 缺 logout/get/update 或 catalog detail/version use case 时逐 operation 501，不跨 application 补代码。

禁止改 common/security、其他 rest 域、domain/application/persistence/boot/contracts/frontend/POM/Migration/测试。
只读检查：17 个 operation 每个恰有 IMPLEMENTED 或 UNAVAILABLE owner；无空成功、跨域 Repository、敏感日志、PaiCLI。
停止条件：公共 mapper 不足、契约与 DTO 冲突、需越界、目录占用或发现 secret 时停止交给 33。
交接：17 operation 表、修改文件、错误/幂等/ETag/cursor 映射、501 缺口、窗口 47 Bean、NotRun。
不得新增测试、安装依赖、构建/测试/启动、Migration、外部调用、部署或 Git。
```
