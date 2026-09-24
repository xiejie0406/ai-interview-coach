# FEAT-QBANK-001 V2 验证记录

> 当前阶段：8 审查验证（局部运行证据已取得，整体仍未通过）  
> 总体证据结果：Blocked  
> 更新时间：2026-08-30

本轮已获本地构建、启动和浏览器验收授权。以下结果仅代表当前本地环境的实际证据；数据库题库数据、真实 Provider、登录态 Admin 流程和用户验收仍未通过，不能据此宣称 V2 整体完成。

## 1. V2 验证计划

| EV | 对应 AC | 计划验证 | 当前结果 |
|---|---|---|---|
| `EV-QBANK-V2-01` | AC-01 | 匿名公开列表/详情；匿名写入被拒；登录态不被公开 GET 错误清除 | Pass（本地）：列表与详情均返回 `200`；匿名详情不再误触发 `401`；个人答案写入仍受保护 |
| `EV-QBANK-V2-02` | AC-02 | 12 模块/600 题数量、关键词、难度、游标与版本回读 | Pass（本地回读）：7 页游标共 606 条，全部 `PUBLISHED`，12 模块均有数据；Flyway v10/v11 已执行；606 个来源版本为 `VERIFIED` |
| `EV-QBANK-V2-03` | AC-03 | 公开 DTO 字段白名单、下线/草稿不可见 | NotRun |
| `EV-QBANK-V2-04` | AC-04 | 个人答案加密、公共/用户租户隔离、他人不可读、系统答案不变 | NotRun |
| `EV-QBANK-V2-05` | AC-05 | 编辑角色草稿→版本→Rubric→提交审核 | NotRun |
| `EV-QBANK-V2-06` | AC-06 | 审核角色驳回/发布/下线、非法转换、RBAC、跨租户和 `If-Match` | NotRun |
| `EV-QBANK-V2-07` | AC-07 | 同 key 同 payload 重放、同 key 不同 payload 冲突、无重复审计事实 | NotRun |
| `EV-QBANK-V2-08` | AC-08 | Portal 无公共新增入口；Admin 加载/空态/错误/403/409 恢复 | Pass（局部）：Portal 题库页显示 12 固定模块和空态；Admin 登录页可渲染；受保护路由未登录跳转登录 |
| `EV-QBANK-V2-09` | AC-09 | 文档、契约和源码不再把普通用户公共新增视为 V2 能力 | NotRun |

## 2. 旧版证据迁移

2026-08-29 前记录的 `EV-QBANK-01..21` 属于旧栈/旧范围，状态统一为 `Superseded`，仅保留以下历史事实：旧版曾运行公开读取、三栏 UI、个人答案和普通用户公共新增流程。它们不能证明若依 V2 的 Admin 工作流、RuoYi JWT/RBAC、幂等、ETag、跨租户或当前构建状态。

其中旧 `EV-QBANK-20`“Chrome 新增公共题目”与 V2 明确冲突，已废止且不得重新启用。

## 3. 已执行验证与证据

| EV | 实际命令/步骤 | 结果/证据 |
|---|---|---|
| `EV-QBANK-RUN-01` | `mvn -pl ruoyi-admin -am package -DskipTests`（`apps/platform-backend`） | Pass：reactor 全部 SUCCESS，`ruoyi-admin` 执行 `spring-boot:repackage` |
| `EV-QBANK-RUN-02` | 使用新 JAR 启动 8081，开启 Flyway v10/v11、catalog/business REST，关闭 Provider/对象存储/后台任务 | Pass：日志出现 `Started RuoYiApplication`，当前实例保持运行 |
| `EV-QBANK-RUN-03` | `GET /`、`GET /captchaImage`、匿名题库/面试/consent 请求 | `/` 200；验证码 200；题库列表/详情 200；受保护面试/consent 返回 `code:401` |
| `EV-QBANK-RUN-04` | Playwright 访问 Portal 首页、`/questions`、模拟面试入口、语音入口、Admin 5173 | Pass（页面级）：页面可渲染；截图见 `output/playwright/portal-home.png`、`portal-questions.png`、`portal-interview.png`、`admin-login.png` |

## 4. 预定验证命令与环境

后端、Admin Web（5173）和 Portal Web（5174）当前保持运行。真实 Provider、对象存储、登录态 Admin 流程和用户业务 UAT 未执行；旧项目删除仍需独立备份、manifest 和二次确认。
