# RuoYi 收敛实施记录：Wave 02

> 文档类型：开发记录
> 文档状态：Draft
> 任务：PHASE-19 / TASK-RUOYI-01~07
> 风险等级：L3
> 当前阶段：7. 开发实现
> 阶段状态：InProgress
> 证据结果：模块编译 Pass；整包构建 Pass；运行启动 Blocked（MySQL 凭据）；业务验证 NotRun
> 更新时间：2026-08-23

## 实际修改

- 将旧 adapter 的 REST、WebSocket、PostgreSQL persistence、Provider、storage、crypto、job 适配器迁入 `ruoyi-interview/controller` 与 `ruoyi-interview/infrastructure`，排除旧 identity adapter。
- 新增 RuoYi `SecurityContext` 请求上下文工厂、业务 tenant resolver、主体 guard、权限注解和 PostgreSQL 独立 DataSource/事务配置。
- 将错误边界改为 RuoYi `AjaxResult` envelope；未配置 Provider/敏感字段密钥时显式返回不可用，不伪造成功。
- 新增 PostgreSQL Flyway 所有权基线 `V1__ruoyi_interview_business_ownership.sql`，只建立 `platform` 业务绑定/tenant/membership 表，不创建密码、角色、权限或 session 表。
- 新增 MySQL `sys_menu` 权限增量脚本 `sql/ruoyi-interview-permissions.sql`；脚本不默认授予角色。
- portal-web、admin-web、mobile 的开发代理均指向 `127.0.0.1:8081`；portal/mobile 业务请求使用 RuoYi Token；移除业务请求的 `AIC_SESSION`/`AIC-XSRF-TOKEN` 发送逻辑。
- 旧 `InterviewCoachApplication` 增加默认拒绝启动保护；旧 `SecurityConfiguration` 仅在显式 legacy 开关下装配；未删除旧目录。
- 修复正式 REST 与未就绪保护 Controller 的重复路径：保护 Controller 仅在正式业务 REST 关闭时装配。
- 新增 `platform.profile_version` 业务表和 `ProfileAccessPort`，面试档案引用统一使用 `ruoyi_user_id BIGINT`，移除新模块对旧 `identity.profile_version` 的读取。
- WebSocket 配置接入迁移后的 `VoiceWebSocketHandler`/`VoiceCaptureCoordinator`；连接仍由 RuoYi Token/SecurityContext 与一次性 ticket 双重约束。
- 统一 Servlet fallback 错误为 RuoYi `AjaxResult` envelope；补齐 `interview:session:edit`、`interview:session:submit` 权限菜单增量。
- Admin Vite 的平台代理也固定为 `http://127.0.0.1:8081`。

## 验证记录

| EV | 命令/步骤 | 结果 | 事实和限制 |
|---|---|---|---|
| EV-02-01 | `mvn -pl ruoyi-interview -am -DskipTests compile` | Pass | `ruoyi-interview` 编译 428 个源文件；tests skipped |
| EV-02-02 | `mvn -DskipTests package` | Pass | RuoYi reactor 与 `ruoyi-admin` repackage 成功；tests skipped |
| EV-02-03 | `java -jar ruoyi-admin/target/ruoyi-admin.jar` | Blocked | 唯一启动类初始化 `http-nio-8081`，随后 MySQL `root` 无密码连接被 `Access denied` 拒绝；未启动旧 AI backend |
| EV-02-04 | `rg` 静态扫描启动类/安全链/旧身份入口 | Pass（静态） | RuoYi 只有一个启用安全链；旧链保留为兼容源且默认禁用；运行态尚未取得有效凭据复核 |
| EV-02-05 | 前端代理与认证引用扫描 | Pass（静态） | `portal-web`、`admin-web`、`mobile` 的代理指向 `127.0.0.1:8081`；真实浏览器 UAT 未运行 |
| EV-02-06 | `mvn -pl ruoyi-interview -am -DskipTests compile`（修复后） | Pass | `ruoyi-interview` 编译 430 个源文件；tests skipped |
| EV-02-07 | `mvn -DskipTests package`（修复后） | Pass | RuoYi reactor 与 `ruoyi-admin` repackage 成功；tests skipped |
| EV-02-08 | `java -jar ruoyi-admin/target/ruoyi-admin.jar`（修复后） | Blocked | 唯一启动类初始化 `http-nio-8081` 后，MySQL 因当前 shell 未注入凭据返回 `Access denied ... (using password: NO)`；进程退出 |
| EV-02-09 | `npm run build:prod`（`apps/admin-web`） | Pass | 2549 modules transformed，Vite production build 成功 |
| EV-02-10 | `npm run build`（`apps/portal-web`） | Pass | `vue-tsc --noEmit` 与 Vite build 成功；存在既有 chunk size warning |
| EV-02-11 | `npm run build:h5`（`apps/mobile`） | Pass | uni H5 `DONE Build complete` |

## 未执行/阻塞

- 未执行 PostgreSQL Flyway、业务表回填、跨库读写、Provider/ASR/TTS 真实调用、Quartz 任务、SSE/WebSocket 握手、RBAC 403、前端浏览器 UAT。
- 未执行 MySQL 权限脚本；需平台管理员确认 `sys_menu.menu_id` 与角色授权窗口后执行。
- 启动验证最小解除动作：在本地受控 shell 注入正确 `RUOYI_DB_PASSWORD`，重启唯一 RuoYi 进程并保留退出码/端口/HTTP 证据；不得把密码写入日志或仓库。
- WebSocket 当前只完成 RuoYi 容器内 wiring 和静态主体/ticket 校验；握手、断线、重连、取消及 PG 写入仍需运行时证据。
- `frontend` 主 React 工程的既有 TypeScript 构建失败仍未修复；该问题不归因于本轮 RuoYi 配置改动。

## 结论

本 Wave 已完成迁移骨架、适配器边界和默认停用保护，不能称为业务迁移完成。当前 Feature 仍为 `InProgress`；下一门是取得本地凭据后的单体启动与 REST/SSE/WebSocket/PG/RBAC/前端逐项证据。
