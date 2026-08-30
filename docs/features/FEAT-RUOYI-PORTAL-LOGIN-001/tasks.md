# Portal RuoYi 登录任务

> Feature ID：`FEAT-RUOYI-PORTAL-LOGIN-001` · 阶段：6/7 · 状态：Completed（实现）

| TASK | 目标 | 文件范围 | 状态 |
|---|---|---|---|
| TASK-PORTAL-LOGIN-01 | 对齐 Admin Cookie/Bearer client、错误和 401 | `apps/portal-web/src/shared/api/client.ts`、`stores/session.ts` | Completed |
| TASK-PORTAL-LOGIN-02 | 重做 RuoYi 登录视觉、验证码、表单和状态 | `features/identity/LoginPage.vue`、Portal 背景资源 | Completed |
| TASK-PORTAL-LOGIN-03 | 统一路由守卫、退出和 Interview WebSocket Token 读取 | `router/index.ts`、`App.vue`、`AccountPage.vue`、`InterviewRoomPage.vue` | Completed |
| TASK-PORTAL-LOGIN-04 | 构建、静态扫描、浏览器 UI 与移动端验证 | `verification.md`、`acceptance.md` | InProgress（真实后端链路 Blocked） |

执行边界：不修改 `backend/`、Admin、Provider 或 Interview 业务 API；不执行 Git、部署或发布。
