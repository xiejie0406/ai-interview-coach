# SPEC-admin-web-ruoyivue3：后台管理前端（基于 RuoYi-Vue3 改造 + 迁移现有 frontend 功能）

> 文档状态：**Draft / WaitingForApproval**
> 文档类型：架构 Spec（候选，未批准）
> 创建日期：2026-08-16
> 最近更新：2026-08-16（方案 B 重写）
> 出品阶段：R1（多端前端回归路线 / 方案 B）
> Spec ID：SPEC-admin-web-ruoyivue3
> 关联决策：`DEC-022`、`DEC-070`、`DEC-073`
> 关联文档：`docs/licenses/若依Vue3上游评审报告.md`（上游评审报告）、`docs/architecture/若依迁移计划.md`（迁移计划）
> 关联设计：`docs/architecture/技术架构.md` 第 8 节
> 关联授权：`AGENTS.md` 第 7 节 / 第 30 行 / 第 34 行；`决策登记表.md` 第 167 行 + 第 2.4 节

本 SPEC 在以下条件**全部满足**之前**不执行**：

1. 用户对 `DEC-070` 拍板为 `Accepted A`（决策表已填 A，本文件生效）；
2. 用户对 `docs/licenses/若依Vue3上游评审报告.md` **签收**（commit hash / 占位策略 / frontend 处置 三选一拍板）；
3. `docs/architecture/若依迁移计划.md` 用户审过；
4. 本 SPEC 状态由 Draft → Approved；
5. 一项**单独的** `git clone` 授权；一项**单独的** `pnpm install` / `vue-tsc` / `vite build` 授权。

## 1. 一句话与目标

后台管理前端以 `RuoYi-Vue3`（MIT）为骨架，**仅借鉴其通用管理页面与 Element Plus 通用组件**；鉴权、API 路由、Provider Key 处理**重写**。**新增**：将现有 `frontend/src/features/{identity,catalog,practice,interview,voice,evaluation,learning,billing,privacy,status,admin,dashboard}` 12 个 feature（landing 归 portal-web）**逻辑层抽取 + 视图层重写**为 Vue 3 + Element Plus，迁移到 RuoYi 的 `views/` 下；迁移完成后 `frontend/` 整体归档为 `frontend-legacy/`。

本 SPEC 是 `AGENTS.md` 第 7 节"商业独立"与第 30 行"未批准不伪造门禁"在本 SPEC 范围的具象化。

## 2. 范围

### 2.1 拟借鉴（来自 RuoYi-Vue3）

- 用户 / 角色 / 部门 / 菜单 / 字典 CRUD 页面（仅 Vue 文件，不含后端）
- 操作 / 登录日志、在线用户、服务 / 缓存监控页面
- 代码生成器前端（template / table / form / sql preview），输出适配 `backend` 的 Flyway / JdbcClient 模板
- 国际化词条 `zh-CN`，与 portal-web 共用字典
- Element Plus 通用组件与样式
- 主题切换 / 暗黑模式

### 2.2 自研（不复制 RuoYi-Vue3 代码）

- 鉴权：Pinia store + 路由守卫 → `/api/v1/identity/profile`
- API Client：`fetch(..., { credentials: 'include' })` 单例
- RBAC 注解层：Spring Security `@PreAuthorize` 的等价前端判断
- **现有 React feature 视图层**：从 `frontend/src/features/*/pages/` 抽取的逻辑层（login API / 类型 / Pinia store）翻译为 Vue 3，**视图层全部重写**
- 权限维度敏感访问审计提示（P0 留位 / P1 实现）

### 2.3 明确不引入

- RuoYi-Vue3 的后端 `RuoYiApplication.java`、`RuoYiServletFilter`、MyBatis-Plus 配置、Sa-Token 入口、Admin-Token 实现
- RuoYi-Vue3 的 Hutool 全部（仅按需引入子模块并在 LICENSE 摘要登记）
- RuoYi-Vue3 的支付 / 论坛 / 商城无关模块
- RuoYi-Vue3 的 RBAC 注解层（`@RequiresPermissions`、`@RequiresRoles`）
- **现有 React 前端的所有 .tsx / .ts / .css / .json**（不复制到 admin-web）

### 2.4 frontend/ 处置（与 DEC-070 关联）

详见 `docs/architecture/若依迁移计划.md` 第 6 节。三选项（A 移 `frontend-legacy/` / B 移 `docs/reference/legacy-frontend/` / C 删除 + bundle）由用户拍板；执行时必须**保留历史证据**（git log / bundle / reference 链接）。

## 3. 非目标

