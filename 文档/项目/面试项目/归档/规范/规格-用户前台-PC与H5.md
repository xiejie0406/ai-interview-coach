# SPEC-portal-web：用户前台 (PC / H5) Vue 3 自研前端架构规格（方案 B）

> 文档状态：**Draft / WaitingForApproval**
> 文档类型：架构 Spec（候选，未批准）
> 创建日期：2026-08-16
> 最近更新：2026-08-16（方案 B 重写）
> 出品阶段：R1（多端前端回归路线 / 方案 B）
> Spec ID：SPEC-portal-web
> 关联决策：`DEC-022`、`DEC-071`、`DEC-073`
> 关联设计：`docs/architecture/技术架构.md` 第 8 节（前端）
> 关联授权：`AGENTS.md` 第 7 节 / 第 30 行 / 第 34 行；`决策登记表.md` 第 167 行 + 第 2.4 节

本 SPEC 在以下条件**全部满足**之前**不执行**：

1. 用户对 `DEC-071` 拍板为 `Accepted A`（决策表已填 A，本文件生效）；
2. 本 SPEC 状态由 Draft → Approved；
3. `docs/architecture/若依迁移计划.md` 用户审过；
4. 一项**单独的** `git clone` 授权（portal-web 自研 Vue 3，**不**引用上游；此项仅指"启动 apps/portal-web/ 项目"的初始化授权）；
5. 一项**单独的** `pnpm install` / `vue-tsc` / `vite build` 授权；
6. `frontend/` 处置选项（A/B/C）已选定 + 对应授权（与 admin-web SPEC 共享）。

## 1. 一句话与目标

为 `ai-interview-coach` 提供 PC 用户前台与 H5 回流场景的自研 Vue 3 前端。**不复用** RuoYi 的鉴权、菜单、字典业务代码；**不引入** portal 上游 fork（理由见评审报告第 7 节）；与 `backend` 共享 REST + SSE + WebSocket。**新增**：将现有 `frontend/src/features/{landing,interview,voice,learning}` 中的 portal 风格视图迁移为 Vue 3 + Element Plus + 自研 design language。

## 2. 范围

### 2.1 P0 包含

- Vue 3.5 + Vite 6 + TypeScript 5.7 + Pinia 2 + Vue Router 4 + Vue I18n 10
- 路由：identity / catalog / practice / interview / voice / evaluation / learning / privacy / status / billing（精简版）
- API Client：基于 `fetch(..., { credentials: 'include' })` 单一实例
- 鉴权：从 `/api/v1/identity/profile` 派生 `user / roles / permissions`；不存 Token
- 错误：统一 `ErrorCode -> 用户文案` 字典；不允许 5xx 触发 UI 死循环
- SSE 客户端封装（带 cursor、eventId 去重、断线恢复）
- WebSocket 客户端封装（带 sequence + 重连预算）
- 录音客户端封装（H5 MediaRecorder；如使用 R5 移动端则不在此 SPEC 重复）

### 2.2 P1 包含

- 主题切换（与 admin-web 共用 design tokens）
- i18n 占位词条 `zh-CN`，`en-US` 不必现在完成
- 报告导出（纯前端 prompt 用户邮件）

### 2.3 不在本 SPEC

- 后台管理（见 `规格-后台管理端-若依Vue3.md`）
- 移动端原生 + 小程序（见 `规格-移动端-uni-app-Vue3.md`）
- 后端任何端点（见 `规格-后端统一网关层.md`）
- **不引入 portal 上游**（vben / SoybeanAdmin / mall-cook 等一律排除）

### 2.4 frontend/ portal 风格 feature 迁移（与 DEC-071 关联）

详见 `docs/architecture/若依迁移计划.md` 第 6 节"frontend/ 迁移清单"。portal-web 接收的迁移子集：

| feature | 子层 | 移植策略 |
|---|---|---|
| `landing/` | pages | landing 引导、hero、价值说明、FAQ 全部用 Vue 3 + 自研 design 重写 |
| `interview/`（portal 部分） | pages | 用户发起面试、查看面试记录、重入面试 |
| `voice/`（portal 部分） | hooks / input / output / store | 录音客户端、波形可视化、ASR 进度条（Pinia 翻译） |
| `learning/`（portal 部分） | pages | 用户视角的学习计划、弱项列表、复测入口 |
| `identity/`（portal 部分） | pages | 登录 / 注册 / 找回 / 隐私设置 |

