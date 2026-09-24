# RuoYi-Vue3 上游评审报告（Draft / 用户未签收）

> 文档状态：**Draft / 用户未签收**
> 评审日期：2026-08-16
> 评审对象：`RuoYi-Vue3`（候选上游）作为本项目 admin-web 后台前端骨架
> 适用范围：仅当 `文档/决策/决策登记表.md` 中 `DEC-070` 被用户明确 `Accepted A` **且本评审报告由用户签收**后，才能进入 `git clone` 执行包
> 关联决策：`DEC-022`、`DEC-070`、`DEC-071`、`DEC-072`、`DEC-073`
> 关联规范：`AGENTS.md` 第 7 节（保护商业独立）、`AGENTS.md` 第 30 行（未批准不伪造门禁）、`文档/决策/决策登记表.md` 第 167-168 行 / 第 2.4 节

**结论一句话**：方案 B 推进的前提是**本文**评审报告由用户签收 + 锁定上游 commit hash + 三选一占位策略（永久参考仓 / 临时 patch / fork 名）+ 创建 `apps/admin-web/MIT-LICENSE-NOTICE.md`；**任一缺失均不进入 R2 物理动作**。

---

## 1. 来源与仓库元数据

| 项 | 值 |
|---|---|
| 仓库名 | `RuoYi-Vue3`（官方 Vue 3 原版，与 `RuoYi-Vue` 共享同一个后端 `y_project/RuoYi-Vue`，仅前端变更） |
| 维护者 | `yangzongzhuan`（"若依"作者本人） |
| 主前端栈 | Vue 3 + Element Plus + Vite + Pinia + Vue Router 4 |
| 后端基线 | 与 `y_project/RuoYi-Vue` master 一致（Spring Boot 4.x、JDK 17+、Spring Security、JWT、Hutool 等） |
| 镜像地址 | Gitee: `https://gitee.com/yangzongzhuan/RuoYi-Vue3`；GitCode: `https://gitcode.com/yangzongzhuan/RuoYi-Vue3`；GitHub: `https://github.com/yangzongzhuan/RuoYi-Vue3` |
| 访问日期 | 2026-08-16 |

> **本项目不拉 `RuoYi-Vue` 后端仓**。`RuoYi-Vue3` 仅作为前端骨架候选；本项目的业务后端是**已有**的 `backend/`（Spring Boot 4.1 + Spring Modulith 2.1 + Flyway，独立产线）。

---

## 2. 许可证深度评审

### 2.1 许可证原文要点

仓库顶部声明：

```
The MIT License (MIT)
Copyright (c) 2018 RuoYi
```

MIT 核心条款（按 SPDX 摘要）：

- ✅ 可商用
- ✅ 可修改
- ✅ 可合并
- ✅ 可再发布
- ⚠️ **唯一条件**：在所有"实质性拷贝"中保留版权与许可声明（`The MIT License (MIT) Copyright (c) 2018 RuoYi`）

### 2.2 与 GPL/AGPL 的传染性差异

| 比较点 | RuoYi-Vue3 | RuoYi-Vue-Plus |
|---|---|---|
| 许可证 | **MIT** | GPL-3.0 + Apache-2.0（部分） |
| 商业分发友好度 | 高 | GPL 具传染性 |
| 与现有 Spring Boot 4 后端耦合 | 前端 + 鉴权改造碰撞面小 | 同左 |
| 仓库活动度（粗估） | 慢 | 快 |

**结论**：MIT 不要求衍生作品开源；只要 `apps/admin-web/` 内文件顶部保留 RuoYi 的版权声明即可。

### 2.3 强制保留声明项（必须落 `MIT-LICENSE-NOTICE.md`）

`apps/admin-web/MIT-LICENSE-NOTICE.md`（R2 时由用户授权创建）必须包含：

