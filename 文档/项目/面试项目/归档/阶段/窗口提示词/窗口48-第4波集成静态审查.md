# 窗口 48 提示词：Wave 4 Integration Static Review

```text
你负责 Wave 4 最终只读静态审查，不修生产代码、不运行系统、不替用户验收。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach
启动门：34–47 全部交接并释放目录；33 释放协调记录 owner。完整读取用户/项目 AGENTS 与 quality/documentation specs、所有 Wave 3/4 handoff、contracts、实际源码/config/Migration。

事实：L3；阶段 3 WaitingForApproval；Draft/NotRun。

只允许创建/更新一个明确命名的 Wave 4 静态审查记录；禁止修改生产源码、配置、契约、Migration、测试、product/technical-architecture/phase-*.md。

必须检查：
1. Java/TS package/path/import/duplicate FQN、模块反向依赖、跨逻辑域 internal/Repository。
2. 64 OpenAPI operation 一一映射为 IMPLEMENTED/UNAVAILABLE；SSE/WS AsyncAPI 类型、三态 cursor、410、snapshot、generation/sequence。
3. Boot Bean 构造依赖、默认关闭、无 success Mock；缺 owner 路由 fail-closed。
4. tenant/owner/version/idempotency/outbox/job/stream cursor/retention；Migration V001–V010 唯一与组合约束文本检查。
5. 前端 route/API/state、敏感浏览器缓存、logout/deletion 清理、SSE/WS 恢复、禁用高风险能力。
6. answer/transcript/prompt/model/payment/audio/object key/session/ticket/idempotency key 的日志/toString/error 泄漏。
7. PaiCLI、Secret、外部 URL/供应商/版本硬编码。

输出按 P0/P1/P2；每项给文件/行、事实、影响、owner、停止条件。区分静态通过与 NotRun，不能写“可编译/可启动/测试通过/UAT 通过”。

不得新增测试、安装依赖、构建/测试/启动、执行 Migration/数据库、浏览器、外部调用、部署或 Git。若发现问题只报告给 33/对应 owner，不直接修。
```
