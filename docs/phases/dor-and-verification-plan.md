# DoR、验证与验收设计

> 文档类型：Phase / 验证计划  
> 文档状态：Draft  
> owner / 责任边界：质量 owner 维护方法和证据入口；用户批准测试/UAT并形成业务结论  
> 创建时间：2026-08-02  
> 更新时间：2026-08-02  
> Feature ID：FEAT-INTERVIEW-001  
> 风险等级：L3  
> 产出/适用阶段：6、8、9、10  
> 阶段状态：WaitingForApproval  
> 证据结果：NotRun

## 1. 进入 Phase 01 编码的 DoR

| DoR 项 | 当前结果 | 依据/缺口 |
|---|---|---|
| L3 分流、范围、独立性 | Pass（文档静态） | AGENTS、控制页；仅说明规划边界 |
| Canonical P0/P1 REQ/BR/AC Approved | Blocked | PRD Draft 且截断 |
| 关键原型及异常状态 Approved | Blocked | HTML Mock 原型存在，用户评审未完成 |
| DES/数据/API/状态/回滚 Approved | Blocked | 技术架构 Draft 且截断；契约包生成中 |
| Feature 控制页 | Pass（文件存在） | 控制页已建；不推导阶段门通过 |
| tasks/依赖/文件范围 | Draft | 主任务候选已建，尚未批准 |
| 执行包与代码授权 | Pending | 用户目标是完整编码；精确包尚未确认 |
| 测试代码授权 | NotRequested | 不创建测试代码 |
| 构建/测试/启动授权 | NotRequested | 不执行命令 |
| 工作区/共享文件 owner | Draft | 并行计划已定义，待 tasks 批准 |

结论：当前 DoR **Blocked**；可以继续文档恢复与任务准备，不可把代码窗口激活。

## 2. AC—EV 入口矩阵

| AC | 核心行为 | 最小技术证据候选 | UAT 候选 | 当前 |
|---|---|---|---|---|
| AC-01 | 已发布题目组合筛选/空态 | Catalog repository/API contract/UI | 筛选并恢复空结果 | NotRun |
| AC-02 | 单题反馈引用 Rubric/原话并区分结论 | Evidence span unit + Golden Set + API/UI | 用户核对一题反馈 | NotRun |
| AC-03 | 文本计划、逐题、有限追问、暂停/结束 | Session/Agent integration + SSE + browser | 完成短文本面试 | NotRun |
| AC-04 | 麦克风告知/拒绝零外传/文本降级 | consent/WS audit + browser permission scenario | 用户拒绝麦克风 | NotRun |
| AC-05 | 语音轮次、转写修正、按修正文本评分 | ASR contract + real authorized chain + browser | 修正技术术语后继续 | NotRun |
| AC-06 | ASR/TTS 故障不丢轮次并降级 | Provider stub/fault injection + session recovery | 故障中继续文本 | NotRun |
| AC-07 | 完成/主动结束后的证据报告 | Evaluation/Report integration + UI | 打开逐题报告并核对三建议 | NotRun |
| AC-08 | 无可靠证据时拒绝伪确定判断 | Golden Bad Case + guard unit | 用户看到证据不足 | NotRun |
| AC-09 | 回答后断网恢复且不重复题/扣减 | Outbox/Job/idempotency + browser reconnect | 刷新/断网恢复 | NotRun |
| AC-10 | 删除会话/转写/报告的状态与结果 | deletion saga + storage/provider failure + audit | 查看处理中/部分失败/完成 | NotRun |
| AC-11 | 已发布 Rubric 修改产生新版本 | Catalog version integration | 管理员发布新版、旧报告仍开 | NotRun |
| AC-12 | 无隐蔽代答入口并拒绝用途 | code/route review + content policy UI | 用户请求代答得到拒绝 | NotRun |
| AC-13 | JD/简历个性化 | Gate C/P1 延后 | 后续新 Feature | NotApplicable（当前路线候选） |
| AC-14 | 开始前预计用量与 BR-09 | reservation/settlement integration + UI | 额度不足/平台失败释放 | NotRun |

`NotApplicable` 只有在用户正式批准延后范围后才成立；当前表仍是候选。

## 3. 测试分层与未来文件范围

