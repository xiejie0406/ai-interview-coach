# Aden Runner

`aden-runner` 是 FEAT-ADEN-001 的独立 Python 3.12 发行单元。IMP-05 已提供
Session、claim、heartbeat、lease/fence、receipt、固定 fixture 与崩溃点模拟；
它仍是无桌面权限、无真实副作用的协议验证器，不是生产执行器。

## 当前安全边界

- 默认 CLI 仍为 `baseline` 自检；协议模拟器只允许显式配置 `localhost`、`127.0.0.0/8`
  或 `::1` RuoYi Origin，并禁用环境代理与 HTTP redirect。
- UIA、Shell、浏览器、任意文件访问和外部写入始终不可启用。
- 不读取 RuoYi 用户 JWT，不连接数据库，也不保存 Runner credential。
- 只提供 JSON Schema 2020-12 的本地文件校验入口；校验过程不会解析远程引用。

六个实现包分别为 `simulator/protocol/transport/lease/receipts/fixtures`。Simulator
只解释 `externalActionsEnabled=false` 且 SHA-256 完整性成立的版本化 TaskPackage；
本地账本严格维护 heartbeat / receipt 单调序号和 fence，进程重启后必须重新对账。

`FaultPoint.AFTER_CLAIM / AFTER_STARTED / BEFORE_FINAL` 用于确定性模拟响应丢失和
崩溃窗口。日志、异常和 `repr` 不输出 credential / session Secret。

## 本地验证

```powershell
uv sync --extra test --frozen
uv run --extra test --frozen pytest
uv run --extra test --frozen ruff check .
uv run --extra test --frozen pyright
uv run --frozen aden-runner
```

校验 Schema 或一个合成实例：

```powershell
uv run --frozen aden-runner-contracts schema <schema.json>
uv run --frozen aden-runner-contracts instance '<schema.json#/$defs/Type>' <example.json>
```

命令成功时不输出业务数据；失败返回非零退出码并只报告本地文件校验错误。
