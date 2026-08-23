# 第 03 期：Identity、personal tenant 与同意基线

> 文档类型：Phase  
> 文档状态：Draft  
> Phase ID：PHASE-03  
> 风险等级：L3  
> 产出/适用阶段：5–9 前瞻规划  
> 阶段状态：WaitingForApproval  
> owner：identity / governance / frontend  
> 证据结果：NotRun  
> 前置：PHASE-01；登录渠道和同意原型批准

## 1. 本期为什么存在

所有练习、会话、音频和报告都必须从第一条数据起具备身份、personal tenant 和用途同意；后补 tenant/consent 会造成高风险迁移和历史数据无归属。

## 2. 用户可见目标

用户可注册/登录、建立个人空间和基础转型目标、查看协议版本与隐私入口；会话过期、无权限和退出有明确反馈。

## 3. 技术学习目标

掌握 Spring Security Cookie session、CSRF、tenant-aware principal、RBAC、资源 owner、协议版本和隐私 by design。

## 4. 范围

一种批准登录方式、找回/验证端口、User/PersonalTenant/Membership/Session/Profile、基础角色、协议同意、登录限流、账号/隐私页面和审计基线。

## 5. 非目标

不做 SSO/SCIM、B2B 组织、社交登录矩阵、完整注销物理删除、管理员敏感内容访问或支付实名。

## 6. 前置决策

DEC-014、028、029、041、042、049；协议/隐私文案和同意目的需产品/隐私 owner 批准。

## 7. 前置期次和依赖

依赖 01 的 Boot、安全配置、错误契约和 Flyway。与 04 可并行，但不得共享 schema owner。

## 8. 涉及的 REQ/BR/AC/DES

REQ-11/12；BR-06/10/12；AC-04/10/12 的基础；DES-MOD-01/10、DES-DATA-TENANT、DES-SEC-AUTH。

## 9. 本期完整功能点

- 注册、登录、退出、当前用户、找回 port 与 session 轮换。
- 自动创建 personal tenant 和 membership；基础目标档案。
- tenant/role/resource owner 授权与统一 `FORBIDDEN`。
- 服务协议/隐私政策版本同意和撤回入口占位。
- 登录/退出/拒绝的最小审计与账号/隐私 UI。

## 10. 正常流程

注册→验证/登录→创建 personal tenant→记录必要协议版本→进入空 Dashboard→退出并吊销 session。

## 11. 空态、错误、拒绝、取消和恢复

新用户显示目标档案空态；重复账号/错误凭据/限流明确；跨 tenant 默认拒绝；用户可取消注册/不同意非必要用途；session 过期后登录并回到安全入口，不恢复敏感表单正文。

## 12. 后端模块

domain `User/PersonalTenant/Membership/IdentityPolicy/ConsentRecord`；application `RegisterUser/AuthenticateUser/ResolvePrincipal/GrantConsent`；adapters security/persistence/channel；boot `SecurityConfiguration`。

## 13. 前端页面和组件

`/auth/register`、`/auth/login`、`/auth/recover`、`/app/onboarding`、`/app/settings/account`、`/app/settings/privacy`；`SessionExpiredNotice`、`ConsentPanel`、`ProtectedRoute`、identity hooks。

## 14. 数据实体、约束和迁移

`identity.user_account`、`tenant`、`membership`、`web_session`、`profile_version`；`governance.consent_record`。账号标识唯一且规范化；personal tenant 一用户至少一个；consent append-only/revoke；私有表 tenant_id NOT NULL。

## 15. REST/SSE/WebSocket/API 或事件

`POST /auth/register|login|logout|recover`、`GET /me`、`PUT /profile`、`POST /consents`；Cookie+CSRF；事件 `identity.user.registered`、`governance.consent.changed` 只含最小 ID/版本。

## 16. Agent/Prompt/Provider

无 LLM。Email/SMS/OAuth 仅 `IdentityChannelPort`，实现前按 DEC-041 选择；fake 只证明用例。

## 17. 安全与隐私

Secure/HttpOnly/SameSite Cookie、session fixation 防护、密码/验证码安全、登录限流、精确 CORS；前端不存长期 token；同意目的分离，不以注册同意覆盖语音/训练用途。

## 18. 计划新增文件树

```text
interview-domain/.../identity/{User,PersonalTenant,Membership,IdentityPolicy}.java
interview-domain/.../governance/ConsentRecord.java
interview-application/.../identity/{RegisterUser,AuthenticateUser,ResolvePrincipal}.java
interview-adapters/.../{security/tenant,persistence/identity,provider/identity-channel}/
interview-boot/.../api/identity/; db/migration/V###__create_identity_and_consent.sql
frontend/src/features/identity/{pages,components,hooks,api}/
frontend/src/features/privacy/pages/PrivacySettingsPage.tsx
contracts/openapi/{identity,consent}.yaml
```

## 19. 计划修改文件树

```text
interview-boot/.../SecurityConfiguration.java
frontend/src/app/router.tsx
contracts/openapi/common.yaml
docs/reference/{security,configuration}.md
```

## 20. 后续 TASK 拆分建议

数据/tenant、登录渠道、Cookie/CSRF、授权策略、同意、前端流程、越权/安全验证分别拆 TASK；外部邮件/短信费用独立授权。

## 21. 验证建议

单元：账号/tenant/consent 状态；集成：session/CSRF/tenant Repository；契约：错误码；UI：注册、过期、拒绝；Golden Set：NotApplicable；UAT：首次登录与隐私告知。安全测试不能证明法律合规。

## 22. 明确完成标准

两个 personal tenant 无法互读；session 轮换/退出/过期可观察；同意版本可查询/撤回；前端无长期凭据；实际 EV 存在后才完成。

## 23. 本期不能证明什么

不能证明完整删除、管理员强审计、生产身份渠道可用、法规合规结论或任何面试功能。

## 24. 风险与停止条件

tenant scope 不能在 Repository/API 双重强制、登录渠道未决却要真实调用、协议目的不清、前端要求 localStorage token 时停止。

## 25. 下一期进入条件

principal/tenant/role 契约稳定，后续数据表可强制 tenant；Catalog 公共内容访问规则获批准。

## 26. 建议学习和复盘内容

复盘 authentication vs authorization、session vs JWT、CSRF/CORS、multi-tenancy、append-only consent、隐私入口≠完成删除。