1. 上游版权原文：`The MIT License (MIT) Copyright (c) 2018 RuoYi`。
2. **修改声明**：明确指出本项目对哪些文件做了修改（按文件路径列表）。
3. **未修改文件清单**：明确指出哪些文件未修改（按文件路径列表）。
4. 许可证全文副本（MIT License 全文）。
5. **不依赖上游兜底**：本项目对上游维护停滞不承担兜底责任声明。

> **强约束**：缺任意一项 → R3 评审一次性驳回。

---

## 3. 来源 / 维护 / 安全 / 性能 / 合约 评审

### 3.1 维护活跃度

| 维度 | 现状 | 风险评级 |
|---|---|---|
| 最近提交 | 评估期内需要 WebFetch HEAD | 待用户签收时确认 |
| Issue 积压 | 待评估 | 待用户签收时确认 |
| Star / Fork | 待评估 | 待用户签收时确认 |
| CVE 公告 | 待评估 | 待用户签收时确认 |

> **触发条件**：评审报告内所有"待评估"项必须由用户决定采用 HEAD / tag / 指定 commit **任一**作为锁定锚点。

### 3.2 上游撤回/被劫持历史初步排查

| 维度 | 现状 |
|---|---|
| 仓库所有权变更 | 待评估（建议用户在签收前自行确认） |
| 镜像一致性 | Gitee / GitCode / GitHub 三镜像是否同 commit hash |
| 历史 license 变更 | 是否曾从 MIT 改为其它（截至 2026-08-16 未观察到变更） |

> **不**由本文给出结论；本文档**只**做信息记录，结论由用户签收。

### 3.3 安全风险

| 风险 | 影响 | 对策 |
|---|---|---|
| 上游 Element Plus 版本 CVE | admin-web 启动时漏洞 | R3 实施前 `pnpm audit` 单独授权 |
| 上游 Hutool 子模块引入未审查 | 引入不需要的能力 | R3 评审逐子模块审查 |
| 上游 Sa-Token / JWT 默认实现 | 偷渡到 admin-web 违反 DEC-073 A | **不复制**鉴权代码；只借鉴 views |
| 上游 RBAC 注解层 `@RequiresPermissions` | 与本项目 `@PreAuthorize` 冲突 | **不复制**注解层 |
| 上游默认 `pom.xml` MyBatis-Plus 模块 | 强耦合 RuoYi 后端 | **不复制**后端入口 |

### 3.4 性能 / 合约

| 维度 | 风险 | 对策 |
|---|---|---|
| 包体积 | 60+ views + Element Plus 二次封装可能 1.5MB+ | R3 评估按需 tree-shake |
| API 路径 | RuoYi 默认 `/dev-api/*` 与本项目 `/api/v1/*` 不一致 | admin-web 重写所有 API 调用层 |
| SSE / WebSocket | RuoYi 默认无 SSE/WebSocket | 全部自研封装，**不**引入 RuoYi 包 |

---

## 4. 改造范围（**未授权前不动**）

R0 阶段**仅**记录以下"拟借鉴"与"拟改造"区间。实际复制以 `DEC-070 Accepted` + 本报告签收 + 单独 `git clone` 授权为准。

