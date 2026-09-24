# SPEC-mobile-uniapp：移动端（uni-app Vue 3 一码多端，方案 B）

> 文档状态：**Draft / WaitingForApproval**
> 文档类型：架构 Spec（候选，未批准）
> 创建日期：2026-08-16
> 最近更新：2026-08-16（方案 B 重写）
> 出品阶段：R1（多端前端回归路线 / 方案 B）
> Spec ID：SPEC-mobile-uniapp
> 关联决策：`DEC-022`、`DEC-072`、`DEC-073`、`DEC-008`、`DEC-035`、`DEC-036`
> 关联设计：`docs/architecture/技术架构.md` 第 8 节
> 关联授权：`AGENTS.md` 第 7 节 / 第 30 行 / 第 34 行；`决策登记表.md` 第 167 行 + 第 2.4 节

本 SPEC 在以下条件**全部满足**之前**不执行**：

1. 用户对 `DEC-072` 拍板为 `Accepted A`（决策表已填 A，本文件生效）；
2. 本 SPEC 状态由 Draft → Approved；
3. 一项**单独**的 `apps/mobile/` 项目初始化授权；
4. 微信小程序 AppID 由用户提供（用户级授权）；
5. 一项**单独的** `pnpm install` / `vue-tsc` / 微信开发者工具连接授权；
6. `frontend/` 处置选项（A/B/C）已选定 + 对应授权（mobile 不直接复用 React feature，但 portal-web 仍迁移 landing 等子集，间接受 frontend 处置影响）。

## 1. 一句话与目标

为 AI 面试教练提供 H5 + 微信小程序双端移动端复用同一份 Vue 3 源码。Provider Key 不进入移动端运行时；音频流强制走后端签名 URL。**不**复用 RuoYi 上游（uni-app 与 RuoYi 生态无重叠）；**不**复用 React feature 组件。

## 2. 范围

### 2.1 平台目标

| 平台 | 状态 | 说明 |
|---|---|---|
| H5（浏览器） | P0 | 一致性最高，与 SPEC-portal-web 主要页面功能相通 |
| 微信小程序 | P0 | 通过 `RecorderManager` 录音 |
| Android 原生（App） | P1 | uni-app 离线打包，仅在 H5 验证稳定后启动 |
| iOS 原生（App） | P1 | 同上 |
| 支付宝 / 抖音 / QQ 小程序 | OUT（暂不启用） | 见 DEC-067 |

### 2.2 P0 模块

- identity：登录 / 账号 / 隐私 / 删除入口
- catalog：题库浏览 / 筛选 / 详情
- interview：面试会话（文本优先，语音次之）
- voice：H5 走 MediaRecorder；小程序走 `RecorderManager`；**采集结束一律走 /api/v1/mobile/audio/upload 上传**，拿回转写 ID
- report：只读 / 导出触发邮件
- learning：弱项列表 / 计划 / 复测

### 2.3 P1 模块

- 推送通道（provider-agnostic）
- 暗色模式
- 一键分享会话（需先解决 DEC-066）

### 2.4 不在本 SPEC

- 后台管理（SPEC-admin-web-ruoyivue3）
- 用户 PC Web（SPEC-portal-web）
- 后端任何端点（SPEC-backend-bridges）

### 2.5 frontend/ 与 mobile 的关系

mobile **不**复用 React feature 视图层。mobile 与 portal-web 共享"模式"（Pinia / Vue Router / 路由命名），**不**共享代码实现。原因：

- React 组件不能跨 uni-app 编译目标运行；
- uni-app 编译器对 Vue 3 模板语法有自己的限制（如不支持部分 TSX / 部分 setup 语法糖）；
- 移动端的 native 录音 / 摄像头 / 推送 API 必须按 uni-app 适配层写。

`frontend/src/features/voice/input/` 等 H5 录音代码**不**直接复用，但**思路**可以借鉴：mobile 录音封装走 `RecorderManager`，但鉴权 / 上传 / 进度状态机与 portal-web 保持一致。

