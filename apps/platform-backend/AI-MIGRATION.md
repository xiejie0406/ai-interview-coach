# Platform Backend 迁移入口

本目录是基于 `../ruoyi-backend/` 完整上游创建的可改造后端工程。`../ruoyi-backend/` 保持只读基线；AI Interview Coach 领域、应用、适配器和契约将在本目录逐步接入。

本地开发默认使用 MySQL 8 实例 `localhost:3306/ry-vue`，数据库连接可通过 `RUOYI_DB_URL` 覆盖，服务端口通过 `RUOYI_SERVER_PORT` 覆盖。迁移目标是由 RuoYi 作为唯一后端进程，旧 AI 后端仅作为迁移期兼容源，不得与 RuoYi 并行作为正式运行入口。

当前已完成：

- 新增 `ruoyi-interview` Maven 子模块并由 `ruoyi-admin` 聚合。
- 迁移排除旧 `identity` 的 domain/application 代码。
- 新增 RuoYi `SecurityContext` 到 AI application 的只读主体桥接。
- `mvn -DskipTests package` 已通过；该结果仅证明 Maven 模块可构建，不代表运行时迁移完成。

当前未完成：

- REST/SSE/WebSocket、PostgreSQL persistence、Provider、Job 和前端切换。
- 旧 identity 接口与旧 AI 后端停止条件验证。
- 测试和数据库迁移仍未执行；上一次服务启动验证因配置使用 MySQL `localhost:3307` 连接被拒绝而失败，现已切换默认端口至 `3306`。

## 当前运行验证

- 时间：2026-08-22
- 启动入口：`ruoyi-admin/target/ruoyi-admin.jar`
- 唯一启动类：`com.ruoyi.RuoYiApplication`
- 监听初始化：曾初始化 `http-nio-8081`
- 最终结果：`Fail`，RuoYi 在创建 `masterDataSource` 时连接 `jdbc:mysql://localhost:3307/ry-vue` 收到 `Connection refused`，进程已退出，未留下 `8081` 监听。
- Redis：只读检查显示 `localhost:6379` 正在监听。
- 未发生：未启动旧 AI 后端，未执行数据库迁移、账号操作、数据写入、Git 操作或旧目录删除。

### 2026-08-22 第二次启动

- 已确认 `localhost:3306` 可连接，并将默认 RuoYi 数据库 URL 从 `3307` 改为 `3306`。
- `mvn -DskipTests package` 再次通过，`ruoyi-interview` 编译 331 个源文件。
- 启动结果：`Fail`，数据库连接已到达 MySQL，但凭据被拒绝：`Access denied for user 'root'@'localhost' (using password: NO)`。
- 密码未读取、未输出、未写入配置；应通过 `RUOYI_DB_PASSWORD` 注入正确凭据后再启动。

### 2026-08-23 数据库初始化与启动诊断

- 使用已提供的本地密码通过环境变量连接 MySQL `localhost:3306`，未写入仓库。
- 仅创建不存在的空数据库 `ry-vue`，未触碰其他数据库；导入 `sql/ry_20260417.sql` 成功。
- 初始化核对：20 张 RuoYi 表、2 个用户、2 个角色、85 个菜单、10 个部门。
- 启动已通过数据源初始化，随后发现 RuoYi 默认 `com.ruoyi.**.domain` alias 扫描误纳入 AI domain，两个 `PromptSchemaPin` alias 冲突；已将 alias 扫描收窄到 RuoYi 自有 domain 包，待重新构建启动验证。

### 2026-08-23 最终启动验证

- 修复 MyBatis alias 范围：保留 RuoYi 公共实体、system、quartz、generator domain，排除 AI domain。
- `mvn -DskipTests package`：`BUILD SUCCESS`。
- `java -jar ruoyi-admin/target/ruoyi-admin.jar`：启动成功并保持运行，唯一服务监听 `8081`。
- 数据源：RuoYi MySQL `localhost:3306/ry-vue` 初始化成功；Redis `localhost:6379` 可用。
- HTTP 冒烟：`GET /captchaImage` 返回 `200`；未登录 `GET /getInfo` 返回 RuoYi `401`；错误验证码登录被拒绝。
- 未输出验证码正文、Token、Cookie 或密码。
- 限制：AI REST/SSE/WebSocket、PostgreSQL、Provider、前端登录迁移和业务 UAT 仍未完成；当前只证明 RuoYi 容器与基础认证入口可运行。

## 尚未迁移的运行时能力

- REST 控制器及 RuoYi 响应/异常/权限注解入口。
- SSE、WebSocket 及 RuoYi 登录主体接入。
- PostgreSQL DataSource、Repository、Flyway 和 `ruoyi_user_id` 数据迁移。
- Provider、ASR、TTS、后台 Job、配置和健康检查适配器。
- portal-web、admin-web、mobile 的统一 RuoYi 登录态和 API 地址切换。
- 旧 identity 接口停用、旧 AI 后端停止条件和回滚演练。
- Maven 测试、运行时冒烟、RBAC、数据迁移、SSE/WebSocket、前端和 UAT 验证。

## 2026-08-23 Wave 02 实际差异

- `ruoyi-interview` 已接入旧 REST/WebSocket、persistence、Provider、storage、crypto 和 job 适配器的迁移副本；旧 identity 适配器未迁入。
- `InterviewInfrastructureConfiguration` 明确 PostgreSQL DataSource、`interviewTransactionManager`、PG `NamedParameterJdbcTemplate`、RuoYi principal facade 和服务端 business tenant resolver。
- `InterviewFlywayConfiguration` 明确 Flyway 只归 PostgreSQL，默认关闭；`V1__ruoyi_interview_business_ownership.sql` 只建立业务绑定/tenant/membership 表。
- `ApiErrorHandler` 使用 RuoYi `AjaxResult`；Provider/敏感密钥缺失返回明确不可用，不伪造成功。
- 旧 `InterviewCoachApplication` 默认拒绝启动；旧 `SecurityConfiguration` 只有显式 legacy 开关才装配。旧目录未删除。
- 三端开发代理/业务请求已统一到 `127.0.0.1:8081` 与 RuoYi Token；真实前端 UAT 尚未执行。
- 修复正式 REST 与“未就绪保护” Controller 的重复映射：仅在正式业务 REST 关闭时保留保护入口。
- 新增 `platform.profile_version` 业务档案基线，`ProfileAccessPort` 只按 `ruoyi_user_id` 校验归属；不再读取旧 `identity.profile_version`。
- RuoYi WebSocket 已接入迁移后的 `VoiceWebSocketHandler` 与一次性 ticket 校验；Provider/PG 未就绪时仍会明确失败，不伪造成功。

Wave 02 证据与阻塞详见 [`docs/development-records/2026-08-23-ruoyi-convergence-wave-02.md`](../../docs/development-records/2026-08-23-ruoyi-convergence-wave-02.md)。本轮修复后模块编译和整包构建仍为 Pass；单体启动仍因未注入 `RUOYI_DB_PASSWORD` 被 MySQL 拒绝，AI 业务端点、PG、Provider、Quartz、SSE、WebSocket 握手和 RBAC 仍未取得运行证据。