| 区块 | RuoYi-Vue3 默认实现 | 本项目拟借鉴或改造 | 拟另存路径 |
|---|---|---|---|
| 用户/角色/部门/菜单/字典管理页面（Vue 文件） | 完整 CRUD + 树形 + 字典选择 | **借鉴**页面布局与表单结构；API 路径改为指向 `backend` 的 `/api/v1/admin/*` | `apps/admin-web/src/views/system/{user,role,dept,menu,dict}.vue` |
| 操作/登录日志、在线用户、服务/缓存监控 | Element Plus 列表 + 分页 + 详情 | **借鉴** | 同上 |
| 代码生成器前端 | template/table 列表 | **借鉴**，但生成目标导向 `backend` 的 Flyway / JdbcClient 模板 | `apps/admin-web/src/views/tool/gen/*.vue` |
| 国际化字典（zh-CN / en-US） | 内置 | **借鉴**通用词条；不复制与产品/支付/医疗相关敏感词 | `apps/admin-web/src/lang/{zh-CN,en-US}.ts` |
| Element Plus 通用组件（Pagination / Tree / Select 等） | 内置 | **直接复用** Element Plus 包，无需复制源码 | `apps/admin-web/src/components/` 复制 RuoYi 的二次封装 |
| 工具类（Hutool 子模块） | Hutool 完整 | **按需引入**，须在 `pom.xml` 内网段说明加入子模块名 | `apps/admin-web/src/utils/hutool` |
| 鉴权（store / permission / utils/auth / api/login） | **Sa-Token / localStorage Token** | **不复制代码**，自研 Pinia store + 路由守卫统一从 `/api/v1/identity/profile` 拉取 user | 自研，源自 `backend` 的 Session/Cookie 派生 |
| 自带后端入口（`RuoYiApplication.java`、`RuoYiServletFilter`、MyBatis-Plus 配置） | Spring Boot 4 单体 | **不复制**，保留现有 `backend/interview-boot` | `apps/admin-web/` 不引入后端 |
| 主题切换、暗黑模式 | 深/浅主题 | **借鉴** | `apps/admin-web/src/assets/styles/` |

> **明确不复制**：`RuoYiApplication.java`、`RuoYiServletFilter`、`pom.xml`、MyBatis-Plus 模块、Sa-Token 入口、`Admin-Token` 实现；它们若进入仓库视为违反 `AGENTS.md` 第 7 节"商业独立"。

---

## 5. 与现有 Spring Modulith 后端的耦合边界

| 维度 | 现行 | RuoYi-Vue3 默认 | 解耦方案 |
|---|---|---|---|
| 前端 → 后端协议 | REST + SSE + WebSocket | RuoYi 默认 REST + Axios | admin-web 继续走 REST；后续 SSE/WebSocket 全部自研封装 |
| 后端鉴权 | `backend/interview-boot` Spring Security + 数据库 Session | RuoYi 默认 JWT + Sa-Token | `apps/admin-web` 走 Cookie Session 派生 user/roles/permissions |
| RBAC 注解 | `@PreAuthorize` 业务表达式 | RuoYi 自定义 `@RequiresPermissions` / `@RequiresRoles` | 沿用 Spring Security `@PreAuthorize`；admin-web 不持有 RBAC 注解层 |
| CORS | 现有 `backend` 已设精确 allowlist | RuoYi 默认 `*` | **不复制** CORS 默认；admin-web dev 代理到 `backend` 同源 |
| Provider Key | 服务端持有 | 不适用 | 保留 |
| 数据库 / Flyway migration | Flyway 15 份已落库 | RuoYi 自带 | 沿用现有 schema；admin-web 不引入新 schema |

---

## 6. frontend/ 迁移到 apps/admin-web/ 的具体动作清单

### 6.1 现有 React feature 盘点（基线）

| feature | 子层（api / hooks / pages） | 移植策略 |
|---|---|---|
| `identity/` | api / pages | 仅**逻辑层**（login API + 派生 user）抽到 Pinia store；**视图层**全部 admin-web 重写 |
| `catalog/` | api / pages | 题库后台视图移植到 `apps/admin-web/src/views/catalog/`；CRUD 适配 RuoYi 的 table + dialog |
| `practice/` | api / pages | 题库后台视图移植 |
| `interview/` | api / hooks / pages | 面试后台视图（监控 / 终止 / 重置）移植 |
| `voice/` | api / hooks / input / output / store | 音频后台视图移植；前端录音封装**不**迁移 |
| `evaluation/` | api / hooks / pages | 评分 / Rubric 后台视图移植 |
| `learning/` | api / pages | 学习计划后台视图移植 |
| `billing/` | api / pages | 套餐 / 额度后台视图移植；**不复制**支付模块 |
| `privacy/` | api / pages | 删除入口 / 审计视图移植 |
| `status/` | api / pages | 系统状态 / Provider 健康度视图移植 |
| `admin/` | api / pages | 内容审核视图移植 |
| `dashboard/` | pages | Dashboard 卡片移植到 `apps/admin-web/src/views/dashboard.vue` |
| `landing/` | pages | **不移植**到 admin-web；`landing` 归 portal-web |

