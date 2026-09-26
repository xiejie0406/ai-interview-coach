# Fashion AI Runtime

`fashion-ai-runtime` 是 AI 智能选品唯一的 Python 3.12 AI 运行时。Java 业务侧
通过内部 HTTP 契约调用它；Python 不直连业务数据库，不持有商品、库存、价格、
报价或正式副作用的权威事实。

当前 IMP-05 基线提供 FastAPI、严格 Pydantic typed output、HMAC 服务身份、解析前
正文限制、W3C Trace Context、脱敏结构日志，以及需求分析和商品属性建议两个窄用例。
Java 持有 Agent 版本、Run、租约、幂等与人工采用事实；Python 只执行已注入的
Analyzer/Suggester 端口。仓库没有真实 Provider 密钥或默认联网 adapter，默认
仍为 `disabled`。本机可显式设置 `FASHION_AI_PROVIDER_MODE=demo`，以确定性规则
生成标明“本机演示”的待确认结果，不调用外部模型。测试数据和入口见
`../../文档/项目/智能选品项目/开发记录/2026-09-26-本机可试用演示版本.md`。

## 现有接口

| 方法 | 路径 | 当前行为 |
|---|---|---|
| `GET` | `/health` | 公共存活检查，返回当前 `disabled` 或 `demo` 模式 |
| `GET` | `/internal/v1/capabilities` | 认证后返回需求分析、商品属性建议、选品搭配的能力状态及原因 |
| `POST` | `/internal/v1/requirement-analysis` | `disabled` 返回 503；`demo` 返回本机规则草案 |
| `POST` | `/internal/v1/product-attribute-suggestion` | 只接收冻结的非价格/库存商品属性；`demo` 返回本机规则建议 |
| `POST` | `/internal/v1/selection-styling` | 只在 Java 冻结候选集内排序 1–4 品类搭配，校验锁定、去重、预算和递进继承；`demo` 返回本机规则组合 |

四条接口的 path、method 和既有 `version: "1.0"` 字段保持兼容。服务不发布
`/openapi.json`、`/docs` 或 `/redoc`；提交版契约由运行时模型确定性导出到
`../../contracts/fashion/ai-runtime.openapi.yaml`。

## 服务认证与请求边界

`/internal/**` 始终 fail closed。未配置密钥时也不会降级为匿名访问；`/health`
不认证，但它只返回存活状态和 Provider 模式，不暴露业务数据。

HMAC v1 必需请求头为：

- `X-Fashion-Service-Id`
- `X-Fashion-Key-Id`
- `X-Fashion-Timestamp`
- `X-Fashion-Nonce`
- `X-Fashion-Audience`
- `X-Fashion-Content-SHA256`
- `X-Fashion-Signature`
- `X-Request-Id`
- `X-Correlation-Id`
- `traceparent`

`tracestate` 可选。完整协议与跨语言向量分别位于
`../../contracts/fashion/service-authentication-v1.md` 和
`../../contracts/fashion/examples/v1/service-authentication-vectors.json`。
当前 v1 禁止 query。默认时钟偏差为 300 秒，nonce 至少保存 600 秒；
active 与 previous key 在入站轮换窗口内都可验证，但 Python 出站 signer 只用
active key。nonce 按 `service_id + nonce` 唯一，因此不能借密钥轮换重复使用。

服务认证密钥由若依“平台密钥”的 `platform.fashion.service.active` 和
`platform.fashion.service.previous` 统一管理。若依启动器从主 MySQL 读取并解密，
通过子进程标准输入的单次管道消息交给 Runtime；旧的
`FASHION_AI_AUTH_*_KEY_*` 环境变量会被拒绝。密钥内容为
`{"keyId":"...","keyBase64":"..."}`，不在本工程保存第二份配置。

每个解码后密钥至少 32 字节；空值、非法 Base64、半套 key 配置或 active 与
previous 共用 key ID 都会拒绝启动配置。其他可调参数：

```text
FASHION_AI_AUTH_ALLOWED_SERVICES=ruoyi-fashion
FASHION_AI_AUTH_CLOCK_SKEW_SECONDS=300
FASHION_AI_AUTH_NONCE_TTL_SECONDS=600
FASHION_AI_AUTH_MAX_NONCE_ENTRIES=100000
FASHION_AI_MAX_JSON_BODY_BYTES=65536
FASHION_AI_LOG_LEVEL=INFO
FASHION_AI_PROVIDER_MODE=disabled
```

