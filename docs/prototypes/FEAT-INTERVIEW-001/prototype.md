# FEAT-INTERVIEW-001 探索性原型审计记录

> 文档类型：原型  
> 文档状态：Draft  
> owner / 责任边界：原型 owner 维护页面、旅程、交互表现和评审问题；产品 owner 维护需求语义并形成评审结论；本文件不批准 PRD、技术设计或实现  
> 创建时间：2026-08-02  
> 更新时间：2026-08-02  
> Feature ID：FEAT-INTERVIEW-001  
> 风险等级：L3  
> 产出阶段：4 原型验证（探索性准备）  
> 阶段状态：InProgress（仅原型工作流；Feature 当前主阶段仍为 3 功能规格、`WaitingForApproval`）  
> Spec 基线：[`prd.md`](../../product/prd.md) v0.2 `Draft`，且 `FUNC-VOICE-002` 行存在字面截断；不是 Approved 基线  
> 原型版本：`prototype/html-v1` 静态高保真 Mock v1  
> 证据结果：NotRun（本轮未运行浏览器检查、真实链路或用户验收）  
> 关联：[`research.md`](../../product/research.md)、[统一产品审查与恢复候选](../../review/canonical-product-recovery-draft.md)、[`decision-register.md`](../../decisions/decision-register.md)、[`state-matrix.md`](state-matrix.md)、[`review-readiness.md`](review-readiness.md)

## 1. 结论与事实边界

`prototype/html-v1` 已经形成可供产品讨论的静态 Mock：源码声明 32 个 hash 页面、32 个状态实验室标签，覆盖公开区、求职者工作台、管理后台和产品决策中心；9 张既有截图覆盖主要桌面与两个窄屏视图。本轮已完整读取原型 README、HTML、CSS、JavaScript、Mock 数据并逐张查看截图。

这组资产目前只能作为**探索性原型**：

- 不连接后端、数据库、账号、支付、对象存储、LLM、ASR 或 TTS。
- 不调用 `getUserMedia`，不录音、不上传、不删除真实数据。
- 页面中的题目、评分、用量、Provider 指标、删除进度和管理员结果均为 Mock 文案。
- `localStorage` 只保存原型视角与 Mock 决策选择；它不是认证、正式决策或生产持久化方案。
- 原型 README 记录过一次本地 Mock 浏览器检查及若干 `Pass`；本轮没有复跑相同检查，因此这里只把它记作“已有历史自述”，不升级为本轮 `EV-*`、UAT 或原型评审结论。
- Canonical PRD 未批准且有截断，Gate A 仅 `DEC-001` 为 `Accepted`。原型不能据此成为 `Approved`，也不能授权编码。

当前判断：**资产足以安排产品走查，但不足以通过阶段 4 原型门**。具体缺口和进入条件见 [`review-readiness.md`](review-readiness.md)。

## 2. 原型资产基线

| 资产 | 唯一用途 | 本轮只读核对 | 不能证明 |
|---|---|---|---|
| [`prototype/html-v1/README.md`](../../../prototype/html-v1/README.md) | 打开方式、Mock 边界、历史检查摘要 | 已读取 | 历史 `Pass` 不是本轮复测或 UAT |
| [`index.html`](../../../prototype/html-v1/index.html) | 静态入口、跳转主要内容、样式和脚本加载 | 已读取 | 生产入口、CSP、鉴权或部署能力 |
| [`assets/app.js`](../../../prototype/html-v1/assets/app.js) | 页面、状态实验室与 Mock 交互 | 已读取；静态计数为 32 个页面声明、32 个 switch case、32 个状态标签 | API、状态机、权限、幂等、恢复或真实副作用 |
| [`assets/styles.css`](../../../prototype/html-v1/assets/styles.css) | 桌面/窄屏视觉、焦点、减少动效和基础语义样式 | 已读取 | 完整 WCAG AA、设备兼容或视觉回归 |
| [`data/mock-data.js`](../../../prototype/html-v1/data/mock-data.js) | 题目、用户、用量和 12 项 Mock 决策 | 已读取 | 真实数据、正式 `DEC-*` 状态或批准范围 |
| [`screenshots/`](../../../prototype/html-v1/screenshots/) | 主要页面的静态视觉快照 | 9 张文件均存在并已逐张查看 | 点击链路、键盘操作、控制台、响应式全量或用户接受 |

本目录只保存可读审计与评审入口；非 Markdown 原型资产继续以 `prototype/html-v1` 为唯一来源，没有复制或修改。

## 3. 页面地图与责任

### 3.1 公开区（6 个页面）

