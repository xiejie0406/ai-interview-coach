# 生产排程 V2 验证与交付记录

> 对象：PROTO-PS-002 / 2.0.0 工作区原型；日期：2026-09-11（Asia/Shanghai）  
> 执行：Codex；独立领域复核：协作 Agent；数据：内置示例 / 隔离测试数据  
> 上游：[PRD 与 AC 唯一正文](../../文档/项目/生产排产项目/产品/产品需求文档.md)、[研究来源](../../文档/项目/生产排产项目/调研/工序与资源调研.md)、[原型说明](README.md)

本记录只把实际执行的断言标为 Pass。原型已有可操作闭环，产品 PRD 中仍有未实现或未验证要求；**不据此宣布全部 P0 验收通过或生产上线就绪**。用户验收没有发生，未代签。

## 1. 环境与范围

- Windows 本地文件，原型由 file URL 加载，不启动服务，不读取用户浏览器登录态。
- Node.js 使用工作区附带运行时；Playwright 使用同一附带依赖；headless Chrome 152.0.7977.83，隔离 context，中文 locale、Asia/Shanghai、reduced motion。
- 主要视口 1440×1000、390×844，补充平板 / 横屏；12 个页面、三甘特、表单、抽屉、CSV、打印媒体、localStorage 与两个隔离测试标签页。
- 引擎在 Node 中加载同一原型源码与本地 solver；脚本检查输出、浏览器控制台及业务状态。部分边界先构造隔离数据，再通过真实 click / fill 走页面，不把注入 fixture 称为生产集成。
- JSON 时间戳为 UTC，转换为本地时间均为 2026-09-11；所有业务日期为示例的 9/14–9/18。

## 2. 实际结果

各组为不同层次的断言，包含交叉覆盖，不将相加结果宣传为独立业务需求数量。

| EV | 范围 / 脚本 | 实际结果 | 机器证据 |
| --- | --- | --- | --- |
| EV-01 | 引擎：有限资源、门槛 / 消耗、合批、锁定、发布、实际开停、返工补产、正式工艺一致性；engine-check.cjs | 39 Pass，0 Fail | [引擎结果](output/playwright/engine-results.json) |
| EV-02 | 初轮 12 页桌面 / 窄屏、三甘特、调整、发布、CSV / 打印媒体；verify.cjs | 37 Pass，0 Fail | [页面结果](output/playwright/browser-results.json) |
| EV-03 | 撤销恢复锁定、解除锁定、跨标签旧副本保护、最终工具栏 / 抽屉；verify-workbench.cjs | 7 Pass，0 Fail | [工作台结果](output/playwright/workbench-results.json) |
| EV-04 | 多产品订单、工艺参数 / 依赖、技能、请假、维修、合拆批、手机表单；verify-catalog.cjs | 13 Pass，0 Fail | [主数据结果](output/playwright/catalog-results.json) |
| EV-05 | 共享批同层与跨层依赖图；同脚本 --graph-only | 2 Pass，0 Fail | [图关系结果](output/playwright/catalog-graph-results.json) |
| EV-06 | 执行过返工 / 补产的订单复制只保留正常工艺；同脚本 --copy-only | 2 Pass，0 Fail | [复制结果](output/playwright/catalog-copy-results.json) |
| EV-07 | 历史班次 / 请假 / 维修不能改写工时，未来例外仍可保存；同脚本 --calendar-history-only | 2 Pass，0 Fail | [日历历史结果](output/playwright/catalog-calendar-history-results.json) |
| EV-08 | 正式现场开停、50 件释放、同炉成员、返工发布执行、日报 / 累计表、版本恢复 / CSV / 刷新；verify-operations.cjs | 16 Pass，0 Fail；新增正式一致性保护后再次完整通过本组 | [现场结果](output/playwright/operations-results.json) |
| EV-09 | 合批 / 转移段数量标签、订单入口、执行图标、最终截图、三项目入口；verify-final.cjs | 7 Pass，0 Fail | [最终差异结果](output/playwright/final-results.json) |
| EV-10 | 本地 JS/CJS 语法、V2及上游文档链接、HTML资产、REQ/BR/AC唯一编号 | 19 Pass，0 Fail；仓库文档脚本另检查174份Markdown通过 | [静态结果](output/playwright/static-results.json) |

原型源码在最后修改后做定向验证；没有因文案收尾无目的重复所有组。最终实际现场组因引擎新增开工保护而重跑，确认正常生产与返工流程未被误阻。

## 3. 可复算的核对值

