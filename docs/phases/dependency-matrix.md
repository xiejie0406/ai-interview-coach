# 依赖、覆盖与 owner 矩阵

> 文档类型：Phase 依赖审查  
> 文档状态：Draft  
> owner / 责任边界：架构计划 owner 维护映射；Feature `tasks.md` 才能维护可执行任务  
> 创建时间：2026-08-02  
> 更新时间：2026-08-02  
> Roadmap ID：ROADMAP-INTERVIEW-001  
> 风险等级：L3  
> 产出/适用阶段：5–6 前瞻输入  
> 阶段状态：WaitingForApproval

## 1. 期次依赖

| Phase | 直接前置 | 可并行边界 | 退出后解锁 |
|---|---|---|---|
| 01 | Gate A、Approved 上游（实施前） | 仅文档/契约设计可与 02 准备并行 | 02–04 |
| 02 | 01、DEC-034–040/真实调用授权 | 可与 03/04 的纯本地纵切并行，不共享供应商决定 | 06、12、13 |
| 03 | 01、DEC-041/042/029 | 可与 04 并行，schema owner 不重叠 | 05、07、08、15 |
| 04 | 01、DEC-007/015/040 | 可与 03 并行 | 05、06、07、10 |
| 05 | 03、04 | 无 | 06、11 |
| 06 | 02、04、05、DEC-034/038/039 | 无 | 09、10 |
| 07 | 03、04、DEC-010/043 | 可与 06 后半并行，需冻结共享 API | 08、09、16 |
| 08 | 03、07，批准的状态/恢复语义 | 无 | 09、10、12、14、15 |
| 09 | 06、07、08 | 无 | 10、14 |
| 10 | 04、06、08、09 | 无 | 11、18 |
| 11 | 05、10 | 可与 12 前端设备实验并行 | 18 |
| 12 | 02、03、08、DEC-035/045/048 | 无 | 13、15 |
| 13 | 02、06、09、12、DEC-036 | 无 | 14、18 |
| 14 | 08、09、13、DEC-025/051/057/058 | 无 | 15–18 |
| 15 | 03、08、12、14、DEC-045–050 | 无 | 16–18 |
| 16 | 07、14、15、DEC-043/044 | 无；真实支付需独立授权 | 17、18 |
| 17 | 14–16、DEC-051–058 | 无；部署仍独立授权 | 18 |
| 18 | 01–17 范围内实现与局部 EV | 无 | 可能形成 ReleaseReady，不含发布 |

依赖图无循环。Gate B 只在对应 Phase 前成为阻断项；Gate C 范围不在图内。

## 2. REQ/BR/AC 覆盖

| 上游 | 主覆盖 Phase | 补充 Phase | 说明 |
|---|---|---|---|
| REQ-01 / AC-01 | 04、05 | 11、18 | 题库筛选、练习与复练 |
| REQ-02 / AC-11 | 04 | 10、15、18 | 内容/Rubric 版本、发布审计 |
| REQ-03 / AC-03 | 07、08、09 | 18 | 计划、状态、文本面试 |
| REQ-04 | 02、06、09 | 10、18 | 有限追问与确定性预算 |
| REQ-05 / AC-04 / AC-05 / AC-06 | 02、12、13 | 14、15、18 | 语音、拒绝、降级和真实质量 |
| REQ-06 | 04、05、08 | 10、12 | 题目/Rubric/回答/转写/轮次版本 |
| REQ-07 / AC-02 / AC-07 | 02、06、10 | 11、18 | 证据评测和报告 |
| REQ-08 / AC-08 | 02、06、10 | 11、17、18 | 低置信、纠错、质量面板 |
| REQ-09 / AC-09 | 08、09 | 14、18 | 暂停/恢复/故障 |
| REQ-10 | 05、11 | 18 | 弱项、复练、趋势 |
| REQ-11 / AC-10 | 03、12、15 | 17、18 | 同意、权限、保留、导出和删除 |
| REQ-12 / AC-12 | 03、09 | 15、18 | 练习边界、拒绝隐蔽辅助 |
| REQ-13 / AC-13 | 当前 18 期不实现 | Gate C 后新 Feature | P1 简历/JD 保持延后，避免猜写截断范围 |
| REQ-14 / AC-14 | 07、16 | 14、17、18 | 权益、预留、支付和成本 |
| REQ-15 | 02、06、12、13 | 14、17 | Provider SPI、路由、监控和开关 |
| BR-01–04 | 04、06–10 | 18 | 版本、单题、判定和可解释性 |
| BR-05–08 | 03、06、10、12、15 | 18 | 表达边界、同意、音频、材料事实 |
| BR-09 | 07、14、16 | 18 | 会话前预估、失败释放和不重复扣减 |
| BR-10–12 | 03、04、09、15 | 18 | 反作弊、内容版本、敏感访问 |