### 6.2 移植原则

| 原则 | 含义 |
|---|---|
| **逻辑层可复用，视图层不复用** | React 组件（含 Tailwind / shadcn / react-router-dom 依赖）**全部重写**为 Vue 3 + Element Plus；只复用"业务逻辑抽象" |
| **API 路径对齐** | React 现有调用 `/api/v1/identity/*` 等路径**保持**；admin-web 复用同一套 backend |
| **类型模型迁移** | `frontend/src/shared/api/types.ts` 等共享类型**逐字段**翻译成 TypeScript 5.7；不引入 `frontend/` 的 `tsconfig.json` |
| **hooks 抽象翻译** | `frontend/src/features/*/hooks/*.ts` 翻译成 `apps/admin-web/src/composables/*.ts`（Pinia composition） |
| **store 翻译** | `frontend/src/features/voice/store/` 翻译成 `apps/admin-web/src/stores/voice.ts`（Pinia） |
| **不引入** | React / Tailwind / shadcn / react-router-dom 任何依赖 |

### 6.3 frontend/ 处置

| 选项 | 含义 | 授权门槛 |
|---|---|---|
| A. 整体移到 `frontend-legacy/`（推荐） | 物理改动，可逆 | 用户单独授权 `git mv` |
| B. 整体移到 `文档/参考/旧前端/` | 作为参考资料保留 | 用户单独授权 |
| C. 删除 | 不可逆 | 用户**二次**确认 |

> **强约束**：执行 C 时必须先**完整**执行 `git bundle create frontend-backup-2026-08-16.bundle --all` 离线打包，备份存 `文档/参考/`。

---

## 7. portal-web 子问题答复

> 原始问题：portal-web 是否也"按相同模式重做"——找一个类似 RuoYi 的 portal 上游？

**答复**（基于 2026-08-16 用户对话）：**不引入额外上游**。

理由：
1. RuoYi-Vue3 是**后台模板**，无 portal 同源上游。
2. 其它开源 portal 候选（vben / SoybeanAdmin / mall-cook）均为后台或商城模板，会带偏 portal 风格。
3. DEC-071 已选 A：自研 Vue 3 + Vite + TypeScript + Pinia，与 admin-web 同栈但**独立**设计语言。

portal-web 复用规则：
- ✅ 复用 admin-web 的 Pinia store 模式 + 路由守卫 + fetch API Client（**抽象**层）
- ✅ 复用 Element Plus 基础组件 + design tokens（**设计**层）
- ❌ **不**复用 admin-web 的 RuoYi views 文件
- ❌ **不**为 portal-web 寻找外部上游

---

## 8. .upstream/ 占位策略（三选一，用户拍板）

| 选项 | 含义 | 优 | 缺 |
|---|---|---|---|
| A. 永久参考仓 | `git clone` 到 `apps/admin-web/.upstream/ruoyi-vue3/`，建 README；主目录 `apps/admin-web/` 写改造代码 | 升级 diff 永远可对照 | 仓体积大（60+ MB） |
| B. 临时 patch | `.upstream/` 不进仓，每次升级靠脚本临时 clone 到 `/tmp` 再生成 patch | 仓体积干净 | 升级时需离线 |
| C. fork 名 | 不保留 `.upstream/`，直接以 fork 形式纳入版本控制（重命名 + 改 import 前缀） | 单仓历史最清晰 | 失去与上游直接对比 |

> **待用户拍板**。**不**拍板则不进入 `git clone`。

---

## 9. 当前不可执行动作清单（R0 / 方案 B 生效后）

按 `AGENTS.md` 第 7 节 / 第 30 行 / 第 34 行 / 本决策表第 167 行：