| 对象 | 预期与实际一致的数值 | 证据 |
| --- | --- | --- |
| O-100 初始计划 | A10 08–12，B10 10–12；H1 13–16；A30 当日16–17，B30 次日08–09；整单 9/15 09:00，延期16自然小时 | EV-01、EV-02 |
| H1 人机占用 | 180 机分钟、60 人分钟；设备运行条显示 A100+B100=200件，成员各自回记 | EV-01、EV-08、EV-09 |
| T20 消耗转移批 | [60,90]、[120,150]、[180,210]，每段20件；第二批未放行不能提前消耗 | EV-01、EV-09 |
| 返工闭环 | A30 原合格98、不良2，返工新任务2件经 V02 发布实际执行；原 good 仍98，recoveredGood=2，整单最终200 | EV-08 |
| 日报跨日 | 9/14 末道合格98件、480人分钟、540机分钟；9/15 末道合格102件（含返工2件），无重复 | EV-08 |
| 人力累计 | 9人、5个工作日共21600分钟=360人时；10个半日桶求和一致，多技能不翻倍 | EV-08 |
| 历史日历 | H1完成后补填王工历史请假、缩短历史班次或F1历史维修被拒；次日培训允许，仍保持60人分钟/180机分钟 | EV-07 |

## 4. PRD 验收覆盖

下表“已执行”仅表示相应局部证据 Pass；右栏任何 NotRun 都意味着**整条 AC 不能作为生产验收 Pass**。原型中的阻断策略是当前能力边界，不是对 PRD 的静默删减。

| AC | 已执行的原型证据 | 尚未覆盖 / 与完整验收的差距 |
| --- | --- | --- |
| AC-01 | EV-04、EV-06：多产品独立展开、数量工时重算、来源可追，新单无执行污染 | 任意计量单位及原文3工序/2工序所有组合 NotRun |
| AC-02 | EV-01、EV-04：缺项 / 循环拒绝；EV-06：源单不变 | 正式工艺主版本发布与迁移确认未实现，NotRun |
| AC-03 | EV-01、EV-04：前置 / 延迟校验与过早调整拒绝，依赖图可编辑 | 原文双并行支路10/11点加30分钟的完整输入未单列实跑，NotRun |
| AC-04 | EV-01、EV-08：实际50件门槛，时间不代替报工；EV-04：比例门槛配置 | 101件比例向上取整、独立待检51件场景 NotRun；原型未建独立待检池 |
| AC-05 | EV-01、EV-09：60件分20件转移、逐批消费、晚释放等待、防超预留 | 非整尾批、多物料换算及复杂工艺消耗组合 NotRun，受限组合显式阻断 |
| AC-06 | EV-01、EV-04、EV-08：兼容 / 超容量拒绝、同炉去重及成员回记 | 任意部分数量装炉未实现；原文60+40和2小时外的行业参数组合 NotRun |
| AC-07 | 无独立同步组执行证据 | 任意同起同止同步组未实现，NotRun；共享炉批不能替代此 AC |
| AC-08 | EV-01、EV-04：技能不足不匹配、请假顺延、可用技能资源改派 | 原文甲乙丙三人组合及技能到期场景未完整实跑，NotRun |
| AC-09 | EV-01：王工自动阶段可另派，装卸冲突拒绝；EV-08：实际3机时/1人时 | 本地样例 Pass；真实设备自动运行和实际班次接口 NotRun |
| AC-10 | EV-01、EV-08：具体人员重复占用拒绝，多技能累计去重 | 两个必需协作席位、跨中心借调全流程未实现，NotRun |
| AC-11 | EV-01：不可中断不拼午休；可中断拆合法段；实际暂停不产出 | 恢复准备、跨班交接与不同设备保留策略全组合 NotRun |
| AC-12 | EV-01：正排人机无重叠、无合法窗口明确未知 | 倒排未实现，NotRun，不宣称正倒排均通过 |
| AC-13 | EV-01、EV-03：下游顺延、共享批整体校验、取消 / 撤销保留基线；EV-08：版本差异 | 任意跨车间复杂网络的全部影响完整性 NotRun |
| AC-14 | EV-01：时间锁 / 资源锁 / 全锁、故障阻止发布；EV-03：锁定撤销恢复 | 本地各锁类型 Pass；真实冻结权限与故障源 NotRun |
| AC-15 | EV-01：旧正式基线拒绝；EV-03：双标签旧保存拒绝 | 真实多用户原子发布、接收端失败和重试幂等未实现，NotRun |
| AC-16 | EV-02、EV-08、EV-09：三视角、人员阶段、共享批去重与数量标签 | 本地样例 Pass；真实资源数据接入 NotRun |
| AC-17 | EV-01：重复流水和超量拒绝；EV-08：实际合格释放及审计 | 独立待检池、现场换人、错误报工更正审批未实现，NotRun |
| AC-18 | EV-01：末端返工、报废整链补产执行及回记；EV-08：2件返工完整UI闭环 | 10件隔离分6返工/4报废、非末端多分支及复杂消耗恢复组合 NotRun |
| AC-19 | EV-01、EV-08：标准样例预计完成、A完成不关闭整单、B齐套后完成，返工后200件不重计 | 本地样例 Pass；外部质量 / 入库 / 出货里程碑 NotRun |
| AC-20 | EV-08：跨日实际段和释放数量、日基线、返工日归属 | 历史工艺主版本迁移后日报重算、所有跨日批次组合 NotRun |
| AC-21 | EV-08：9人5日与半日累计守恒、技能去重；EV-01：并发冲突 | 原文日总富余但下午峰值缺口的全表展示及多席位缺口 NotRun |
| AC-22 | EV-01：无合法窗口、不良未处置或实际逾期时交期未知且有原因 | 本地未知语义 Pass；真实数据延迟 / 缺工时大数据组合 NotRun |
| AC-23 | EV-01：数据revision变化阻止旧候选发布；EV-03：正式基线保持 | 求解任务取消 / 超时、异步求解期间事件竞争未完整实现，NotRun |
| AC-24 | 页面数据转义、本地审计和筛选导出已实现，EV-02、EV-08验证相关导出 | 服务端车间权限、来源订单去重同步、接口中断恢复均未实现，NotRun |

