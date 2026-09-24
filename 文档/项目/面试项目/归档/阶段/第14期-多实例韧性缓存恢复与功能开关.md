# 第 14 期：多实例韧性、Redis、恢复与 Feature Flag

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-14  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：platform / operations / interview / frontend  
> 证据结果：NotRun  
> 前置：PHASE-08、09、13；多实例/SLO/Flag 决策批准

## 1. 本期为什么存在

T1 已有业务闭环，第 14 期才以真实证据深化多实例协调、限流、续传、故障注入和开关；避免 Redis/Kafka 在没有需求前成为业务事实依赖。

## 2. 用户可见目标

实例切换、Provider 故障、网络断开或语音关闭时，用户得到可解释状态、恢复/文本降级/稍后完成路径，已确认回答和额度事实不丢不重。

## 3. 技术学习目标

学习 Redis coordination、distributed rate limit/lock、resilience budget、circuit breaker、feature flag、chaos/fault injection、backpressure 和可观测恢复。

## 4. 范围

Redis 短期状态/限流/锁/SSE cursor、multi-instance session/worker、Provider timeout/retry/circuit breaker、Flag、事件重放窗口、恢复 API、backlog/成本熔断、故障面板基础。

## 5. 非目标

不把 Redis 变事实源、不上 Kafka/Kubernetes/微服务、不做无限重试、自动发布或生产环境实际部署。

## 6. 前置决策

DEC-025/051/055–058/062/064/065；SLO、限流、公平性、重试/成本预算、Flag owner/审计、Redis 失败模式。

## 7. 前置期次和依赖

08 Job/Outbox/idempotency；09 text；13 voice。所有增强应保持已有 REST/SSE/WS 和业务状态兼容。

## 8. 涉及的 REQ/BR/AC/DES

REQ-05/09/14/15；BR-09；AC-06/09/14；DES-RELIABILITY/REDIS/FLAG/RECOVERY。

## 9. 本期完整功能点

Redis adapter；分布式限流/短锁；SSE cursor/WS affinity 候选；worker 多实例 claim；Provider 熔断/降级；Flag 开关；恢复状态；Job backlog/重试风暴/成本告警；故障注入场景。

## 10. 正常流程

请求经 tenant/user 限流→PostgreSQL 写事实→Redis 协调→worker 任一实例 claim→Provider 调用受预算/熔断→事件续传→用户恢复；Flag 可关闭 voice/provider 而文本读取可用。

## 11. 空态、错误、拒绝、取消和恢复

Redis 不可用时采用批准 fail-open/closed（安全/额度必须 closed 或 DB fallback）；限流拒绝含 retry-after；Provider 熔断不循环；用户取消 Job；实例崩溃 lease 接管；重放超窗 GET snapshot。

## 12. 后端模块

application `RateLimitPort/DistributedLockPort/FeatureFlagPort/RecoveryService`；adapters Redis/resilience/operations projection；boot multi-profile/instance ID；既有 domain 不感知 Redis。

## 13. 前端页面和组件

全局 `ReconnectBanner/ServiceDegraded/RateLimitNotice/RecoveryCenter`；admin `/admin/operations/providers|jobs|flags` 最小只读/受控操作；query/hooks 不自行切业务状态。

## 14. 数据实体、约束和迁移

PostgreSQL `operations.feature_flag/flag_change_audit/provider_health_projection`；Redis 只存 TTL cursor/lock/rate buckets；Job/Outbox 仍 PostgreSQL。Flag version/owner/reason/environment/expiry。

## 15. REST/SSE/WebSocket/API 或事件

恢复/status、admin flags/providers/jobs；SSE/WS 重连/heartbeat/sequence 契约深化；事件 `feature.flag.changed/provider.circuit.*`；不改变业务 event schema 无版本升级。

## 16. Agent/Prompt/Provider

Provider Router 接健康/Flag/预算；主备最多批准切换次数；Prompt/schema 新版可 Flag；Agent 不决定重试/路由。

## 17. 安全与隐私

Redis 不存正文/凭据；Flag 不能关闭 auth/privacy/audit；admin 改 Flag 需 role/reason/audit；故障日志脱敏；限流维度不暴露用户身份。

## 18. 计划新增文件树

```text
interview-application/.../platform/{RateLimitPort,DistributedLockPort,FeatureFlagPort,RecoveryService}.java
interview-adapters/.../{redis,resilience,persistence/operations,inbound/rest/operations}/
interview-domain/.../operations/FeatureFlag.java
interview-boot/.../{RedisConfiguration,db/migration/V###__create_operations_flags.sql}
frontend/src/shared/recovery/; frontend/src/features/admin/operations/
contracts/openapi/{recovery,operations}.yaml
observability/dashboards/{jobs,providers}.json
```

## 19. 计划修改文件树

```text
interview-application/.../{platform/job,agent/ProviderRouter,interview/session,voice}/
interview-adapters/.../{inbound/sse,inbound/websocket}/
frontend/src/app/providers.tsx
contracts/asyncapi/{interview-events,voice-events,job-events}.yaml
```

## 20. 后续 TASK 拆分建议

Redis/TTL、multi-instance worker、rate/lock、resilience/route、Flag/admin、reconnect/recovery UI、metrics/alerts、fault injection；容量命令/资源单列授权。

## 21. 验证建议

单元：retry/Flag；集成：Redis loss/multi-worker；契约：resume/sequence；UI/Playwright：degraded/reconnect；Golden Set：Prompt Flag regression；故障注入/性能：核心；UAT：故障仍可继续。不能证明生产云。

## 22. 明确完成标准

Redis 丢失不丢业务事实；实例/worker 崩溃可恢复；重复不扣费/推进；熔断/Flag 有审计且保文本；关键故障有可观察下一步。

## 23. 本期不能证明什么

不能证明生产 SLA、极限容量、跨区域容灾、Kafka/Kubernetes 必要性或真实灾难恢复。

## 24. 风险与停止条件

Redis 成为唯一事实、Flag 可放宽安全、重试无界、主备循环、故障注入影响未授权环境或数据库 Job 未经证据被替换时停止。

## 25. 下一期进入条件

隐私删除/支付/运维可依赖稳定 Job、Flag、观测与多实例语义；高风险故障无未定义终态。

## 26. 建议学习和复盘内容

复盘 cache vs source of truth、distributed coordination、circuit breaker、bulkhead、fault injection、feature flag governance 与 SLO evidence。