- 不替代 SPEC-portal-web：portal-web 不依赖本 SPEC 的页面
- 不复制 RuoYi-Vue 的 Vue 2 版本
- 不绑定 GPL-3.0 / Apache-2.0 传染性许可的项目组件
- 不复制现有 React 组件到 admin-web

## 4. API 边界

- 只与 `backend` 通信，路由前缀 `/api/v1/admin/*` 与 `/api/v1/identity/*`
- 不另起后端进程
- 不引用 RuoYi 默认后端 URL（`https://localhost:8080/ruoyi-*`）

## 5. 部署

- 同源部署于 `https://admin.example.com`
- 通过 Spring Modulith `interview-admin-bridge` module 暴露的端点工作；详见 `规格-后端统一网关层.md`

## 6. 安全

- DEC-073 A：Cookie Session，不存 Token
- 在 `apps/admin-web/src/utils/auth.ts` 中**不调用**任何 `localStorage.setItem('token', ...)`，违反即 R3 评审一次性驳回
- 提权审批界面（reason code + 二次确认）默认占位；具体规格待 SPEC-治理细节后续 SPEC（不在 R1 范围）

## 7. 测试

- Vitest + Vue Test Utils + Element Plus 组件 stub
- 关键路径：登录 / 路由守卫 / 用户管理 / 角色权限 / 操作日志查询
- 不引入 RuoYi 的 `RuoYiSwagger` 配置（与本后端契约脱钩）

## 8. 引入方式（R2 起执行，须单独授权）

按 `docs/licenses/若依Vue3上游评审报告.md` 第 4 节"改造范围"逐文件采纳；引入动作必须通过 `git clone` 单次完成并落 `apps/admin-web/` 子目录，**禁止**把 RuoYi 全仓复制粘贴入本仓的主分支树。`.upstream/` 占位策略由用户拍板（A 永久参考仓 / B 临时 patch / C fork 名），详见 `docs/licenses/若依Vue3上游评审报告.md` 第 8 节。

## 9. 不可执行动作（R1 期间）

按 R0 评审报告第 9 节；同时不引入 `RuoYiApplication`、不复制 `RuoYiServletFilter`、不复制 RBAC 注解层；**不复制现有 React 组件**到 admin-web。

## 10. 验收（进入 R2 实现合约的 Gate）

- [ ] 用户对 `DEC-070` 拍板为 `Accepted A`（已完成）
- [ ] 用户对 `docs/licenses/若依Vue3上游评审报告.md` **签收**
- [ ] 用户对 `docs/architecture/若依迁移计划.md` **签收**
- [ ] 本 SPEC 状态 Draft → Approved
- [ ] `apps/admin-web/` 单独立项 + `git clone` 单独授权
- [ ] `pnpm install` / `vue-tsc` / `vite build` 已获授权
- [ ] `frontend/` 处置选项（A/B/C）已选定 + 对应授权

## 11. 维护与升级策略（仅占位，R3 时细化）

- RuoYi-Vue3 上游变更触发 `apps/admin-web/CHANGELOG.md` 内部版本
- CVE / 安全补丁：建立 `apps/admin-web/MIT-LICENSE-NOTICE.md` 维护版权声明（详见评审报告第 2.3 节）
- 升级方式（patch / minor）：通过 `apps/admin-web/.upstream/ruoyi-vue3/.git` 的"比对"模式更新具体文件，**不走 submodule**（如选 A 占位策略）

## 12. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 把 RuoYi 默认 JWT 鉴权代码偷渡进来 | 破 DEC-073；破 RuoYi MIT 的保留声明要求 | R3 评审逐 `git log -p` 检查；R0 评审报告第 9 节为阻断 |
| 上游维护停滞 | 长期 CVE 风险 | R3 后建立内部 fork；切分支时保留主分支对外宣传口径 |
| GPL 传染 | 误引 Plus 子模块 | 不引入 Plus；只引 Vue 3 主仓库的前端部分 |
| 与 portal-web 重复 | 字典 / 组件不一致 | 第 7 节抽离 `@ai-coach-ui` 二级包（不在 R1 范围） |
| React feature 视图层被原样复制 | 引入 React 依赖 / 包体积膨胀 | SPEC 第 2.2 / 2.3 节硬约束；R3 评审逐 component 检查 |
| frontend/ 删除丢历史 | R7 评审无对照 | 删除前必须 `git bundle create` 离线打包 |

## 13. 与决策表的回链

- DEC-070 已填 A：`RuoYi-Vue3，git clone + 迁移 frontend 功能`
- DEC-022 已填 C：`Vue 3 多端，与 DEC-070/071/072/073 联动`
- DEC-073 已填 A：`Cookie Session + Pinia 派生`
- 决策表第 2.4 节"方案 B 模式生效与执行边界"同步生效