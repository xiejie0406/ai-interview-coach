# 窗口 26 提示词：Integration Static Review

```text
你负责 AI Interview Coach Wave 3 集成静态审查，不写生产代码。

项目目录：
D:\2025Ai\26-05-23\ai-interview-coach

只允许：
- 读取文件。
- 可更新 docs/development-records/ 下新的审查记录。
- 如发现必须修正文档索引或提示词错别字，可只改 docs/phases/window-prompts/**。

禁止：
- 修改 backend/frontend/contracts/POM/测试代码。
- 运行 Maven/npm 构建、测试、服务、迁移。
- 调用外部 Provider、ASR/TTS、支付、对象存储。
- 执行 Git add/commit/push/PR。

审查目标：
1. Java package/path 一致。
2. Java import 可定位。
3. domain 不依赖 Spring/JPA/HTTP/adapters/boot。
4. application 不依赖 adapters/boot。
5. adapters 不被 domain/application 反向依赖。
6. frontend 相对 import 可定位。
7. contracts 与前端 API facade 的明显不一致列表。
8. OpenAPI/AsyncAPI 裂缝列表：Report ID、Learning plan 恢复、Voice ACK/flow-control/cancel、Billing minor unit、Export status、Admin Audit、message/userMessage、ETag/version。
9. tenant/owner/version/idempotency/outbox/job/reservation/artifact lifecycle 是否有 owner。
10. 敏感内容 toString/log 检查：answer、transcript、prompt、schema output、model message、socketTicket、object key、payment payload。
11. PaiCLI 引用检查。
12. InterviewSnapshot 与 OpenAPI 全字段映射，尤其 planVersion/hash、pendingJobIds、reservation、report、voiceSummary、streamCursor 与恢复失败字段。
13. ConfirmTranscript 是否在同一本地事务中形成 AnswerVersion、next-step Job/Outbox 和幂等结果，是否仍存在 Transcript-only 部分成功路径。
14. Session Filter、tenant/principal、CSRF、admin permission 与 Controller/use-case Bean 装配是否闭合；默认配置是否误启用 Provider/Worker 或继续 deny-all/permit-all。
15. 哪些事项必须在测试授权后才可证明。

输出：
- docs/development-records/YYYY-MM-DD-wave-03-static-review.md
- 记录只读命令和结果；所有运行证据仍写 NotRun。
- 给出下一轮 3-5 个最小修复窗口建议。
```
