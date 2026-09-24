# APS 排程引擎技术调研

> 文档类型：技术专项调研；编号：RES-APS-ENGINE-001；版本：1.0.0 Draft  
> 调研 / 更新日期：2026-09-11；owner：用户；调研整理：Codex  
> 调研问题：资源和时间由什么技术计算，人员、设备、班次与订单交期如何进入同一套有限产能排程  
> 上游：[市场与竞品调研](市场与竞品调研.md)、[工序与资源调研](工序与资源调研.md)  
> 下游：[APS 排程引擎需求与技术设计](../架构/APS排程引擎需求与技术设计.md)  
> 边界：公开官方资料与现有需求、数据库设计的技术分析；未安装求解器、未使用真实工厂数据跑基准，也不是厂商产品实测

## 1. 结论先行

本项目首选 **Google OR-Tools CP-SAT 9.15 的 Java API** 作为 APS 求解内核，采用“Spring Boot 排程编排服务 + 独立 Java 求解 Worker + MySQL 计划版本库 + 独立约束校验器”的结构。

选择它的原因不是“开源所以免费”，而是本项目最难的部分正好是 CP-SAT 能直接表达的组合约束：工序时间区间、前后置、候选人员与设备、独占资源、累计容量、人机同步、锁定、延期和多目标优化。OR-Tools 官方 Java API 提供 `IntervalVar`、可选区间、`NoOverlap`、`Cumulative`、求解时限、回调、提示解和不可行假设等基础能力。

同时明确四个边界：

1. OR-Tools 是优化工具箱，不是开箱即用的 APS 产品；工艺、日历、技能、数量、质量、版本、发布和解释层仍需本项目实现。
2. MySQL 保存权威事实、输入快照和计划版本，但不负责在 SQL 中搜索排程组合。
3. DHTMLX Gantt Community 与 `vis-timeline` 只展示结果并提交调整意图，不计算正式排程。
4. 求解超时后得到的可行方案不是全局最优；没有找到方案也不自动等于已经证明无解。

## 2. 行业内是怎样做的

成熟 APS 通常不是“一次点击得到一张甘特图”，而是一条受控闭环：

```text
业务事实刷新
→ 数据就绪检查
→ 订单与工艺展开
→ 候选资源裁剪
→ 启发式或优化求解
→ 独立可行性复验
→ 场景比较与人工修复
→ 正式发布
→ 现场报工、故障和质量反馈
→ 影响分析与局部重排
```

公开资料中可以确认以下共性：

- SAP PP/DS 同时提供启发式、详细排程、优化和交互式计划板；资源及组件可用性参与详细排程，固定工序可以排除在重新调度范围外。
- Siemens Opcenter Scheduling 以订单为基础处理多约束、资源可用性、不同资源加工速度、顺序相关换型、工序重叠和优化规则。
- 专业 APS 普遍把正式计划与 What-if 场景分开，把冻结、修复、发布和异常告警作为一等能力。
- 算法结果必须结合现场执行事实滚动更新；自动重排不应直接覆盖已执行任务和当前正式计划。

这些产品资料证明了行业方法，不证明某个产品与本 PRD 的所有细节完全一致。具体的半批转序、共享炉批、具名人员效率和跨产品依赖仍需用统一样例做 PoC。

## 3. 求解框架比较

| 维度 | OR-Tools CP-SAT | Timefold Solver Community | Choco Solver | 纯自研规则 / SQL |
| --- | --- | --- | --- | --- |
| 许可证 | Apache-2.0 | Community 为 Apache-2.0；Enterprise 另行商业许可 | BSD-3-Clause | 自有代码，但维护成本全部自担 |
| 技术形态 | C++ 内核，提供 Java/JNI 与 Maven 包 | 纯 Java / Kotlin，POJO 与 Constraint Streams | 纯 Java 底层约束规划库 | Java 规则、存储过程或手工排序 |
| 时间区间与替代资源 | 原生区间、可选区间、`NoOverlap`、`Cumulative` | 能建模，通常要设计规划实体、变量、阴影变量和评分 | 能建模 Task/Cumulative，领域层需自建 | 小规模派工可做，组合约束快速失控 |
| 制造排程贴合度 | 高，适合柔性 Job Shop、人机同步、共享资源和锁定 | 中高，规则频繁变化、人员排班、可解释评分较友好 | 中，底层能力够但制造排程生态较少 | 低，不适合证明复杂有限产能可行性 |
| 动态重排 | 重建模型、固定约束、旧计划 `addHint`，应用层自建 | Problem Change、pinning、非扰动重排能力较完整 | 可自定义搜索和 warm start | 逻辑容易散落，难保证全局一致 |
| 解释能力 | 返回状态、目标、bound、日志与不可行假设集合；业务解释需自建 | Score Analysis 与 justification 较强 | 有冲突解释能力，业务映射仍需自建 | 可以写原因，但很难证明没有漏约束 |
| Spring 集成 | 官方 Java 包；建议隔离原生库和 CPU 任务 | 原生 Starter；当前 2.x 需 Java 21 / Spring Boot 4 | 无官方 Spring 领域集成 | 最容易起步，长期风险最高 |
| 本项目结论 | **主推荐，先做真实数据 PoC** | 对照 PoC / 退出备选，不与主引擎同时生产运行 | 禁止 JNI 时的后备研究项 | 仅用于候选裁剪和快速派工，不作唯一正式求解器 |

