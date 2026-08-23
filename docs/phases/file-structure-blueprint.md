# 计划文件结构蓝图

> 文档类型：设计蓝图  
> 文档状态：Draft  
> owner / 责任边界：架构 owner 维护计划结构；本文不授权或实际创建源码  
> 创建时间：2026-08-02  
> 更新时间：2026-08-02  
> Roadmap ID：ROADMAP-INTERVIEW-001  
> 风险等级：L3  
> 产出/适用阶段：5–6 前瞻输入  
> 阶段状态：WaitingForApproval

## 1. 目标树（全部为计划创建）

```text
ai-interview-coach/
├── pom.xml
├── backend/
│   ├── pom.xml
│   ├── interview-domain/
│   ├── interview-application/
│   ├── interview-adapters/
│   ├── interview-boot/
│   └── interview-test-support/
├── frontend/
├── contracts/
│   ├── openapi/
│   ├── asyncapi/
│   └── schemas/
├── benchmarks/
├── deploy/
├── observability/
└── docs/
    ├── features/
    ├── development-records/
    ├── reference/
    ├── research/
    └── releases/
```

## 2. Java package 计划

基础包暂用 `com.aiinterviewcoach` 作为工作名，最终 group/package 需由 DEC-002/032/033 决定，不能据此锁定。

```text
interview-domain/src/main/java/com/aiinterviewcoach/
├── identity/{User,Tenant,Membership,AccountPolicy}.java
├── catalog/{Question,QuestionVersion,RubricVersion,PublicationPolicy}.java
├── practice/{PracticeAttempt,AnswerVersion,PracticeProgress}.java
├── interview/{InterviewPlan,InterviewSession,InterviewTurn,SessionPolicy}.java
├── voice/{AudioArtifact,TranscriptVersion,AudioRetentionPolicy}.java
├── evaluation/{EvaluationVersion,EvidenceSpan,DimensionResult,ReportVersion}.java
├── learning/{Weakness,LearningPlan,LearningItem,ComparabilityPolicy}.java
├── billing/{Entitlement,UsageReservation,Settlement,Order,CostLedgerEntry}.java
├── governance/{ConsentRecord,DeletionRequest,AuditEvent,AdminAccessPolicy}.java
└── platform/{TenantId,CorrelationId,DomainEvent}.java

interview-application/src/main/java/com/aiinterviewcoach/
├── identity/{RegisterUser,AuthenticateUser,ResolvePrincipal}.java
├── catalog/{PublishQuestion,SearchPublishedQuestions}.java
├── practice/{SubmitPracticeAnswer,ListPracticeHistory}.java
├── interview/{CreateInterviewPlan,StartInterview,SubmitAnswer,ApplySessionCommand,RecoverInterview}.java
├── agent/
│   ├── port/{ChatModelPort,SpeechToTextPort,TextToSpeechPort,ProviderFailure}.java
│   ├── InterviewAgent.java
│   ├── EvidenceExtractor.java
│   ├── RubricJudge.java
│   ├── ReportComposer.java
│   └── LearningCoach.java
├── evaluation/{EvaluateAnswer,ComposeReport,ApplyQualityGate}.java
├── voice/{OpenAudioUpload,TranscribeAudio,ConfirmTranscript,SynthesizeSpeech}.java
├── billing/{CheckEntitlement,ReserveUsage,SettleUsage,ReleaseUsage,HandlePaymentCallback}.java
├── governance/{GrantConsent,RequestExport,RequestDeletion,AuthorizeAdminAccess}.java
├── operations/{ChangeFeatureFlag,QueryProviderHealth}.java
└── platform/{JobPort,OutboxPort,IdempotencyPort,ClockPort}.java

interview-adapters/src/main/java/com/aiinterviewcoach/
├── inbound/{rest,sse,websocket,webhook}/
├── persistence/{identity,catalog,practice,interview,voice,agent,evaluation,learning,billing,governance,operations,platform}/
├── provider/{llm,asr,tts,payment,storage}/
├── security/{cookie,csrf,rbac,tenant}/
└── observability/{metrics,tracing,redaction}/

interview-boot/src/main/java/com/aiinterviewcoach/boot/
├── InterviewCoachApplication.java
├── ApiConfiguration.java
├── SecurityConfiguration.java
├── ProviderConfiguration.java
├── WorkerConfiguration.java
└── properties/
```

## 3. Migration 计划

文件编号在正式 `tasks.md` 按实际基线分配；此处的 `V###` 只是顺序占位，不可直接当最终文件名。

