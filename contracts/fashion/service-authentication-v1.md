# Fashion 内部服务认证 v1

该协议用于 `ruoyi-fashion` 与 `fashion-ai-runtime` 的双向内部 HTTP 调用。它是 IMP-01 的可测试 HMAC-SHA256 key-ring 基线；生产部署仍必须使用私网 TLS。当前实现不代表 mTLS、OAuth2 或 workload identity 已部署。

## 必需请求头

| 请求头 | 约束 |
| --- | --- |
| `X-Fashion-Service-Id` | 调用方稳定服务身份；当前为 `ruoyi-fashion` 或 `fashion-ai-runtime` |
| `X-Fashion-Key-Id` | key ring 中的 active 或 previous key ID，不得包含 Secret |
| `X-Fashion-Timestamp` | Unix epoch 秒 |
| `X-Fashion-Nonce` | 每个请求唯一，16～128 位 `[A-Za-z0-9._:-]` |
| `X-Fashion-Audience` | 接收方服务 ID，必须与本服务一致 |
| `X-Fashion-Content-SHA256` | 实际 HTTP 请求体原始字节的 SHA-256 小写十六进制；空正文使用 SHA-256 空摘要 |
| `X-Fashion-Signature` | `v1=` 加无 padding Base64URL HMAC-SHA256 |
| `X-Request-Id` | UUID；用于单次请求关联，不作为授权依据 |
| `X-Correlation-Id` | 1～100 位稳定业务关联 ID |
| `traceparent` | W3C Trace Context v00；接收方继续传播，禁止放业务正文或 Secret |

当前 v1 内部接口禁止 query string。签名正文按 UTF-8 连接，末尾没有额外换行：

```text
FASHION-HMAC-SHA256\n
{UPPERCASE_METHOD}\n
{RAW_PATH}\n
{SERVICE_ID}\n
{AUDIENCE}\n
{TIMESTAMP_EPOCH_SECONDS}\n
{NONCE}\n
{LOWERCASE_BODY_SHA256}
```

`X-Fashion-Signature` 的值是 `v1=` + `base64url_no_padding(HMAC_SHA256(secret, canonical_request))`。验证方必须在 JSON 解析和业务处理前校验 header 格式、audience、时间窗、key ID、实际正文摘要和签名，并使用常量时间比较。默认允许时钟偏差为 300 秒；nonce 至少保留 600 秒，只有签名校验成功后才登记。重复 nonce 返回 `401 SERVICE_AUTHENTICATION_FAILED`，不向调用方区分密钥、签名或重放失败细节。

认证与授权必须分层：缺失/畸形 header、未知 key、摘要或签名错误、过期和重放统一返回 `401 SERVICE_AUTHENTICATION_FAILED`；签名已经成立但 `service_id` 或 `audience` 不在允许范围时返回 `403 SERVICE_NOT_AUTHORIZED`；nonce store 达到有界容量时 fail closed，返回 `429 RATE_LIMITED`。不得在签名成立前用 403 暴露授权范围。

key ring 至少支持一个 active key 和一个 previous key 的轮换窗口。配置项中的 Secret 必须是至少 32 字节随机 key material 的 RFC 4648 Base64 或 Base64URL 编码；Java 与 Python 都先解码后用于 HMAC，不把配置字符串本身当作 key。Secret 只来自环境 Secret 或平台 Secret 注入，没有仓库默认值；日志、trace、错误响应和浏览器产物都不得包含 Secret、签名、delegated actor token 或请求正文。

Spring Boot 侧配置前缀为 `fashion.service-identity`，环境变量映射为 `FASHION_SERVICE_IDENTITY_ACTIVE_KEY_ID`、`FASHION_SERVICE_IDENTITY_ACTIVE_KEY_BASE64`、`FASHION_SERVICE_IDENTITY_PREVIOUS_KEY_ID` 和 `FASHION_SERVICE_IDENTITY_PREVIOUS_KEY_BASE64`。Python 侧对应 `FASHION_AI_AUTH_ACTIVE_KEY_ID`、`FASHION_AI_AUTH_ACTIVE_KEY_BASE64`、`FASHION_AI_AUTH_PREVIOUS_KEY_ID` 和 `FASHION_AI_AUTH_PREVIOUS_KEY_BASE64`。两侧只通过部署 Secret 注入匹配的 key material；轮换时先部署新 active＋旧 previous，确认双方切换后再移除 previous，不能把 Secret 写入 YAML、浏览器环境或本文示例。

进程内 nonce store 使用有界 map 与到期最小堆：重复 nonce 返回 `401 SERVICE_AUTHENTICATION_FAILED`；容量保护返回 `429 RATE_LIMITED`，不得把容量耗尽误报为重放。多实例部署前必须把 nonce store 替换为跨实例一致的受控实现，或切换到平台批准的 mTLS/OAuth2/workload identity；当前实现只证明本阶段协议和单实例防重放边界。