| hash | 页面 | 主要评审问题 | 关联上游 |
|---|---|---|---|
| `#home` | 产品首页 | 垂直定位、10 分钟首练、练习/非作弊边界是否清楚 | REQ-12、BR-10、AC-12；DEC-003/004/011/013 |
| `#flow` | 产品能力与流程 | “选题→作答→追问→报告→复测”是否是首发闭环 | REQ-01/03/07/10；DEC-004 |
| `#sample-report` | 公开示例报告 | Mock/脱敏、低置信和“非招聘结论”是否足够醒目 | REQ-07/08/12、BR-03/04、AC-07/08 |
| `#pricing` | Free / Pro 套餐 | 数值是否会被误认为已批准定价；失败计费文案是否可理解 | REQ-14、BR-09、AC-14；DEC-010/043/044 |
| `#faq` | FAQ | 评分、语音、音频删除、失败计费和诚信边界 | REQ-05/08/11/12/14 |
| `#auth` | 登录 / 注册 | 主登录方式、协议同意、验证/找回/限流和取消恢复 | REQ-11；DEC-014/028/041/042 |

静态源码中的 `#questions` 被归入求职者工作台，不是公开区页面。因此 README 所述“公开题库”目前没有独立公开路由和匿名/登录分界，这是待产品 owner 决定并修订的缺口。

### 3.2 求职者工作台（17 个页面）

| 领域 | hash 页面 | 原型表现 | 关联上游 |
|---|---|---|---|
| 身份/档案 | `#onboarding`、`#dashboard` | 目标设置、跳过、今日任务、能力和继续会话 | REQ-03/09/10/11 |
| 题库/练习 | `#questions`、`#question-detail`、`#practice`、`#focus-practice` | 筛选、来源/版本、文本回答、证据不足、错题/复测 | REQ-01/02/06/07/08/10；AC-01/02/08/11 |
| 面试准备 | `#interview-config`、`#interview-plan` | 岗位/范围/时长/模式、预计用量、追问预算、开始前确认 | REQ-03/04/05/11/14；AC-03/04/14 |
| 文本会话 | `#text-room`、`#session-control` | 单题/追问、暂停、结束确认、恢复和部分报告文案 | REQ-03/04/06/09/12；AC-03/09/12 |
| 语音会话 | `#voice-room` | 授权→录音→ASR→低置信→修正→TTS→失败→文本降级的 Mock 链 | REQ-05/06/07/11；BR-05/06/07；AC-04/05/06 |
| 报告/学习 | `#report-overview`、`#report-evidence`、`#learning-plan`、`#progress` | 维度/证据/限制/三项行动、弱项计划和不可比趋势说明 | REQ-07/08/10；AC-02/07/08 |
| 权益/隐私 | `#usage`、`#privacy` | 预留/结算 Mock、平台失败不扣费、保留/删除状态与部分失败 | REQ-11/14；BR-06/07/09/12；AC-10/14 |

### 3.3 管理后台（8 个页面）

| hash | 页面 | 原型表现 | 关联上游 |
|---|---|---|---|
| `#admin-content` | 题目 / Rubric 列表 | 草稿/发布版本和编辑入口 | REQ-02/06、BR-01/11、AC-11 |
| `#admin-editor` | 创建和编辑题目 | 来源、Rubric 和追问要点 | REQ-02、BR-11 |
| `#admin-review` | 内容审核与版本发布 | 版本对比、来源/Golden Set/第二审核人检查 | REQ-02/08、AC-11；DEC-015/031/039/040 |
| `#admin-provider` | Provider 状态与成本 | 明确标注的 Mock 成功率、延迟、成本和降级 | REQ-05/08/14/15 |
| `#admin-golden` | Golden Set 与评测质量 | Mock 样本、一致率和阻断项 | REQ-07/08、AC-02/08；DEC-031/039 |
| `#admin-complaints` | 投诉与评分纠错 | 评分/内容工单，提示不静默改历史 | REQ-02/08 |
| `#admin-deletion` | 隐私删除请求 | 删除步骤、外部确认和部分失败 | REQ-11、AC-10 |
| `#admin-audit` | 敏感访问审计 | reason code、工单和拒绝提示 | REQ-11、BR-12；DEC-049/050 |

管理员视角目前只是前端角色切换；没有真实认证、MFA、RBAC、资源 owner 校验或审计写入。它只能评审信息架构，不能证明权限边界。

### 3.4 产品决策中心（1 个页面）

