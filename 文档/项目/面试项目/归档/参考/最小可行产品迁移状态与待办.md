# 最小 MVP：题库与文字/语音面试

> 文档状态：Approved  
> 风险等级：L2  
> 当前阶段：7. 开发实现  
> 阶段状态：InProgress  
> 更新日期：2026-08-16  
> 结论：代码迁移尚未全部完成，暂不进入用户验收

## 1. MVP 范围

第一版只保留两个核心功能，并要求 Portal Web 与 uni-app 都使用同一套 `/api/v1` 后端契约：

1. 题库；
2. 文字面试与语音面试。

注册、登录、Cookie Session、暂停/恢复/跳过/提前结束、刷新恢复属于必要支撑能力。

本期不做报告、评分、能力雷达、学习计划、单题练习、计费、支付、复杂后台、部署和 Git 操作。

## 2. 当前代码完成度

| 模块 | Portal Web | uni-app | 后端 | 当前结论 |
|---|---|---|---|---|
| 题库 | 列表、关键词/方向/难度筛选、详情、参考答案、加载/空态/失败重试已编码 | 同等功能已编码 | 复用 Catalog 查询 REST | 代码闭环，待场景验证 |
| 注册与登录 | 注册政策、注册、登录、Cookie Session、路由守卫已编码 | 已替换旧若依验证码登录/注册，接入同一 Session | Identity REST 已存在 | 代码闭环，待场景验证 |
| 文字面试 | 配置、计划确认、开始、回答、跳过、暂停/恢复、提前结束、刷新恢复已编码 | 同等流程已编码 | 已补 start、answer、session command 和同步题目推进 | 代码闭环，待场景验证 |
| 语音面试（H5） | 麦克风授权、MediaRecorder、Web Speech、文字修正、提交、失败转文字已编码 | H5 同等流程已编码 | 当前回答仍通过文字 Answer REST 提交 | 客户端闭环，待场景验证 |
| 语音面试（非 H5） | 不适用 | 已接 `uni.getRecorderManager`，失败可转文字 | 尚无可调用的服务端上传/ASR 入站链路 | 只完成录音和降级，未完成自动转写 |
| 服务端语音链路 | 客户端尚未接入 | 客户端尚未接入 | 领域模型、Repository、Provider、OpenAPI/AsyncAPI 已有；REST Controller、WebSocket Handler、ticket、音频存储编排未实现 | **P0 未完成** |

## 3. 本轮实际编码

### Portal Web

- 主导航收敛为题库与模拟面试；
- 新增题库详情和参考答案页面；
- 面试房间接入真实 Session 快照和 ETag；
- 支持开始、提交回答、暂停、恢复、跳过和提前结束；
- 支持浏览器录音与 Web Speech，识别文字提交前可修改；
- 麦克风或识别失败时保留文字回答；
- 修复 Web Speech interim 结果重复累加；
- 暂停时隐藏回答区，避免无效提交；
- 录音保留 Blob 分片与字节状态，不再只是启动后立即丢失状态。

### uni-app

- `pages.json` 只注册首页、题库、登录/注册、面试配置和面试房间；
- 旧若依工作台、我的、帮助等源码暂不删除，但已从运行路由移除；
- 登录和注册改用 AI 面试后端 Cookie Session；
- 新增题库列表、筛选、详情和参考答案；
- 面试配置与房间改用同一 `/api/v1` 契约；
- H5 支持 MediaRecorder + Web Speech；
- 非 H5 支持 `uni.getRecorderManager`，识别不可用时可手动输入；
- 增加面试入口登录检查、刷新恢复、暂停态保护和操作忙碌保护。

### 后端

- 注册 `StartInterview`、`SubmitInterviewAnswer`、`ApplySessionCommand` 和 `ProgressInterview`；
- 新增开始、回答、暂停/恢复/跳过/提前结束等 REST 入站接口；
- Session 响应返回 `allowedCommands`；
- MVP 按确认计划中的题目顺序同步打开下一题，不依赖尚未运行的动态 Agent Worker。

## 4. 尚未完成的迁移

### P0：服务端语音链路

仓库现有 `contracts/openapi/voice.yaml` 与 `contracts/asyncapi/voice-events.yaml` 定义了：

- `POST /interviews/{id}/voice-preflight`；
- `POST /interviews/{id}/voice-sessions`；
- `/ws/v1/interviews/{id}/voice` 音频分片协议；
- Transcript 查询、修正确认和 Audio Artifact 生命周期。

但当前代码没有对应 REST Controller 和 WebSocket Handler，`WebSocketInboundAdapter` 仍只是空标记接口；`VoiceCapabilityPort`、`InterviewVoiceAccessPort`、`VoiceSessionTicketPort` 也没有运行时实现。因此以下能力还未完成：

1. 客户端录音上传到服务端；
2. 服务端音频大小/时长/codec 门禁；
3. Audio Artifact 持久化与过期清理；
4. ASR 调用、转写状态和失败恢复；
5. Transcript 修正确认后生成 Answer；
6. 刷新后恢复未确认的语音转写。

在该 P0 关闭前，只能称为“浏览器本地语音识别版 MVP”，不能称为完整服务端语音面试。

### P1：自动化测试

后端 Maven 模块当前没有测试源码，`mvn test` 会显示 `No tests to run`。需要补最少的会话推进、并发 ETag、跳过/恢复和语音降级测试后，才能形成稳定回归证据。

## 5. 代码级检查结果

| 证据 | 命令 | 结果 |
|---|---|---|
| EV-BACKEND-COMPILE-01 | `mvn -pl backend/interview-boot -am -DskipTests package` | Pass，Spring Boot 可执行 Jar 打包成功 |
| EV-PORTAL-BUILD-01 | `apps/portal-web: npm run build` | Pass，1466 modules transformed；仅有 chunk 大小和 store import 警告 |
| EV-MOBILE-H5-BUILD-01 | `apps/mobile: npm run build:h5` | Pass，`DONE Build complete` |
| EV-MOBILE-ROUTES-01 | PowerShell 解析 `pages.json` | Pass，JSON 有效且运行路由已收敛 |

这些结果只证明代码可以编译/构建，不代表业务场景验收通过。

## 6. 下一步顺序

1. 完成服务端 Voice REST、WebSocket、ticket、存储和 Transcript 编排；
2. Portal Web 接入 voice preflight、voice session、音频分片和 Transcript 确认；
3. uni-app H5 接入同一链路；非 H5 接入平台录音文件上传和服务端 ASR；
4. 增加最小自动化测试并完成编译/构建检查；
5. 最后才使用真实 Google Chrome 验收题库、文字面试、语音授权/识别/修正/提交、失败降级和刷新恢复；
6. Chrome 只能证明 uni-app H5，微信小程序和原生 App 需要独立真机验收。

## 7. 授权边界

本轮已授权本地源码和文档修改、Maven/Portal/uni-app 构建、本地服务重启、专用本地验收数据和真实 Google Chrome 验收。

不包含部署、Git、生产数据和付费 Provider 调用。若完整服务端 ASR 必须调用真实收费 Provider，需要重新取得独立授权；在此之前不得把浏览器 Web Speech 结果描述为服务端 ASR 已完成。