## 3. 非目标

- 不实现端到端 Realtime 语音（DEC-059 延后）
- 不引入 Provider SDK 到小程序内；不自建 STUN/TURN
- 不绕过 DEC-035 / DEC-036 的中国地域与删除 SLA
- 不做 PC 与小程序的 UI 完全像素一致
- **不**复用 React feature 组件到 mobile

## 4. API 边界

- REST：`/api/v1/mobile/*` 与 `/api/v1/identity/*` 等
- SSE：与 portal-web 复用 `/api/v1/streams/*`
- WebSocket：**不直接走长连接**，录音以分段上传 + 任务轮询为主；如需流式 ASR 走服务端中转（详见 SPEC-backend-bridges § 4）

## 5. 鉴权与安全

- DEC-073 A：Cookie Session 在 H5 工作；小程序由后端 `wechat-mini-program-session` 流程换出系统 Cookie（H5 与小程序状态视为同账号）
- 录音：必须经用户手势触发；权限被拒后不反复弹窗
- 录音只通过服务端签名 URL 上传；移动端永不直连对象存储
- LLM/ASR/TTS Provider Key 一律服务端持有

## 6. 部署

- H5 与 SPEC-portal-web 同源（同 SPA）前置路由切换
- 小程序：`wx-xxxxxxx`，需用户提供小程序 AppID 并通过 IP 白名单申请
- App：用户在用户级授权后才到 R5 后的子 SPEC（不在 R1）

## 7. 测试

- 单元：`@dcloudio/uni-cli-shared` + Vitest 不可直接兼容，使用 H5 浏览器驱动
- 关键路径：登录 / 题库首屏 / 录音上传 / 报告查询 / 删除
- 真机验证：R5 单独走用户授权；本 SPEC 仅规划

## 8. 与现有 React 前端的关系

- 与 SPEC-portal-web 同步：现有 `frontend/` React 19 工程在 Approved 后归档为 `frontend-legacy/`
- **不**复制 React 组件到 mobile
- **不**引入 React Native / Taro 原生壳

## 9. 不可执行动作（R1 期间）

按 R0 评审报告第 9 节同步执行；同时：

- ❌ 不在 `manifest.json` 写入任何真实 AppID
- ❌ 不申请微信小程序账号（用户级授权）
- ❌ 不调用 uniCloud / 第三方云函数
- ❌ 不引入 `RecorderManager` 之外录音 API
- ❌ **不**引入 RuoYi 上游（uni-app 与 RuoYi 生态无重叠）

## 10. 验收（进入 R5 实现合约的 Gate）

- [ ] 用户对 `DEC-072` 拍板为 `Accepted A`（已完成）
- [ ] 本 SPEC 状态 Draft → Approved
- [ ] `apps/mobile/` 单独立项 + HBuilderX 初始化单独授权
- [ ] 微信小程序 AppID 由用户提供（用户级授权）
- [ ] `frontend/` 处置选项（A/B/C）已选定 + 对应授权

## 11. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 小程序录音授权被拒 | 语音面试无法跑 | 文本降级（DEC-008 A 路径已铺） |
| uni-app Vue 3 升级打破 pinia store | 移动端与 SPEC-portal-web 复用破裂 | 仅复用"模式"，不强制"实现复用" |
| 微信小程序开发者工具对 vue 3 TSX 支持延迟 | DX 不畅 | R5 落"DCloud 控制台"模式而不是 HBuilderX |
| LLM Provider Key 偷渡到小程序 | 破 DEC-048 / DEC-028 | 小程序内禁用 `wx.request` 写死 URL；以白名单域名 + 后端中转 + 签名 URL 三重保险 |

## 12. 与决策表的回链

- DEC-072 已填 A：`uni-app Vue 3`
- DEC-022 已填 C：`Vue 3 多端，与 DEC-070/071/072/073 联动`
- DEC-073 已填 A：`Cookie Session + Pinia 派生`
- 决策表第 2.4 节"方案 B 模式生效与执行边界"同步生效