`#decisions` 展示 12 项 A/B/C Mock 选择，并明确写明只存浏览器 `localStorage`、不修改正式决策登记。它不是 `DEC-002–068` 的一一映射，也不能作为用户批准记录。截图 `06-decisions.png` 中“首批目标用户”曾选择 B，只代表当时浏览器 Mock 状态；正式 [`decision-register.md`](../../decisions/decision-register.md) 仍以 `Pending` 为准。

## 4. 关键用户旅程

| 步骤 | 角色/入口 | 用户动作 | 原型反馈与下一步 | 关联 AC | 当前限制 |
|---|---|---|---|---|---|
| 1 | 访客 / `#home` | 理解定位并查看流程/示例 | 进入 `#onboarding` 或 `#sample-report`；展示练习和诚信边界 | AC-12 | 未形成真实公开题库；营销/价格未批准 |
| 2 | 新用户 / `#auth`、`#onboarding` | Mock 登录，填写或跳过目标 | 进入 Dashboard | 关联 REQ-11 | 没有可评审的协议版本、必要/可选同意和验证/找回完整流 |
| 3 | 用户 / `#questions` | 搜索、筛选、打开发布题 | 查看来源、Rubric 版本和开始练习 | AC-01/11 | 只有 Mock 列表；公开/登录边界和下线态不完整 |
| 4 | 用户 / `#practice` | 提交文本回答 | 展示维度、用户原话和证据不足；加入复测 | AC-02/08 | 没有真实生成、幂等、草稿恢复或 Golden Set 证据 |
| 5 | 用户 / `#interview-config` | 选择岗位、范围、时长和模式 | 生成 `#interview-plan`，显示题数、追问预算和预计用量 | AC-03/14 | 无题、估算失败、版本过期、取消释放预留未形成专用交互 |
| 6 | 用户 / `#text-room` | 回答、暂停、跳过或结束 | 展示追问、会话状态和结束确认；可进入报告 | AC-03/09 | 跳过只是 toast；断线、重复提交、Provider 失败主要为通用横幅，不是完整恢复流 |
| 7 | 用户 / `#voice-room` | 先看用途，模拟授权/拒绝；完成 ASR/TTS Mock 链 | 拒绝或 TTS 失败可回文本 | AC-04/05/06 | 无真实麦克风/外发；“修正”没有可编辑文本；取消录音和删除 Artifact 未覆盖 |
| 8 | 用户 / `#report-overview` | 打开报告和逐题证据 | 查看限制与三项行动，进入学习计划 | AC-07/08 | 主动结束后仍落到普通报告，未显式标记部分报告；72 总分的产品语义待 DEC-013 |
| 9 | 用户 / `#learning-plan` | 接受建议并复测 | 展示建议理由、计划和趋势 | 关联 REQ-10 | 拒绝/删除/重排、无可用题、不可比版本只做文案示例 |
| 10 | 用户 / `#privacy` | 查看保留和删除状态 | 显示外部确认、备份过期和部分失败 | AC-10 | 缺少删除范围选择、强确认、legal hold、撤回同意和不可逆点 |
| 11 | 管理员 / `#admin-review` | 对比版本并评审发布 | 显示来源、Golden Set 和第二审核人检查 | AC-11 | 未勾第二审核人时“发布”仍可点击；没有真实权限/拒绝/审计 |
| 12 | 管理员 / `#admin-audit` | 申请敏感访问 | 缺 reason code 时显示校验错误 | 关联 BR-12 | 没有 MFA、短会话、真实审批或审计回读 |

## 5. 已表达的交互决定与待确认语义

| 原型当前表达 | 来源依据 | 状态 | 产品 owner 必须决定的语义 |
|---|---|---|---|
| Java → AI Agent 垂直定位 | PRD 定位、研究建议、DEC-003 推荐 A | Draft 假设 | 是否接受 DEC-003/007，而不是被首页视觉默认锁定 |
| 题库→面试→报告→复练 | PRD 主流程、DEC-004 推荐 A | Draft 假设 | 是否作为首发闭环和各页面 P0 边界 |
| 练习反馈而非招聘结论 | REQ-08/12、BR-03/04/10、DEC-013 推荐 A | Draft 假设 | 是否显示综合数值 72；低置信时分数如何弱化/隐藏 |
| 级联语音并始终可降级文本 | REQ-05、AC-04/05/06、DEC-008 推荐 A | Draft 假设；PRD 功能表截断 | 语音是否 P0、告知内容、用户确认点、取消/重录和原音删除语义 |
| 转写成功后尽快删除原音 | BR-07、DEC-012/045 推荐 | Draft 假设 | SLA、失败/重试保留、Provider/备份副本和用户可见状态 |
| 主动结束生成部分报告 | FUNC-TEXT-009、REQ-09 | 可见但表现不完整 | 何时生成部分报告、是否消耗额度、未答题怎样显示 |
| 平台失败不扣费，预留后结算 | BR-09、AC-14、DEC-043 推荐 | P1 Draft 假设 | 用户取消、Provider 部分成功、并发会话、价格版本过期如何处理 |
| 内容发布经来源、Golden Set、第二审核 | 产品审查建议、DEC-006 候选、DEC-031/039/040 | Draft 候选 | 这是硬阻断还是提醒；审核角色和紧急下线后的历史报告语义 |
| 删除立即隐藏、异步处理外部/备份 | 产品审查建议、DEC-047 推荐 | Draft 假设 | 强确认、可取消窗口、法定例外、部分失败 SLA 和支持升级 |
| 敏感访问需要 reason code | BR-12、DEC-049 推荐 | Draft 假设 | 允许的 reason、MFA、ticket、时限、审批与用户可见性 |