1. ❌ 不执行 `git clone https://gitee.com/yangzongzhuan/RuoYi-Vue3.git`（**单独授权**）
2. ❌ 不创建 `apps/admin-web/` 目录或任何文件（**单独授权**）
3. ❌ 不创建 `apps/portal-web/` 目录（**单独授权**）
4. ❌ 不创建 `apps/mobile/` 目录（**单独授权**）
5. ❌ 不修改 `backend/pom.xml`、不创建任何 `interview-*-bridge` module（**单独授权**）
6. ❌ 不移动 / 归档 / 删除 `frontend/`（**单独授权 + 二次确认** for 删除）
7. ❌ 不写 `package.json` / `vite.config.ts` / `manifest.json` / `index.html` / `tsconfig.json` / `.env`
8. ❌ 不写 `Dockerfile` / `docker-compose.yml`
9. ❌ 不执行 `pnpm install` / `npm install` / `yarn`
10. ❌ 不执行 `mvn spring-boot:run` / `mvn compile` / `mvn test`
11. ❌ 不执行 `vue-tsc` / `vite build` / `vite dev`
12. ❌ 不执行 `git init` / `git commit` / `git push`
13. ❌ 不调用任何 LLM / ASR / TTS Provider
14. ❌ 不连接任何微信小程序开发者工具
15. ❌ 不触发任何对外部 CDN / npm registry 的网络请求

---

## 10. 执行的前置条件清单（决策 + 文档 + 授权）

执行任何 R2-R8 动作的**全部**前提：

| # | 前置条件 | 类型 | 验收证据 |
|---|---|---|---|
| 1 | `DEC-070/071/072/073` 用户回答 `Accepted A` | 用户决策 | `文档/决策/决策登记表.md` 用户决定列已填 A |
| 2 | 本评审报告（`文档/许可/若依Vue3上游评审报告.md`）由用户**签收** | 用户签收 | 用户口头或文本回复"签收" |
| 3 | 上游 commit hash 已锁定（HEAD / tag / 指定 commit 三选一） | 用户拍板 | 本文件"待用户拍板"项填写 |
| 4 | `.upstream/` 占位策略（A / B / C）已选定 | 用户拍板 | 同上 |
| 5 | portal-web 子问题已答 | 用户拍板 | DEC-071 已 Accepted（自研 Vue 3） |
| 6 | PRD v0.3 收敛通过 + 4 份 SPEC `Draft → Approved` | 文档 Approved | 4 份 SPEC 状态由 Draft 改 Approved |
| 7 | 一项**单独的** `git clone` 授权 | 用户执行授权 | 用户单独说"授权 git clone" |
| 8 | 一项**单独的** `pnpm install` / `mvn` / `vue-tsc` / `git init` 授权 | 用户执行授权 | 同上 |
| 9 | `frontend/` 处置选项（A/B/C）已选定 + 对应授权 | 用户拍板 + 授权 | 本表 + 用户单独说"授权 frontend 处置" |

**任一**为 0 → 不进入 R1 后的执行。

---

## 11. 用户签收栏（占位）

```
[ ] 上游 commit hash：__________
[ ] .upstream/ 占位策略：A / B / C
[ ] frontend/ 处置：A / B / C
[ ] 用户签收日期：__________
[ ] 用户签字 / 文字回复：__________
```

---

## 12. 维护成本注意

RuoYi-Vue3 仓库活动度比 `RuoYi-Vue-Plus` 慢（基于本次访问的"近期动态"列推断）。如果上游长期不更新，本项目需要决定：

- (a) 升级自己 fork（推荐：在 `apps/admin-web/` 内做"自包含"，不与上游 tight-coupling）
- (b) 替换为 `RuoYi-Vue-Plus`（需要重新做许可证评估：GPL-3.0 传染风险）
- (c) 走自研 `C. 自研 Vue 3 + Element Plus`，把现借鉴页面全部抽出来

具体策略待 `DEC-070 Accepted` + 本报告签收后写入 `docs/maintenance/web-maintenance.md`（尚未建立）。