**不**迁移：catalog / billing / privacy / status / admin / dashboard / evaluation（这些归 admin-web 或暂缓）。

## 3. 非目标

- 不实现 SSR（Nuxt 不进入本项目）
- 不引入 jQuery / Vue 2 兼容代码
- 不在浏览器持有 LLM / ASR / TTS Provider Key
- 不实现 B2B 组织/SSO/邀请码 UI
- **不**寻找 portal 上游 fork（DEC-071 锁定 A 自研）
- **不**复制现有 React 组件到 portal-web

## 4. API 边界

- REST：`/api/v1/*`，详见 `技术架构.md` § 13.2
- SSE：`/api/v1/streams/*`，事件封借鉴 § 13.4
- WebSocket：`/ws/v1/interviews/{id}/voice`，仅 audio 用途
- 不与 RuoYi 后端入口通信

## 5. 部署

- 同源 `https://portal.example.com`；CORS 由 `backend` allowlist 精确控制
- 构建产物为静态资源；CI 单独授权
- 域名 / TLS / 反代不在本 SPEC 范围

## 6. 安全

- 鉴权：DEC-073 A（Cookie Session；不存 Token）
- CSP：默认 `default-src 'self'`；`script-src` 不允许 `'unsafe-inline'`
- 上传：走 `backend` 签名 URL；不直连对象存储

## 7. 测试

- Vitest + Vue Test Utils，覆盖 stores / composables / 路由守卫
- 关键页面：登录 / 题库首屏 / 面试会话入口 / 报告首屏
- 不引入 Playwright（详见技术架构 § 20）

## 8. 与现有 React 前端的关系

- 本 SPEC 一旦 Approved 并进入 R4，**现有 `frontend/` React 19 工程将被归档为 `frontend-legacy/`**（仍存留，作为 R7 历史证据）
- 归档动作需要单独授权；不属于 R1 SPEC 范围
- 归档选项（A 移 `frontend-legacy/` / B 移 `docs/reference/legacy-frontend/` / C 删除 + bundle）由用户拍板；执行与 admin-web SPEC 共享同一动作

## 9. 不可执行动作（R1 期间）

按 R0 评审报告第 9 节；同时**不引入** portal 上游、**不复制** React 组件到 portal-web。

## 10. 验收（进入 R4 实现合约的 Gate）

- [ ] 用户对 `DEC-071` 拍板为 `Accepted A`（已完成）
- [ ] 用户对 `docs/architecture/若依迁移计划.md` **签收**
- [ ] 本 SPEC 状态由 Draft → Approved
- [ ] `apps/portal-web/` 目录建立单独立项（需 R1 之外另授权）
- [ ] `pnpm install` / `vue-tsc` / `vite build` 已获用户单独授权
- [ ] `frontend/` 处置选项（A/B/C）已选定 + 对应授权

## 11. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 与 RuoYi-Vue3 借鉴范围串味 | 自研 portal-web 误用其默认 JWT | SPEC 第 4 节与 DEC-073 锁定鉴权；R4 评审 |
| SSE 重连自研复杂度 | 报告进度流断线 | R4 必须有 SSE 端的 cursor 续约支持；客户端封装必须经过契约测试 |
| WebSocket 跨端复用 | mobile 也要同链路 | 规格-后端统一网关层.md 暴露统一 `/ws/v1/*` |
| 引入 portal 上游 fork | 偏 portal 风格、与 admin-web 共享栈断裂 | DEC-071 锁定 A；R4 评审逐 `git log` 检查依赖 |
| React feature 视图层被原样复制 | 引入 React 依赖 | SPEC 第 2.3 / 2.4 节硬约束；R4 评审 |

## 12. 与决策表的回链

- DEC-071 已填 A：`自研 Vue 3，与 admin-web 同栈但独立空间`
- DEC-022 已填 C：`Vue 3 多端，与 DEC-070/071/072/073 联动`
- DEC-073 已填 A：`Cookie Session + Pinia 派生`
- 决策表第 2.4 节"方案 B 模式生效与执行边界"同步生效