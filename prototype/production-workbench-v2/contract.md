# 生产排程 V2 本地实现契约

> 类型：原型技术参考；版本：2.0.0；更新：2026-09-11；维护：Codex  
> 上游：[PRD](../../文档/项目/生产排产项目/产品/产品需求文档.md)；实现：[engine.js](engine.js)；证据：[验证记录](verification.md)

本文件描述本轮实际 JavaScript 模块间契约，不是生产服务接口。普通 script 本地加载，无后端或本地 JSON fetch。HTML 加载顺序见 [index.html](index.html)，共享 UI 位于 `../business-suite-v1/shared/`。

## 1. 模块边界

| 文件 | 责任 |
| --- | --- |
| `engine.js` | 示例数据、候选求解、独立校验、工时 / 数量 / 交期、发布、实际生产与质量处置 |
| `app.js` | `window.A`、导航、三甘特、任务调整、锁定撤销、发布审查、本地保存与旧副本保护 |
| `catalog.js` | 多产品订单、工艺图与参数、依赖、人员设备日历、批次 |
| `operations.js` | 执行、日报、累计产能、版本比较、审计与导出 |

## 2. 状态和时间

`window.Engine` 为引擎，`window.A.state` 为应用唯一状态。时间值是从 2026-09-14 08:00 起算的整数分钟，次日 08:00 为 1440；日期转换固定用于演示业务时间。区间采用 `[start,end)`，资源占用以 `segments` 为准。

核心状态包括 `schema:2`、基础数据 `revision`、模拟生产时间 `now`、`orders`、`tasks`、`resources`、`batches`、`draft`、`published`、`versions`、`events`、`operationRecords`。

- `draft.basedOn` 是正式版本 ID 字符串，例如 `V01`，不是数字；`draft.revision` 是生成候选时的数据修订号。
- `published` / `versions` 保留分配快照及发布说明；新发布版本还保存 `taskSpecs` / `batchSpecs` 工艺与批次快照，开工和恢复前复验。
- `_writeToken` 标识一次本地保存。`A.commit()` 保存前核对存储中的 token，旧副本不能覆盖；这不是服务端事务锁。
- `order.lines` 包含产品、需求量、单位及工艺来源；`task` 以 `orderId` / `lineId` 关联订单产品实例。
- 工艺字段包括 `mode`、`skill` / `minLevel`、可选 `machines`、`rate`、`setup` / `run` / `unload`、`interruptible`、`deps` 和 `batchId`。
- 依赖 `type` 为 `finish` 或 `quantity`；数量依赖可使用绝对门槛或比例。消耗型转移批另用 `consume`、`ratio`、`transferQty`，不与非消耗时序门槛混算。
- 执行字段包含 `status`、`good`、`bad`、`reported`、`activity`、`releases`、`lastActionAt`；`activity` 记录实际开停区间，`releases` 记录实际数量放行。
- 质量恢复用 `disposedBad`、`recoveredGood`、`dispositionCount`、`recoveryChainId`、`recoveryOf` 等追溯来源。原 `good` 不被返工回记覆盖；最终交付量避免原任务与恢复链重复计算。
- 新订单只复制正常工艺字段，不复制恢复链或任何执行事实。已执行任务的工艺不能经普通表单修改；日历修改不得改变已发生资源活动的可用性。

`resource` 以 `id` 标识具体人员 / 设备，包含 `skills`、`available`、`unavailable`、工作中心及车间归属。`assignment` 包含 `taskId`、`start`、`end`、`personId`、`machineId`、`batchId` 和 `segments`；每段含起止、`kind`、阶段资源及数量。

共享批成员可以具有相同分段，冲突检查和报表按批次、资源、阶段时段去重。自动运行段 `personId=null`，纯人工 `machineId=null`；车间不是额外重复扣减的资源池。

## 3. 引擎入口

| API | 行为 |
| --- | --- |
| `Engine.createState()` | 生成 4 订单 / 17 任务 / 9 人 / 4 设备的独立示例状态 |
| `Engine.plan(state, options={})` | 返回候选 assignments 与 issues，不修改正式计划；正向逐任务有限候选选择 |
| `Engine.validate(state, assignments)` | 返回问题数组；校验数据、依赖数量、技能、日历、容量、冲突、锁定和执行限制 |
| `Engine.metrics(state, assignments)` | 返回订单完成估算与原因、人机时及未排任务；无法可靠估算用 end:null |
| `Engine.adjust(state, taskId, changes)` | 生成调整后的分配及问题；共享批整体调整，变更只形成候选 |
| `Engine.publish(state, note)` | 返回 {ok,message}；成功时保存正式版本；失败不修改正式计划；复验基线、数据版本和硬约束 |
| `Engine.report(state, taskId, action, values={})` | start / pause / resume / report；只执行正式任务，复验实际前置与资源；记录开停和数量事件 |
| `Engine.actualProgress(state, task, at=state.now)` | 根据实际活动、日历和放行计算进度，返回 actualSegments 供日报统一使用 |
| `Engine.dispose(state, taskId, kind, qty, reason)` | kind='rework' 或 'scrap'；形成可追溯返工 / 补产任务，须重排发布后再执行 |
| `Engine.occupied` / `productionAt` / `releaseAt` / `duration` | 共享占用、计划产出、释放和时长计算工具；详细参数见引擎定义 |

issue 包含 code、message、severity，可关联 taskId / resourceId。报工失败不能推进数量；页面设置模拟时间后调用引擎，失败时恢复先前时间。实际逾期、暂停和不良未处置不能被旧计划结束时间掩盖。

## 4. 页面入口

- `A.commit(message)` 保存并重绘，返回是否成功；`A.render()` 仅重绘；`A.go(view)` 导航；`A.task(id)` 开任务抽屉。
- `A.e` 转义动态文本，`A.fmt` / `A.time` / `A.hours` 格式化；`A.btn` / `A.badge` / `A.table` / `A.empty` 统一基础控件；`A.csv` 本地导出。
- `A.audit(type,message,taskId)` 记录事件，不自动保存；`Views[view]` 返回页面；`Actions[action]` 处理 data-action；`PageEvents[view]` 绑定重绘后的输入事件。
- 订单、工艺、资源和批次更改递增基础数据 revision，使旧候选需要重排；实际报工使用独立流水，不以页面上的负责人标签充当权限。

所有页面使用同一状态；浏览器表单校验只承担原型交互，不能替代未来服务端鉴权和约束检查。