ASGI 中间件在 FastAPI/Pydantic 解析 JSON 前累计实际消息字节，默认上限为
64 KiB。缺失 `Content-Length`、伪造较小值或 chunked 传输都不能绕过上限；
超限统一返回 `413 PAYLOAD_TOO_LARGE`。认证失败与 nonce 重放不暴露内部原因，
统一返回 `401 SERVICE_AUTHENTICATION_FAILED`；服务身份/目标不允许时返回
`403 SERVICE_NOT_AUTHORIZED`；进程内 replay store 容量耗尽时 fail closed，
返回 `429 RATE_LIMITED`。

正文携带的 `request_id` 必须与 `X-Request-Id` 一致；可选
`correlation_id` 存在时必须与 `X-Correlation-Id` 一致。不一致返回安全的
`422 REQUEST_VALIDATION_FAILED`，且错误响应与日志都不回显正文、签名或 Secret。

## 可观测性边界

Runtime 接收并继续传播 W3C `traceparent`/`tracestate`，响应携带
`traceparent` 与 `X-Correlation-Id`。错误信封包含 nullable `request_id`、
`correlation_id`、`run_id`、`trace_id` 和稳定错误对象；无法安全解析的业务 ID
保持 `null`。

结构日志采用字段白名单，只允许请求方法、path、状态、耗时、正文大小、已认证
服务 ID、业务关联 ID、trace ID 和稳定错误码。OpenTelemetry SDK 只建立本地
trace/metrics 基座，当前未配置 exporter、collector 或外部采集端，不应把
“已有埋点”解释成“已接入真实可观测平台”。

## 目录

```text
fashion-ai-runtime/
├── src/fashion_ai/
│   ├── api/                 # FastAPI 路由、ASGI 安全中间件、HMAC adapter
│   ├── application/         # 需求分析、商品属性建议与 disabled adapter
│   ├── domain/              # 传输无关的严格领域/契约模型
│   ├── ports/               # Analyzer、入站认证、出站 signer 端口
│   ├── telemetry/           # W3C trace、OTel 基座与脱敏结构日志
│   ├── main.py              # 应用组合根
│   └── settings.py          # 外部配置与 key ring
├── scripts/export_openapi.py
└── tests/
```

根层 `models.py` 和 `config.py` 仅保留兼容导入；新代码应使用上述分层模块。

## 本地验证

项目使用 `uv.lock` 固定可复现依赖；开发环境放在仓库已忽略的 `.runtime/`：

```powershell
$env:UV_PROJECT_ENVIRONMENT = "..\.runtime\fashion-ai-runtime-venv"
uv sync --extra test --extra agent --frozen
uv run --frozen pytest -p no:cacheprovider
uv run --frozen ruff check src tests
uv run --frozen pyright
uv run --frozen python scripts/export_openapi.py
```

服务认证模式下从工作区根目录运行若依启动器；除主 MySQL 连接与密文根密钥这两类
自举环境外，服务认证值都来自平台密钥模块：

```powershell
java -jar ruoyi-backend/ruoyi-admin/target/ruoyi-admin.jar --run-fashion-ai-runtime uv run --project python/fashion-ai-runtime --frozen uvicorn fashion_ai.main:app --host 127.0.0.1 --port 8100
```

单独启动 `uvicorn` 时不加载服务认证密钥，受保护接口失败关闭。平台密钥轮换后，
先保留 previous 版本，再按部署顺序重启 Java 和 Python 进程，确认双端一致后
移除旧版本。

## 仍然存在的生产限制

- 生产部署仍要求私网 TLS；当前实现不表示 mTLS、OAuth2 或 workload identity
  已上线。
- replay store 是 map + expiry min-heap 的单进程有界实现。多实例部署前必须
  替换为跨实例一致的受控实现，或切换到平台批准的服务身份方案。
- OTel 当前没有 exporter/collector；Provider、费用上限和工具调用均未启用。
  执行租约、幂等与正式采用只存在于 Java/MySQL 控制面，Python 不复制这些事实。
- 任何 Provider 启用都必须另行完成配置、费用、超时/重试、审计脱敏、结构化
  输出验证和失败降级设计；不得修改 disabled adapter 使其静默联网。