### 3.1 为什么不一期同时上两个求解器

同一条业务规则分别在 CP-SAT 与另一套评分模型中实现，会产生约束漂移：一个引擎认为可行，另一个认为冲突；修复和解释也会出现两套口径。首版只维护一套正式模型和一套独立确定性校验器。Timefold 只在 OR-Tools PoC 未达到建模、性能或运维门槛时，用相同数据集做替代验证。

### 3.2 为什么不用甘特图或 MySQL 直接排

- 甘特组件知道屏幕上的条和资源行，不知道真实技能有效期、质量放行、数量守恒、共享批容量及发布水位。
- MySQL 擅长一致性、检索和事务，不擅长在海量可选人员、设备和时间组合中搜索最优解。
- 纯规则正排适合快速生成初始候选，但遇到交期竞争、替代资源、多技能员工和换型权衡时，容易陷入局部选择。

因此前端、数据库、启发式和优化器各有职责，不能由其中一个越界代替全部系统。

## 4. OR-Tools 能力与本项目需求映射

| 本项目问题 | CP-SAT 建模基础 | 仍需自研的业务逻辑 |
| --- | --- | --- |
| 准备、运行、卸料 | 每阶段建立开始、时长、结束及 `IntervalVar` | 阶段拆分、工时来源、暂停及恢复规则 |
| 一个人或一台设备不能同时做两件事 | 每个独占资源一组 `NoOverlap` | 候选资格、实际占用、跨版本发布复验 |
| 多台兼容设备 / 多个合格人员选一个 | 每个候选建立 `OptionalIntervalVar`，presence 布尔量满足 `ExactlyOne` | 技能、中心资格、有效期和日历预筛 |
| 班组人数、炉位、工位或电力上限 | `Cumulative(interval, demand, capacity)` | 容量单位、窗口变化与业务解释 |
| 工序先后 | 线性不等式连接前后阶段 | FINISH、数量门槛、转移批与物料释放语义 |
| 人机同时参与 | 人员和设备可选区间共享同一开始 / 结束变量 | 哪个阶段需要人，自动运行是否释放人员 |
| 请假、午休、维修、已执行任务 | 固定占用区间或限定可用域 | 日历合并、审批和更正追溯 |
| 时间锁 / 资源锁 | 固定开始结束变量；指定 presence 为 1 | 锁范围、权限、现实冲突和解锁流程 |
| 交期 | 完工最大值、延期非负变量和整数目标 | 订单承诺来源、产品行合格完成口径 |
| 局部重排 | 闭包外任务固定；旧解 `addHint` | 影响闭包、版本差异、人工计划保护 |
| 无法排程 | assumption literal 与 infeasible core 可辅助定位 | 原因码、对象名称、替代窗口和修复建议 |

`addHint` 只帮助搜索从旧计划附近开始，不是锁定；真正锁定必须变成等式或固定资源选择。不可行假设集合也不保证天然给出完整、最小且业务可读的根因，仍需独立诊断层。

## 5. 推荐的求解逻辑

### 5.1 时间基准

数据库继续保存 UTC `datetime(3)` 与秒数。求解时以 `horizon_start` 为零点，把所有时间转为统一整数刻度。首版默认一分钟一格；小于一分钟且确实影响可行性的工艺，才切换到秒级模型。一个求解任务中禁止混用刻度。

CP-SAT 只处理整数。数量、效率和比率应先按计量单位量子换算成整数，不允许用浮点近似偷偷释放多余数量。

