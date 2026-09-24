# FEAT-QBANK-001 技术设计（RuoYi V2）

> 文档状态：Draft  
> 风险等级：L3  
> 当前阶段：5 技术设计 / 7 开发实现  
> 证据结果：NotRun

## 1. 设计结论

题库只运行在 `apps/platform-backend/ruoyi-admin + ruoyi-interview`，管理端使用 `apps/admin-web`，用户端使用 `apps/portal-web`。不新增第二个 Spring Boot main、账号表、Session Cookie、CSRF 体系或独立权限实现。

```text
匿名 Portal ── GET /api/v1/questions ───────────────┐
Portal + RuoYi JWT ── my-answer ───────────────────┤
Admin + RuoYi JWT/RBAC ── /api/v1/admin/questions ─┼─> Catalog application/domain
                                                     └─> PostgreSQL + encryption/audit
```

## 2. 模块与职责

- `domain/catalog`：`Question`、`QuestionVersion`、`RubricVersion`、发布/审核事实及状态不变量。
- `application/catalog`：公开搜索/详情、个人答案、草稿/版本/Rubric、审核/发布/下线用例；不感知 HTTP。
- `infrastructure/persistence/catalog`：PostgreSQL 持久化、加密字段、游标、幂等记录和乐观锁。
- `controller/rest/catalog`：参数校验、RuoYi principal/RBAC、ETag、错误 envelope 和 DTO 映射。
- `apps/admin-web`：内容治理工作台；不得调用公开写接口代替 Admin 工作流。
- `apps/portal-web`：公开浏览和个人答案；不得展示公共题目创建入口。

## 3. API 与权限

| API | 身份 | 权限/语义 |
|---|---|---|
| `GET /api/v1/questions` | 匿名可用 | 只读当前发布投影 |
| `GET /api/v1/questions/{id}` | 匿名可用 | 登录时可叠加自己的答案投影 |
| `PUT /api/v1/questions/{id}/my-answer` | RuoYi JWT | 普通题库使用权限；主体从 JWT 取值 |
| `GET /api/v1/admin/questions*` | RuoYi JWT | `interview:question:list` |
| `POST /api/v1/admin/questions` | RuoYi JWT | `interview:question:add` + `Idempotency-Key` |
| `POST .../versions`、`POST .../rubrics` | RuoYi JWT | `interview:question:edit` + 并发/幂等约束 |
| `POST .../commands/submit-review` | RuoYi JWT | 编辑权限 |
| `POST .../commands/reject-review|publish|retire` | RuoYi JWT | 审核权限，不与普通编辑权限混用 |

RuoYi 本身使用 Bearer JWT 且关闭 CSRF，因此题库契约不再声明旧 `AIC_SESSION` 或 `X-AIC-XSRF-TOKEN`。公开 GET 的匿名放行需要在 Security allowlist 与方法权限上同时成立；不能使用类级 `@PreAuthorize` 把公开 GET 一并拦截。

## 4. 数据、版本与幂等

- 公共题库使用显式 `interview.catalog.public-tenant-id`，但用户答案同时保存用户租户和用户 ID，读取时必须完整匹配。
- 题目根使用聚合版本作为 ETag：响应 `ETag: "vN"`，写请求传 `If-Match: "vN"`。
- 题目内容版本与 Rubric 版本不可变；发布表只追加发布/下线事实并更新受控指针。
- `Idempotency-Key` 按主体、操作、资源和请求摘要绑定；同 key 不同 payload 返回冲突，同 key 同 payload 返回原结果。
- 12 模块/600 题导入必须校验 stable key、来源/许可、模块、难度、重复内容和答案结构；生产默认不自动 seed。

## 5. 状态与失败恢复

```text
DRAFT ──submit-review──> IN_REVIEW ──publish──> PUBLISHED ──retire──> RETIRED
  ▲                          │                      │
  └──────reject-review───────┘                      └─new version─> PUBLISHED_WITH_DRAFT
                                                         └─submit-review─> PUBLISHED_WITH_REVIEW
```

- 非法转换：409；不更改聚合或审计事实。
- ETag 冲突：409；Admin 重新加载快照，保留本地表单并让用户决策。
- 数据库/加密/配置未就绪：fail-closed，不回退内存成功实现。
- 匿名公开请求：不生成伪 principal，不读取个人答案。
- 已认证请求 JWT 无效：401；前端按统一登录策略处理，不能把所有公开接口错误解释为会话失效。

## 6. 兼容、回滚与删除前提

- 旧 `/questions` 公共 POST 不属于 V2，Portal 入口和后端能力均应移除；若为过渡保留路由，必须稳定拒绝且不得写库。
- 旧题库数据只作为迁移源，导入前生成数量、哈希和来源报告；未完成回读前不删除旧项目。
- 题库通过 EV/UAT 后才可进入旧项目删除准备；删除仍需精确清单、可回读备份和用户二次确认。

## 7. 验证设计

验证至少覆盖：公开匿名读取、JWT/RBAC、12 模块数据、游标、个人答案隔离、Admin 全工作流、非法转换、ETag、幂等重放、跨租户、错误 envelope、Portal/Admin UI 空态/错误态。当前均为 `NotRun`。
