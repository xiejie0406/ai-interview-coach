# 配置与安全默认值

> 文档类型：实现参考  
> 文档状态：Draft  
> owner / 责任边界：platform/security owner 维护实际配置入口；部署 Secret 由环境 owner 管理  
> 创建时间：2026-08-02  
> 更新时间：2026-08-02  
> 适用范围：Foundation Wave 01  
> 关联：[`../../backend/interview-boot/src/main/resources/application.yaml`](../../backend/interview-boot/src/main/resources/application.yaml)、[`../../.env.example`](../../.env.example)

## 1. 当前环境变量

| key | 用途 | 默认/要求 | 敏感 |
|---|---|---|---|
| `INTERVIEW_DB_URL` / 兼容 `AIC_DB_URL` | PostgreSQL JDBC URL | 本地候选 URL；实际环境显式配置 | 否 |
| `INTERVIEW_DB_USERNAME` / 兼容 `AIC_DB_USERNAME` | 运行时 DML 账号 | `aic_app` 候选 | 否 |
| `INTERVIEW_DB_PASSWORD` / 兼容 `AIC_DB_PASSWORD` | 运行时数据库密码 | 无默认值；缺失应失败 | 是 |
| `INTERVIEW_DB_MIGRATOR_USERNAME` / 兼容 `AIC_DB_MIGRATOR_USERNAME` | 独立 Flyway DDL 账号 | 无默认值 | 否 |
| `INTERVIEW_DB_MIGRATOR_PASSWORD` / 兼容 `AIC_DB_MIGRATOR_PASSWORD` | Flyway 密码 | 无默认值 | 是 |
| `INTERVIEW_RELEASE_VERSION` / 兼容 `AIC_RELEASE_VERSION` | health 中的候选版本 | `0.1.0-SNAPSHOT` | 否 |

`.env.example` 只有键和非秘密候选；生产不从仓库 `.env` 读取 Secret。当前没有任何 Provider、对象存储或支付 Key 配置。

## 2. 硬安全默认值

- Session Cookie：`AIC_SESSION`，`HttpOnly`、`Secure`、`SameSite=Strict`、只允许 Cookie tracking。
- CSRF：Cookie `AIC-XSRF-TOKEN`；header `X-AIC-XSRF-TOKEN`。CSRF token 不是登录凭据；长期 session/token 仍不得存 localStorage。
- CORS：Foundation 不启用跨域；Vite 本地开发使用同源代理候选，不修改服务端 CORS。
- CSP/Referrer/Permissions Policy：API 安全基线默认拒绝；麦克风只允许同源，并且浏览器组件仍需用户显式操作与业务 consent。
- Actuator：只暴露 health；不返回详细内部状态。
- 错误：不输出 message、stack、binding internals、SQL、Key、完整 Prompt/回答。
- Hibernate：`ddl-auto=validate`；Flyway clean 禁止；业务表不由应用自动生成。
- multipart：Foundation 禁止；语音后续走明确的 turn-scoped WS/object flow。

## 3. Foundation capability locks

下列 `interview.foundation-safety.*` 当前固定为 `false`：业务 REST、SSE、WebSocket、真实 Provider、对象存储写入、后台 Job。改变任何一项都需要相应垂直切片完成端口、权限、状态、数据、失败恢复与任务边界；不能靠环境变量提前打开。

## 4. 未执行

没有执行依赖解析、Maven/TypeScript 构建、数据库连接、Flyway migration、服务启动或浏览器请求。上述键和值来自源码静态读取，不是环境可用性证据。
