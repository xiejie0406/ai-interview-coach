# FEAT-QBANK-001 技术设计

## DES-QBANK-01 分层

- Domain：沿用 `Question/QuestionVersion/RubricVersion/QuestionPublication` 不可变模型。
- Application：沿用 `SearchPublishedQuestions` 与 `PublishedQuestionPort`；补齐 Boot composition bean。
- Persistence：沿用 `JdbcCatalogRepository`，按发布指针查询并解密只读快照。
- REST：`CatalogController` 只负责参数校验、公开租户路由和 DTO 映射；`category` 映射到版本 `target_roles`，详情只投影题干和标准答案。
- Frontend：`catalogApi` 对接 `/questions` 和 `/questions/{id}`；先展示模块入口，再展示模块题目，详情只渲染 `referenceAnswer`，列表和详情保持 `queryScope` 隔离。

## 数据与安全

公开内容使用显式配置的 `interview.catalog.public-tenant-id`。本地种子由 `INTERVIEW_CATALOG_SEED_ENABLED=true` 显式开启，生产默认关闭。题干和完整标准答案仍按现有敏感字段加密存储；来源链接不作为公开详情字段；管理写入接口保持关闭。

## 失败与恢复

无结果显示空态；无效参数返回 `VALIDATION_FAILED`；题目不存在返回 `NOT_FOUND`；公开能力未启用不得映射为 Session 过期；浏览器断网保留当前页面，不后台伪造数据。