全部当前 P0 `REQ-01–12` 和 `AC-01–12` 均有主 owner/Phase。P1 `REQ-13/AC-13` 明确延后；`REQ-14/15` 作为 T2/技术横切覆盖，不被误写成 P0 已批准。

## 3. 模块、页面、数据 owner

| 模块 | 后端 owner | 前端页面/feature owner | schema owner | 首次 Phase |
|---|---|---|---|---|
| identity | identity | `/auth/*`、`/app/settings/account` | `identity` | 03 |
| catalog | catalog | `/questions/*`、`/admin/catalog/*` | `catalog` | 04 |
| practice | practice | `/practice/*`、题目作答区 | `practice` | 05 |
| agent | agent/integration | 无独立页面；内部质量页由 operations | `agent` | 02/06 |
| billing | billing | `/app/usage`、`/pricing`、`/checkout` | `billing` | 07/16 |
| interview | interview | `/app/interviews/new`、`/app/interviews/:id` | `interview` | 07/08 |
| evaluation | evaluation | `/app/reports/:id`、纠错组件 | `evaluation` | 06/10 |
| learning | learning | `/app/learning`、Dashboard | `learning` | 11 |
| voice | voice | interview 下 `voice/*` | `voice` | 12 |
| governance | governance/security | `/app/settings/privacy`、`/admin/audit` | `governance` | 03/15 |
| operations | operations | `/admin/operations/*` | `operations` | 14/17 |
| platform | platform | 全局错误/恢复 banner | `platform` | 01/08 |

## 4. 外部系统 owner

| 外部系统 | 业务 owner | Adapter owner | 首次真实接入门 | 降级/停止 |
|---|---|---|---|---|
| LLM | agent/evaluation | integration | 02 benchmark、06 产品 Adapter；DEC-034/038/048 | fake、备 Provider、受控失败；数据条款不满足即停 |
| ASR | voice | integration | 02 benchmark、12 产品 Adapter；DEC-035/045/048 | 文本输入；低置信需确认 |
| TTS | voice | integration | 02 benchmark、13 产品 Adapter；DEC-036/048 | 同内容文本；不阻断会话 |
| Object Storage | governance/voice | integration | 12；DEC-026/045 | 本地 fake 仅开发；删除失败为 PARTIAL_FAILED |
| Redis | platform | adapters/operations | 14；DEC-025 | PostgreSQL 为事实源，Redis 丢失可重建 |
| Payment | billing | integration | 16；DEC-044 | 测试权益/邀请码；禁止未决真实收费 |
| Email/SMS/OAuth | identity | integration | 03；DEC-041 | fake 或单一批准渠道 |
| Monitoring/OTel exporter | operations | platform | 17；DEC-056 | 本地标准输出/测试 exporter；不得采敏感正文 |

## 5. 断裂检查

- 没有 Phase 依赖未来 Phase 的实现。
- 12/13 的语音都复用 08 的状态/Job、09 的 Agent 闭环和 07 的用量 port，不建立平行会话事实。
- 16 的支付只把订单结果映射为既有 Entitlement，不重写用量/成本模型。
- 18 只汇总证据链接，不复制 Feature `verification.md`/`acceptance.md` 的事实正文。