### 5.2 候选资源裁剪

在创建可选区间前，先按以下顺序过滤：

1. 资源类型与阶段角色一致。
2. 资源属于或获准借调到目标工作中心。
3. 所有必需技能在计划时点有效且达到等级。
4. 设备、工装、批次兼容键和容量单位兼容。
5. 求解窗口内至少存在可能容纳该阶段的可用窗口。
6. 已执行占用、全锁和明确禁用资源直接排除。

候选过多会按“任务 × 人员 × 设备 × 时间”放大模型，因此裁剪是性能设计，不是放宽硬约束。

### 5.3 分层目标

不采用一个随意堆叠巨大权重的目标。建议按业务优先级分层求解：

1. 先得到满足全部硬约束、覆盖详细计划范围内必需任务的方案。
2. 最小化延期订单数及按订单优先级加权的延期时长。
3. 在不恶化上一层结果的前提下，最小化相对正式计划的时间、人员和设备扰动。
4. 再减少换型、加班、借调、等待和在制时间。
5. 最后改善完工总历时和负荷均衡。

设备利用率不能排在准时交付和计划稳定性之前。把设备排满可能制造更多在制品、延误高优先级订单，也可能造成下游拥堵。

## 6. 动态重排的正确范围

局部重排不是只移动甘特图上选中的一条任务。请假、故障、插单或人工拖动发生后，应计算影响闭包：

```text
直接受影响任务
∪ 上下游依赖与供需任务
∪ 共享批成员
∪ 同窗口竞争相同人员、设备、工装或容量的任务
∪ 需要保持同一资源连续性的阶段
```

闭包外任务及已执行事实固定，闭包内任务重新优化；若无法得到合法方案，再扩大范围或转人工决策。所有结果产生新候选版本，不能静默覆盖正式计划。

## 7. 版本、部署与运维判断

- 截至本次调研，OR-Tools 官方最新稳定发布为 9.15，仓库使用 Apache-2.0；正式项目仍需锁定精确 Maven 版本和保存许可证清单。
- OR-Tools Java 官方支持 Maven 包，运行时需要加载平台原生库。最终目标操作系统必须实测对应原生包、CPU 架构、JDK、内存和线程数；本项目当前目标已选为 Windows x64，因此不以 Linux/glibc 或容器作为前置条件。
- 求解是 CPU / 内存密集任务，不应在普通 HTTP 请求或持有 MySQL 事务期间同步执行。Worker 应支持并发上限、时限、取消和进程异常恢复。
- Timefold 2.x 当前要求 Java 21 及 Spring Boot 4；Community 为 Apache-2.0，Enterprise 是独立商业产品。它适合作为备选，不应把企业版功能写成 Community 已具备。
- Choco 6.0.1 是 BSD-3-Clause 的纯 Java 约束规划库，能表达调度约束，但需要更多底层领域封装，首批不选。

版本号属于易变信息，正式引入依赖时必须重新核对，不使用 `latest` 作为构建版本。

## 8. 仍需通过 PoC 证明的事项

| 待验证 | 统一验证方法 | 未通过时的处理 |
| --- | --- | --- |
| 20,000 道任务规模 | 使用同一脱敏数据分别跑候选裁剪、局部和全量模型，记录变量、约束、内存、首个解和最终 gap | 缩短详细窗口、分解瓶颈资源、加强候选裁剪或触发备选框架评估 |
| 共享炉批 | A60+B40、超量、不兼容、自动运行和装卸人员样例 | 固化批模板；不能正确守恒则不进入生产实现 |
| 连续转移批 | 上游20件/小时、下游40件/小时、每20件释放 | 显式拆批；禁止用一次解锁近似全部后续供给 |
| 具名人员效率 | 相同技能不同标准速率，对比交期与人员选择 | 增加有效期化资源工艺速率模型，不能只用技能等级代替 |
| 不可行解释 | 缺技能、无连续窗口、锁定撞维修、物料不足四组故障注入 | 建立业务原因码和诊断模型；不直接展示求解器内部变量名 |
| 滚动重排稳定性 | 故障、请假、急单各跑人工计划基线与局部求解 | 调整扰动目标和闭包策略；不可接受时转人工修复 |
| Worker 运维 | 超时、取消、进程崩溃、重复请求、求解中数据修订 | 候选过期或可恢复，正式版本保持不变 |

在真实数据基准通过前，不承诺“120 秒必得全局最优”，也不承诺任何行业规模都使用同一个模型参数。