## 5. 复核发现及修复

| 发现 | 修改与恢复语义 | 复验 |
| --- | --- | --- |
| 工具栏控件在窄容器被裁切 | 允许工具组按容器换行，时间轴仍局部滚动 | EV-03、EV-09及最终截图 |
| 工艺图长依赖边穿过工序节点 | 长边绕行，共享批成员保持同层 | EV-05及工艺图截图 |
| 合批设备条显示代表成员数量、转移段显示整任务量 | 合批显示成员总量，转移段显示段数量，车间整任务仍显示任务量 | EV-09 |
| 工作台订单交期按钮 action 不对应实际入口 | 统一为已注册的订单详情 action | EV-09 |
| 复制源单带入实际字段及恢复链任务 | 过滤派生任务，以工艺白名单构造新单，执行状态重新初始化 | EV-06 |
| 拆批 / 工艺修改后现场混用新主数据与旧正式安排 | 发布保存工艺 / 批快照；开工和恢复复验归属、成员和参数；不一致拒绝 | EV-01新增5场景，EV-08重新通过 |
| 日历编辑改变已发生的人机时 | 对已记录活动窗口比较新旧可用性，历史变更拒绝，未来无活动窗口允许 | EV-07 |
| 密集测试提示影响截图阅读 | V2限制同时提示数量，最终截图等待提示自然消失；未修改共享V1样式 | EV-09与人工视觉核对 |

最终入口测试第一次在 hashchange 生效前立即断言，产生1项 Fail。保留 [首次记录](output/playwright/final-results-first.json)，补充等待目标 iframe 路由条件后复验7项 Pass；这是测试同步问题，未修改其他两个项目业务入口。其余修复以表中实际复验结果为准。

编号静态核查首次只匹配行首 BR，漏读表格和列表中的定义，得到15/48。保留 [首次静态结果](output/playwright/static-results-first.json)；扩展解析为文档实际三种格式后重新运行，48条均唯一，静态19项全部通过；未为检查而改写业务规则。

## 6. 复现命令

工作目录为仓库根。所有脚本只创建隔离上下文和本地证据文件，无真实业务写入。以下使用本次实际运行时路径；其他机器可替换为已有 Node 与 Playwright 路径。

```powershell
$prototypeNode = 'C:/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
& $prototypeNode prototype/production-workbench-v2/engine-check.cjs --output prototype/production-workbench-v2/output/playwright/engine-results.json
& $prototypeNode prototype/production-workbench-v2/verify.cjs
& $prototypeNode prototype/production-workbench-v2/verify-workbench.cjs
& $prototypeNode prototype/production-workbench-v2/verify-catalog.cjs
& $prototypeNode prototype/production-workbench-v2/verify-catalog.cjs --graph-only
& $prototypeNode prototype/production-workbench-v2/verify-catalog.cjs --copy-only
& $prototypeNode prototype/production-workbench-v2/verify-catalog.cjs --calendar-history-only
& $prototypeNode prototype/production-workbench-v2/verify-operations.cjs
& $prototypeNode prototype/production-workbench-v2/verify-final.cjs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-docs.ps1
```

前9条对应本轮已运行的组，最终退出码均为0。文档与链接检查独立记录，不据其通过推断产品行为。

## 7. 截图与交付限制

已人工核对 [桌面工作台](output/playwright/workbench-final-1440.png)、[窄屏工作台](output/playwright/workbench-final-390.png)、[人员甘特](output/playwright/workbench-person-final.png)、[工艺图](output/playwright/catalog-routes-final.png)、[返工后日报](output/playwright/operations-daily-recovery.png)、[半日累计产能](output/playwright/operations-capacity-half-days.png)。页面没有整体横向溢出；表格、工艺图和时间轴允许局部滚动。打印媒体检查不代表实体打印或全部纸张分页通过。

没有执行生产接口、权限 / 安全、真实负载、跨浏览器与读屏器全矩阵、现场用户验收。引擎是有限窗口的串行候选选择，不保证全局最优；倒排、多席位、任意同步、复杂消耗恢复等差距见上表和 README。原始 Word、旧排程 V1、已有正式业务系统均保留；未进行 Git 暂存、提交或发布。
