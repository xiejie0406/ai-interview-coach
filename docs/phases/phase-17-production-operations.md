# 第 17 期：生产运维、监控、备份与回滚准备

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-17  
> 风险等级：L3  
> 产出/适用阶段：5–10 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：operations / platform / security / release  
> 证据结果：NotRun  
> 前置：PHASE-14–16；部署/云/监控/备份动作分别授权

## 1. 本期为什么存在

功能闭环和商业事实建立后，才把系统提升为生产候选：环境/Secret/网络、指标/告警、备份恢复、迁移、Flag、运行手册和回滚必须先可审核，不能在 Phase 18 临时补。

## 2. 用户可见目标

测试环境中可看到明确服务状态/维护/降级；运营能在不读取敏感正文的情况下定位故障、质量和成本，并按手册停止语音/支付/新模型。

## 3. 技术学习目标

学习容器化单体、dev/test/prod 隔离、OTel/Micrometer、SLI/SLO/alert、secret management、PITR/restore、migration/rollback 与 runbook。

## 4. 范围

容器/Worker profile、环境配置、TLS/同源、Secret、OTel/metrics/log redaction、dashboard/alert、health/readiness、备份/PITR/对象生命周期、恢复演练计划、迁移/Flag/冒烟/停止/回滚手册。

## 5. 非目标

不实际生产发布、不上 Kubernetes/微服务/跨区 active-active、不自动回滚未批准数据操作、不宣称 SLO 已达成。

## 6. 前置决策

DEC-051–057/064/065；首发云/地域、RPO/RTO、SLO、Exporter、日志/备份保留、域名/TLS、发布责任与紧急停止授权。

## 7. 前置期次和依赖

14 resilience/flags；15 privacy/audit；16 payment/cost。应覆盖所有 module/Provider/数据 owner，不新增业务功能。

## 8. 涉及的 REQ/BR/AC/DES

横切 REQ-05/09/11/14/15；AC-06/09/10/14；DES-DEPLOY/OBS/BACKUP/ROLLBACK/SEC。

## 9. 本期完整功能点

artifact/profile；环境/Secret；health/readiness；业务/质量/可靠性/成本/安全指标；告警/值守；Flag runbook；DB backup/PITR；对象生命周期；恢复演练计划；Flyway 发布顺序；冒烟/停止/回滚；公告。

## 10. 正常流程

候选 artifact→test 配置/迁移→readiness→受控启动→冒烟/指标观察→备份可定位→若停止信号触发则关闭 Flag/停止批次→按兼容方案回滚并回读。

## 11. 空态、错误、拒绝、取消和恢复

无数据 dashboard 说明采集未开始；缺 Secret/迁移失败拒绝启动；告警配置错误阻断；用户取消部署准备不影响运行系统；备份恢复失败保持 NotReady；回滚不兼容 schema 时停止而非强降级。

## 12. 后端模块

boot config/profiles/actuator；adapters observability/redaction/health；operations application projections/announcements；业务 domain 不依赖监控 SDK。

## 13. 前端页面和组件

`/status`、`/admin/operations/overview|providers|quality|cost|jobs|flags`；`ServiceStatusBanner`、`MetricCard`、`AlertTimeline`、`FeatureFlagControl`、`MaintenanceNotice`。

## 14. 数据实体、约束和迁移

operations projection/announcement/alert acknowledgement 可持久化；指标时序进入批准监控后端，不进业务表；backup inventory/restore evidence 作为受控记录。迁移 expand/contract 和 app/schema compatibility matrix。

## 15. REST/SSE/WebSocket/API 或事件

public status、admin operations/flags/announcements；health/readiness 内外分层；指标 exporter；不为监控新增业务 WS；事件到 metrics/log/trace 只含脱敏 IDs。

## 16. Agent/Prompt/Provider

Provider/Prompt/Schema 质量和成本 dashboard；Flag 关闭/回退到上一批准版本；监控不采 Prompt/回答正文；自动路由仍受 14 的确定性策略。

## 17. 安全与隐私

环境/Secret 隔离、最小网络/DB 权限、TLS、精确 CORS、admin MFA/reason/audit、日志/trace 红线、备份加密/访问/保留、测试无生产数据。

## 18. 计划新增文件树

```text
deploy/{local,environments/{test,prod}}/
observability/{dashboards,alerts,collector}/
backend/interview-boot/src/main/java/.../boot/{ObservabilityConfiguration,HealthConfiguration}.java
backend/interview-adapters/.../observability/{metrics,tracing,redaction}/
frontend/src/features/admin/operations/; frontend/src/features/status/
contracts/openapi/{status,operations}.yaml
docs/operations/{configuration,monitoring,backup-restore,migration,runbook,rollback}.md
docs/releases/<candidate-id>/{smoke-plan,rollback-plan}.md
```

## 19. 计划修改文件树

```text
interview-boot/src/main/resources/application*.yaml
interview-boot/.../{ApiConfiguration,SecurityConfiguration,ProviderConfiguration,WorkerConfiguration}.java
frontend/src/app/router.tsx
各 module 的脱敏 metrics instrumentation
docs/reference/configuration.md
```

## 20. 后续 TASK 拆分建议

artifact/config、network/secret、OTel/metrics、dashboard/alerts、backup/restore、migration/rollback、status/admin UI、runbook/review；云资源/部署/恢复运行各独立授权。

## 21. 验证建议

配置/静态；集成：health/exporter/redaction；UI：status/admin；故障注入：alert/Flag；备份 restore rehearsal；security；performance baseline；UAT：维护/降级文案。计划存在不证明恢复成功。

## 22. 明确完成标准

候选环境/配置/Secret/迁移边界可审；核心 SLI/告警有 owner；敏感正文不采集；备份/恢复/停止/回滚步骤和完成信号清楚；实际演练结果按授权记录。

## 23. 本期不能证明什么

不能证明生产 SLO、实际发布、真实灾难恢复、跨区容灾、Kubernetes 必要性或所有安全风险关闭。

## 24. 风险与停止条件

监控采正文、test/prod 混 Secret/数据、备份未加密/无 restore、迁移不可回退且无补偿、Flag 可放宽安全或云/部署未授权时停止。

## 25. 下一期进入条件

锁定候选版本/差异/配置/数据；验证包、UAT 包和上线就绪范围可被独立批准。

## 26. 建议学习和复盘内容

复盘 observability pillars、SLI/SLO、alert fatigue、backup vs restore、expand-contract migration、release/rollback fact boundaries。