## 6. 截图索引

| 截图 | 页面/状态 | 本轮视觉核对结论 | 证据边界 |
|---|---|---|---|
| [`01-home.png`](../../../prototype/html-v1/screenshots/01-home.png) | 桌面首页 | 定位、CTA、三段闭环和诚信说明可见 | 只证明静态画面 |
| [`02-questions.png`](../../../prototype/html-v1/screenshots/02-questions.png) | 桌面题库 | 筛选、卡片、状态、来源和导航可见 | 不证明搜索/权限/数据来源 |
| [`03-text-interview.png`](../../../prototype/html-v1/screenshots/03-text-interview.png) | 文本面试 | 单题、回答、追问、进度、暂停/结束入口可见 | 不证明 SSE、预算或恢复 |
| [`04-voice-fallback.png`](../../../prototype/html-v1/screenshots/04-voice-fallback.png) | TTS 失败 | 文本降级与重试入口、语音状态链可见 | 不证明 TTS、持久化或不扣费 |
| [`05-report.png`](../../../prototype/html-v1/screenshots/05-report.png) | 报告总览 | 维度、限制、三项行动和非招聘结论可见 | 分数均为 Mock，不证明质量 |
| [`06-decisions.png`](../../../prototype/html-v1/screenshots/06-decisions.png) | 决策中心 | Pending/推荐/Mock 选择边界可见 | 浏览器选择不是正式决定；图中存在非推荐 Mock 选择 |
| [`07-admin.png`](../../../prototype/html-v1/screenshots/07-admin.png) | Provider 管理 | Mock 指标、降级和最小权限提示可见 | 不证明管理员权限、Provider 或成本 |
| [`08-mobile-home.png`](../../../prototype/html-v1/screenshots/08-mobile-home.png) | 390px 首页 | CTA 和卡片在窄屏可阅读 | 不代表全部页面响应式通过 |
| [`09-mobile-voice.png`](../../../prototype/html-v1/screenshots/09-mobile-voice.png) | 390px 语音页 | 文本入口、结束、问题和权限卡可见 | 不证明移动浏览器麦克风/兼容性 |

## 7. 原型评审记录

| 日期 | 评审人 | 范围 | 反馈 | 采纳决定 | owner / 关闭条件 |
|---|---|---|---|---|---|
| 未执行 | 未指定 | 阶段 4 产品走查 | NotRun | 未形成 | 产品 owner 先关闭 [`review-readiness.md`](review-readiness.md) 中的阻断项并安排实际走查 |

评审结论：**未形成**。不得把本文件、静态原型、截图或 README 中的历史浏览器记录写成“通过”“有条件通过”或用户验收。

## 8. 回流与下一步

1. 由 PRD owner 恢复 `FUNC-VOICE-002` 后缺失正文并修订 Canonical PRD；本原型不得猜写为原文。
2. 由用户关闭 Gate A 产品决定，至少确定定位、核心闭环、语音、评分措辞、账号/匿名、内容后台、音频默认策略和阶段 4 范围。
3. 按 [`state-matrix.md`](state-matrix.md) 补齐或明确裁剪关键页面的专用状态，不能只依赖全局状态横幅。
4. 产品 owner 以可点击原型实际逐场景走查，记录“通过 / 有条件通过 / 不通过”、反馈、owner 和关闭条件。
5. 只有 Canonical Spec 和适用原型均获批准，才可把产品交互作为技术设计和 `tasks.md` 的输入；本资料包本身不授权编码、测试、构建、服务、外部调用、部署或 Git。