## 9. 官方来源

访问日期均为 2026-09-11。以下仅列本专项直接使用的一手资料，完整竞品来源仍在上游调研中维护。

| ID | 官方来源 | 用于确认 | 限制 |
| --- | --- | --- | --- |
| E01 | [OR-Tools Scheduling Overview](https://developers.google.com/optimization/scheduling) | 官方调度问题入口 | 示例不是本项目完整模型 |
| E02 | [OR-Tools Job Shop Java 示例](https://developers.google.com/optimization/scheduling/job_shop) | 工序前置、机器不重叠、区间变量和 makespan | 基础 Job Shop，不含人员、日历和物料业务层 |
| E03 | [OR-Tools Java 安装](https://developers.google.com/optimization/install/java) | Maven、源码和 SDK 三种 Java 使用方式 | 正式镜像仍需本项目验证 |
| E04 | [OR-Tools `CpModel` Java API](https://or-tools.github.io/docs/javadoc/com/google/ortools/sat/CpModel.html) | 可选区间、NoOverlap、Cumulative、hint、assumption | API 能力不代表自动形成 APS 规则 |
| E05 | [OR-Tools `CpSolver` Java API](https://or-tools.github.io/docs/javadoc/com/google/ortools/sat/CpSolver.html) | 状态、best bound、取消、回调、不可行假设集合 | 业务解释需自建 |
| E06 | [OR-Tools 求解时限](https://developers.google.com/optimization/cp/cp_tasks) | Java 设置时限和回调停止 | 超时语义由应用层准确映射 |
| E07 | [OR-Tools 官方仓库与许可](https://github.com/google/or-tools) | Apache-2.0、版本与源码 | 引入时仍需依赖清单 |
| E08 | [OR-Tools 9.15 发布](https://github.com/google/or-tools/releases/tag/v9.15) | 本次选型基准版本 | 后续升级必须单独回归 |
| E09 | [Timefold Solver 官方仓库](https://github.com/TimefoldAI/timefold-solver) | Community / Enterprise 边界、Apache-2.0、Java/Kotlin 定位 | Enterprise 能力不能外推给 Community |
| E10 | [Timefold 1.x 升级到 2.x](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-from-v1) | Java 21 与 Spring Boot 4 基线 | 版本会变化，引入时复核 |
| E11 | [Timefold FAQ](https://docs.timefold.ai/timefold-solver/latest/frequently-asked-questions) | 支持矩阵、pinning 和人工计划角色 | 产品建议不替代本项目验证 |
| E12 | [Choco Solver 官方仓库](https://github.com/chocoteam/choco-solver) | 纯 Java、BSD-3-Clause 与版本 | 只作为后备框架研究 |
| E13 | [SAP PP/DS](https://help.sap.com/docs/SAP_S4HANA_ON-PREMISE/f899ce30af9044299d573ea30b533f1c?locale=en-US&state=PRODUCTION&version=2023.latest) | 启发式、优化、交互计划、资源和组件可用性 | SAP 产品机制，不是本项目实现证据 |
| E14 | [SAP 详细排程策略](https://help.sap.com/docs/SAP_S4HANA_ON-PREMISE/f899ce30af9044299d573ea30b533f1c/75e12d5164d2fb50e10000000a441470.html?locale=en-US) | 有限资源下人工计划和策略配置 | 具体版本与客户配置相关 |
| E15 | [Siemens Opcenter Advanced Scheduling](https://www.siemens.com/en-us/products/opcenter/advanced-planning-scheduling-aps/advanced-scheduling-software/) | 多约束、资源速度、换型、工序关系和优化规则 | 厂商产品页，未做账号实测 |

## 10. 调研决定

| 编号 | 决定 | 状态 |
| --- | --- | --- |
| ENG-DEC-01 | 首选 OR-Tools CP-SAT 9.15 Java API 进入同数据集 PoC | Recommended for PoC |
| ENG-DEC-02 | 求解 Worker 与普通 API 进程隔离，MySQL 为唯一权威业务事实源 | Proposed |
| ENG-DEC-03 | Timefold 作为退出备选，不一期维护双生产引擎 | Proposed |
| ENG-DEC-04 | 甘特图拖动必须通过同一硬约束校验与候选版本流程 | Required |
| ENG-DEC-05 | 真正采用前以真实数据规模、解释质量、原生库部署和故障恢复为门槛 | Required |
