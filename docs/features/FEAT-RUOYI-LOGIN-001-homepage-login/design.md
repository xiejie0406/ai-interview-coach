# 主页 RuoYi 登录迁移技术设计

> 文档类型：设计
> 文档状态：Draft
> owner / 责任边界：platform / identity / frontend
> 创建时间：2026-08-23
> 更新时间：2026-08-23
> Feature ID：FEAT-RUOYI-LOGIN-001
> 风险等级：L3
> 产出/适用阶段：5 技术设计
> 阶段状态：InProgress
> 关联：[`feature-spec.md`](feature-spec.md)、[`../../architecture/ruoyi-platform-convergence.md`](../../architecture/ruoyi-platform-convergence.md)

## 1. 契约与请求路由

| 操作 | 浏览器路径 | RuoYi 返回 |
|---|---|---|
| 验证码 | `/api/captchaImage` -> `/captchaImage` | `code/captchaEnabled/uuid/img` |
| 登录 | `/api/login` -> `/login` | `code/token/msg` |
| 当前用户 | `/api/getInfo` -> `/getInfo` | `code/user/roles/permissions` |
| 菜单权限 | `/api/getRouters` -> `/getRouters` | `code/data` |
| 退出 | `/api/logout` -> `/logout` | `code/msg` |
| AI 业务 | `/api/v1/*` -> `/api/v1/*` | 由 RuoYi SecurityContext 和 AI Controller 决定 |

## 2. 状态与安全边界

- Token 的唯一持久化键为 `ruoyi-token`；密码、验证码、Token 不进入 URL、日志或 query cache。
- 用户、roles、permissions、routers 仅保存在 React Query/Context 内存；不复制为第二套认证状态。
- 每个请求从唯一 Token 键读取并发送 `Authorization: Bearer <token>`。
- 401 清理 Token、取消/清理业务 query 并跳转登录；403 只显示拒绝。
- AI Controller 的当前主体必须从 RuoYi `SecurityContext`/`SecurityUtils.getLoginUser()` 获取，前端传入的 `user_id/role/tenant` 不可信。

## 3. 代理与回滚

- `frontend` `/api`、`/actuator` 统一代理 `http://127.0.0.1:8081`。
- 不改 RuoYi SecurityFilterChain、TokenService、Session 体系。
- 回滚仅恢复本轮前端文件和代理配置；不得恢复旧 AI 登录为正式入口，不删除历史文件，不触碰数据库。