| 层级 | 未来文件范围 | 证明 | 不能单独证明 |
|---|---|---|---|
| Domain unit | `interview-domain/src/test/**` | 状态、预算、计费、版本和策略 | 数据库/API/真实模型 |
| Application unit | `interview-application/src/test/**` | 用例编排、port 调用、错误/取消 | Adapter 和真实事务 |
| Module boundary | test-support/boot 架构测试 | Maven/package 依赖不越界 | 业务正确性 |
| Repository/Testcontainers | adapters/boot integration tests | PostgreSQL schema、tenant、lock、migration | 生产容量/备份 |
| Provider contract | adapters provider tests + stubs | request/response/error/timeout/schema | 真实质量、地域、费用 |
| API/AsyncAPI | boot contract tests | auth、error、幂等、序列/续传 | 用户体验/业务接受 |
| React component | `frontend/src/**/*.test.tsx` | 表单/状态/无障碍组件 | 真实端到端 |
| Browser | `e2e/**/*.spec.ts` | 用户可见流程与恢复 | 用户最终接受 |
| Golden Set | `benchmarks/**` | 模型/Prompt/Rubric 相对内容质量 | 线上漂移/就业结果 |
| Security/privacy | `security/**` + integration | 越权、CSRF、CORS、日志、删除 | 法律意见 |
| Resilience/performance | `performance/**`/fault scenarios | 已批准负载下的恢复/容量 | 未测生产条件 |

测试代码也是受控范围；没有授权时本表只提供设计。

## 4. 精确命令门

具体 Java/Spring/Node 版本、Wrapper 和包管理器尚未批准，因此当前不能给出可执行精确命令。执行包批准后必须把以下占位替换为真实命令，禁止 Agent自行换命令：

| 动作 | 命令状态 | 环境/影响 | 失败证据 |
|---|---|---|---|
| Java 构建 | 待 Wrapper/模块确定 | 产生 `target/`、下载依赖 | exit code、Maven error、模块 |
| 后端测试 | 待测试范围和容器确定 | 可能启动 PostgreSQL/Redis 容器 | Surefire/Failsafe、容器日志 |
| 前端安装/检查 | 待 Node/包管理器/lock 确定 | 下载依赖、产生缓存/node_modules | exit code、锁文件差异 |
| 浏览器验证 | 待服务/端口/浏览器授权 | 启动进程、写截图/trace | 控制台、截图、trace |
| Golden/Provider | 待账号/费用/数据授权 | 外部调用与成本 | request ID、用量、脱敏报告 |
| 性能/故障 | 待资源与停止门授权 | CPU/内存/网络/数据状态影响 | 指标、错误、恢复信号 |

## 5. 浏览器 UAT 场景

- UAT-01：访客理解练习定位，登录后完成首次文本练习。
- UAT-02：题库筛选无结果后恢复并打开版本化题目。
- UAT-03：配置并完成短文本面试，暂停/主动结束含明确后果。
- UAT-04：拒绝麦克风，确认零音频外传并完成文本降级。
- UAT-05：ASR 把 Java/AI 术语识别为低置信，用户修正后评分。
- UAT-06：TTS/LLM 故障后已答内容仍在，用户继续文本或结束。
- UAT-07：报告展示证据、不足和三项复练，用户进入学习计划。
- UAT-08：额度不足在开始前提示；平台故障不重复扣减。
- UAT-09：用户发起删除并看到 `PARTIAL_FAILED` 后的可操作下一步。
- UAT-10：内容管理员发布新 Rubric，旧报告仍引用旧版且无用户正文权限。

用户必须确认验收包、环境、账号/数据、步骤和影响；Agent 不能代替用户形成结果。

## 6. 失败退回与停止条件

- 产品/AC 不成立：退阶段 3/4。
- schema/API/状态/恢复不成立：退阶段 5/6。
- 实现缺陷：退阶段 7。
- P0 AC Fail、跨 tenant、隐私泄漏、金额不可复算、无界重试或不可恢复数据：阻断上线就绪。
- 未授权命令、真实账号/费用/外部写入：停止，保留 NotRun。

## 7. 证据位置候选

Feature `verification.md` 保存 AC—EV 矩阵；`acceptance.md` 保存 UAT；`benchmarks/reports/` 保存脱敏质量报告；`docs/releases/<id>/` 只汇总上线就绪链接，不复制 EV/UAT 正文。
