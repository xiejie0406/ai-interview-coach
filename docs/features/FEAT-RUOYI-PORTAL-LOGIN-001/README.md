# Portal RuoYi 登录体验

> Feature ID：`FEAT-RUOYI-PORTAL-LOGIN-001`  
> 文档状态：Draft  
> 当前阶段：8 审查验证  
> 阶段状态：InProgress  
> 风险等级：L2  
> 发布事实：`NotReleased`

## 目标

将 `apps/portal-web/login` 对齐 `apps/admin-web` 的 RuoYi 登录协议和交互：`/captchaImage`、`/login`、`/getInfo`、`/getRouters`、`/logout`，唯一使用 `Admin-Token` Cookie，并保留 Portal 的产品文案和业务路由。

## 追踪

- `REQ-PORTAL-LOGIN-01`：使用 Admin 同一四字段登录与验证码流程。
- `REQ-PORTAL-LOGIN-02`：Token 仅保存为 `Admin-Token` Cookie，统一发送 Bearer，401 清理并回到 `/login`。
- `REQ-PORTAL-LOGIN-03`：退出调用 `/logout` 后清理 Token 和内存主体。
- `BR-PORTAL-LOGIN-01`：登录中禁止重复提交；登录失败刷新验证码。
- `AC-PORTAL-LOGIN-01`：未登录受保护路由跳转 `/login?redirect=...`。
- `AC-PORTAL-LOGIN-02`：空表单、验证码失败、网络失败均有可见反馈。
- `AC-PORTAL-LOGIN-03`：成功登录校验 `/getInfo`、`/getRouters` 后进入原 Portal 首页。
- `AC-PORTAL-LOGIN-04`：退出或 401 后 Cookie 清理，再访问受保护路由回到登录页。

## 非目标

不修改 `backend/`、不调用真实 Provider、不改变 Interview 业务 API、不新增 Session/Cookie/Token 协议、不改变 Portal 业务路由。

详见 [`verification.md`](verification.md) 与 [`acceptance.md`](acceptance.md)。
