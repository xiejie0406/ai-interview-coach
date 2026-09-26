# Aden Agent Runtime

`aden-agent-runtime` 是 FEAT-ADEN-001 的独立 Python 3.12 发行单元。IMP-07
实现 provider-disabled 的离线候选链：读取显式本地 `AgentJob` fixture，经严格
Pydantic DTO、Scope / 工具 / 来源策略、`FakeModelPort` 和独立 `LeaseSupervisor`
生成 `Candidate`，再提交给纯内存 Fake transport。它不直接推进 Java Task，且不是
第二套业务后端。

## 当前安全边界

- `provider_disabled=true` / `provider_mode=provider_disabled` 是不可改为启用态的类型约束；未安装任何 Provider SDK。
- 默认 transport 为 `fake`，网络、UIA、Shell、浏览器、任意文件访问和外部写入均关闭。
- `DisabledModelPort` 始终 fail closed；`FakeModelPort` 只按严格 DTO 产生确定性合成 draft。
- 不监听 HTTP 端口，不连接数据库，不读取用户 JWT 或 Provider Secret。
- JSON Schema 校验只读取显式传入的本地文件，不解析远程引用。
- Model 调用前后及提交前重新检查取消、deadline、lease 与 fencing；失效后的迟到结果不会提交。
- 工具只允许 `read / proposal`，最终有效范围是服务端 manifest 与本地 allowlist 的交集；本期不执行真实工具。
- 三个产品 Agent Family 仅以版本化 RunSpec 形状注册且全部禁用；`SYNTHETIC_CORE` 是当前唯一可执行测试 harness。

## 包职责

`bootstrap` 负责 fail-closed 配置；`contracts` 负责 DTO / Schema；`client` 提供 Fake
控制面；`runtime` 编排 attempt；`agents` 保存 Family 注册表；`model_ports` 隔离模型
SDK；`tools` 计算工具范围；`policy` 校验引用、敏感字段和大小；`observability` 只输出
脱敏结构化元数据。

## 本地验证

```powershell
uv sync --extra test --frozen
uv run --extra test --frozen pytest
uv run --extra test --frozen ruff check .
uv run --extra test --frozen pyright
uv run --frozen aden-agent-runtime
uv run --frozen aden-agent-runtime tests/fixtures/agent-job.json
uv build
```

校验 experimental Agent Schema 或一个合成实例：

```powershell
uv run --frozen aden-agent-contracts schema <schema.json>
uv run --frozen aden-agent-contracts instance '<schema.json#/$defs/Type>' <example.json>
```

本工程不依赖 `aden-runner`，也不向其导出 Python 包；两个进程可以独立锁定、构建和发布。
PydanticAI extra 属于可选兼容实验，本阶段没有安装；若未来验证，也只能放在
`ModelAgentPort` 后并使用 `TestModel / FunctionModel`，不得启用真实 Provider。