```text
interview-boot/src/main/resources/db/migration/
├── V###__create_identity_schema.sql
├── V###__create_catalog_schema.sql
├── V###__create_practice_schema.sql
├── V###__create_agent_registry.sql
├── V###__create_billing_entitlement_and_reservation.sql
├── V###__create_interview_plan_session_turn.sql
├── V###__create_platform_outbox_job_idempotency.sql
├── V###__create_evaluation_and_report.sql
├── V###__create_learning_plan.sql
├── V###__create_voice_artifact_and_transcript.sql
├── V###__create_governance_consent_deletion_audit.sql
├── V###__create_billing_order_payment_cost_ledger.sql
└── V###__create_operations_projection_and_feature_flag.sql
```

每个 migration 需要 owner、前向兼容、失败恢复、锁/时长评估；破坏性变化拆成 expand/migrate/contract。运行应用不拥有 DDL 权限。

## 4. API/事件/Schema 契约计划

```text
contracts/openapi/
├── common.yaml
├── identity.yaml
├── catalog.yaml
├── practice.yaml
├── interview-plan.yaml
├── interview-session.yaml
├── evaluation-report.yaml
├── learning.yaml
├── voice.yaml
├── billing.yaml
├── privacy-audit.yaml
└── operations.yaml
contracts/asyncapi/
├── interview-events.yaml
├── job-events.yaml
├── voice-events.yaml
└── payment-webhooks.yaml
contracts/schemas/
├── error-envelope.schema.json
├── event-envelope.schema.json
├── interviewer-action-v1.schema.json
├── evidence-extraction-v1.schema.json
├── rubric-judgement-v1.schema.json
├── report-composition-v1.schema.json
└── learning-plan-v1.schema.json
```

## 5. React 计划

```text
frontend/src/
├── app/
│   ├── router.tsx
│   ├── providers.tsx
│   ├── routes/{public,auth,workspace,admin}.tsx
│   └── errors/{RootErrorPage,RouteErrorPage}.tsx
├── features/
│   ├── identity/{pages,components,hooks,api,store}/
│   ├── catalog/{pages,components,hooks,api}/
│   ├── practice/{pages,components,hooks,api,store}/
│   ├── interview/
│   │   ├── setup/{InterviewSetupPage,PlanPreview}.tsx
│   │   ├── room/{TextInterviewPage,InterviewTimeline,AnswerComposer}.tsx
│   │   ├── hooks/{useInterviewEvents,useInterviewRecovery}.ts
│   │   └── store/interviewUiStore.ts
│   ├── evaluation/{pages,components,hooks,api}/
│   ├── learning/{pages,components,hooks,api}/
│   ├── voice/
│   │   ├── input/{MicrophoneConsent,Recorder,TranscriptEditor}.tsx
│   │   ├── output/{AudioPlayer,VoiceTurnStatus}.tsx
│   │   ├── hooks/{useVoiceSocket,useSpeechPlayback}.ts
│   │   └── store/voiceUiStore.ts
│   ├── billing/{pages,components,hooks,api}/
│   ├── privacy/{pages,components,hooks,api}/
│   └── admin/{catalog,operations,audit,billing}/
└── shared/
    ├── api/{client,error,eventEnvelope}.ts
    ├── auth/
    ├── recovery/
    ├── accessibility/
    └── components/{AsyncState,ErrorNotice,PermissionDenied,ReconnectBanner}.tsx
```

TanStack Query 负责服务端状态；Zustand 只负责短期交互/设备/连接；服务端仍是 session、tenant、quota、deletion 真相。每个页面计划相邻放 `.test.tsx` 和 Playwright 场景引用，具体数量由 Phase `tasks.md` 决定。

## 6. 测试与证据计划

```text
interview-domain/src/test/java/.../*PolicyTest.java
interview-application/src/test/java/.../*UseCaseTest.java
interview-adapters/src/test/java/.../*RepositoryIT.java
interview-adapters/src/test/java/.../*ProviderContractTest.java
interview-boot/src/test/java/.../*ApiIT.java
interview-test-support/src/main/java/.../{builders,fakes,containers,golden}/
frontend/src/**/*.test.tsx
e2e/{auth,catalog,practice,text-interview,voice-fallback,report-learning,privacy-billing}/*.spec.ts
benchmarks/{fixtures,runners,reports}/
security/{tenant,csrf,cors,upload,log-redaction}/
performance/{sse,websocket,job-backlog}/
docs/features/<FEAT-ID>-<slug>/{verification,acceptance,release-readiness}.md
```

这些都是计划位置。当前没有创建任何源码/测试，也没有测试结果；真实 Provider、浏览器 UAT、故障注入、性能和部署验证均需独立授权。

## 7. 文件修改边界

本路线实施时，“计划修改”优先限于根 POM、对应 module POM、Boot 装配、契约索引、Router、共享 API/错误层和明确的跨域事件；逻辑域不得为了便利修改其他域内部文件。每一期具体新增/修改树见对应 `phase-XX.md`，正式文件清单只能在批准的 Feature `tasks.md` 中落定。
