# 窗口 45 提示词：Inbound Integration Reconciliation

```text
你负责窗口 34–40 的串行入站收口。所有前驱必须停止写入并释放 inbound；本窗口不补 application/domain/persistence。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：34–40 全部交接；33 授予 adapters/inbound/** 唯一 owner。完整读取所有交接、64 operation 清单、AsyncAPI、实际文件与最近时间戳。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun。

只允许编辑 backend/interview-adapters/src/main/java/com/aiinterviewcoach/adapters/inbound/**。

必须完成：
1. 合并公共 context/error/security 调用差异，消除重复 mapper、重复 route、package/FQN 冲突。
2. 64 OpenAPI operation 恰好一条 IMPLEMENTED/UNAVAILABLE 路由；health 不重复；不允许空成功。
3. REST/SSE/WS 重新鉴权、tenant/owner、CSRF、correlation、idempotency、ETag 和 ErrorEnvelope 一致。
4. SSE 三态 cursor、410 snapshot、question text enrichment、live unavailable；WS ticket/generation/sequence/backpressure/default-disabled 无冲突。
5. 生成窗口 47 精确 Bean 构造依赖清单；缺 port 保持 501/fail-closed。

禁止修改其他模块、contracts/frontend/boot/POM/Migration/测试。
静态检查：duplicate route/FQN/import、跨层/跨域 Repository、敏感日志、mock success、PaiCLI；逐 operation 输出结果。
停止条件：必须修改上游契约/用例、发现目录仍变化或产品语义不唯一时停止交给 33。
交接：修改文件、64 operation 矩阵、SSE/WS 矩阵、Boot Bean 图、NotRun。
不得新增测试、安装依赖、构建/测试/启动、Migration、外部调用、部署或 Git。
```
