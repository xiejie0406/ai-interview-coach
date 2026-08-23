# RuoYi 收敛实施记录：Wave 01

> 文档类型：开发记录
> 文档状态：Draft
> 任务：PHASE-19 / TASK-RUOYI-02
> 风险等级：L3
> 当前阶段：7. 开发实现
> 阶段状态：InProgress
> 证据结果：Build Pass；Runtime Startup Fail；Tests NotRun
> 授权：用户于 2026-08-16 明确要求“开始落地”；仅覆盖本地模块骨架和桥接实现

## 1. 本轮目标

把 AI Interview Coach 作为 RuoYi 的正式 Maven 业务子模块接入唯一启动容器，先建立不引入第二套认证的模块边界。

## 2. 已修改范围

- `apps/platform-backend/pom.xml`
  - 聚合新增 `ruoyi-interview`。
- `apps/platform-backend/ruoyi-admin/pom.xml`
  - 由唯一 RuoYi Web 容器依赖 `ruoyi-interview`。
- `apps/platform-backend/ruoyi-interview/pom.xml`
  - 新增业务模块 POM，预留 JDBC、Validation 和 PostgreSQL 适配依赖。
- `apps/platform-backend/ruoyi-interview/src/main/java/com/ruoyi/interview/application/security/PrincipalRef.java`
  - 新增不含密码和 Token 原文的只读主体值对象。
- `apps/platform-backend/ruoyi-interview/src/main/java/com/ruoyi/interview/configuration/RuoYiPrincipalFacade.java`
  - 只从 RuoYi `SecurityContext` 读取当前主体。
- `apps/platform-backend/ruoyi-interview/README.md`
  - 固化模块边界、认证所有权和迁移期兼容源规则。
- `apps/platform-backend/ruoyi-interview/src/main/java/com/ruoyi/interview/domain/**`
  - 迁移旧 domain 中排除 `identity` 的 121 个 Java 文件。
- `apps/platform-backend/ruoyi-interview/src/main/java/com/ruoyi/interview/application/**`
  - 迁移旧 application 中排除 `identity` 和注册同意桥的 206 个 Java 文件。
- `apps/platform-backend/ruoyi-interview/src/main/java/com/ruoyi/interview/domain/governance/BusinessRole.java`
  - 将旧 membership role 降级为业务语义枚举，不作为 RuoYi RBAC 事实。
- `apps/platform-backend/ruoyi-interview/src/main/java/com/ruoyi/interview/application/security/ActivePrincipalGuard.java`
  - 新增应用层主体边界，后续由 RuoYi principal adapter 实现。
- `apps/platform-backend/ruoyi-interview/src/main/java/com/ruoyi/interview/application/security/BusinessTenantResolver.java`
  - 声明服务端业务 tenant 解析端口，不允许从请求参数推断工作区或权限。

## 3. 明确没有做的事

- 没有复制旧 `IdentityController`、`SecurityConfiguration`、`AIC_SESSION` 或旧 identity repository。
- 没有新增 Spring Boot main、`SecurityFilterChain`、登录/注册/退出接口。
- 没有写 MySQL 或 PostgreSQL，没有执行 Flyway。
- 已执行 Maven 打包，结果为 `BUILD SUCCESS`，但 tests skipped；未运行测试或前端验证。
- 没有删除或移动 `backend/`，也没有修改工作区已有的 RuoYi `application*.yml` 差异。

## 4. 本轮启动验证（2026-08-22）

- 执行：`java -jar apps/platform-backend/ruoyi-admin/target/ruoyi-admin.jar`
- 结果：`Fail`。
- 事实：唯一 RuoYi 启动类启动并初始化 `http-nio-8081`，随后 Druid 创建 `masterDataSource` 时访问 `jdbc:mysql://localhost:3307/ry-vue` 失败，根因为 `Connection refused`；进程退出。
- 环境只读检查：Redis `localhost:6379` 正在监听；MySQL `localhost:3307` 不可连接。
- 未发生：旧 AI 后端未启动，未执行数据库迁移、业务写入、账号操作、Git 操作或目录删除。

## 5. 第二次启动验证（2026-08-22）

- 变更：本地默认数据库端口由 `3307` 调整为 `3306`，保留 `RUOYI_DB_URL` 和 `RUOYI_DB_PASSWORD` 外部覆盖。
- 构建：`mvn -DskipTests package`，结果 `BUILD SUCCESS`；`ruoyi-interview` 编译 331 个源文件。
- 启动：已连接到 MySQL `localhost:3306`，随后因 `Access denied for user 'root'@'localhost' (using password: NO)` 失败并退出。
- 安全边界：未读取或输出密码，未执行数据库写入、迁移、账号操作、旧后端启动或 Git 操作。

## 6. 数据库初始化与最终启动验证（2026-08-23）

- 使用用户提供的本地数据库凭据通过环境变量完成验证，密码未写入仓库或日志。
- 仅创建原本不存在的空库 `ry-vue`，导入 `apps/platform-backend/sql/ry_20260417.sql`；其他数据库未修改。
- 核对结果：20 张表、2 个用户、2 个角色、85 个菜单、10 个部门。
- 修复 `mybatis.typeAliasesPackage`：排除 AI domain，补入 `com.ruoyi.common.core.domain.entity`，避免 alias 冲突并保留 RuoYi mapper 所需实体。
- `mvn -DskipTests package`：`BUILD SUCCESS`。
- RuoYi 单体启动成功，`http-nio-8081` 保持监听，Quartz 和 Redis 初始化完成。
- HTTP 冒烟：`/captchaImage` 为 `200`；未登录 `/getInfo` 为 `401`；错误验证码登录被拒绝。
- 证据边界：仅证明平台容器、RuoYi MySQL 基础 schema 和基础认证入口可运行，不代表 AI 业务迁移完成。

## 7. 下一步

1. 为 `ActivePrincipalGuard` 提供 RuoYi adapter，并完成权限编码映射。
2. 为 PostgreSQL 建立独立 DataSource 与事务边界，并补齐 `ruoyi_user_id` 迁移 SQL。
3. 将 REST/SSE/WebSocket 控制器改造成 RuoYi 权限注解入口。
4. 获得构建/测试授权后，先执行模块级编译，再做单服务启动和端到端验证。

## 8. 停止条件

出现第二套账号/session、前端主体覆盖、权限绕过、跨库不可解释不一致或秘密泄露时立即停止该波次。
