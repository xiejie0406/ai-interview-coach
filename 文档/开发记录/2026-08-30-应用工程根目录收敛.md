# Apps 根目录提升记录

> 文档类型：目录迁移记录
> 文档状态：Draft
> owner / 责任边界：执行人记录移动和验证事实
> 创建时间 / 更新时间：2026-08-30
> 产出阶段：8 审查验证 / 10 上线就绪准备
> 风险等级：L3（跨工程目录重组）

## 1. 范围与映射

用户明确要求将项目移动到主目录并删除 `apps/`。本次不平铺各工程内部文件，不删除任何 `apps/` 子工程，也不覆盖根目录已有文件。

| 移动前 | 移动后 |
|---|---|
| `apps/platform-backend/` | `platform-backend/` |
| `apps/admin-web/` | `admin-web/` |
| `apps/portal-web/` | `portal-web/` |
| `apps/mobile/` | `mobile/` |
| `apps/ruoyi-app/` | `ruoyi-app/` |
| `apps/ruoyi-backend/` | `ruoyi-backend/` |
| `apps/ruoyi-vue3-frontend/` | `ruoyi-vue3-frontend/` |

七个目标路径移动前均不存在。移动完成后删除空 `apps/` 目录。目录采用同盘移动，文件内容未重新生成；恢复方式是新建 `apps/` 并把七个目录按表反向移动。

## 2. 配套修改

- `platform-backend/bin/run-local.ps1`：根 `.env` 定位由 `../../..` 调整为 `../..`。
- 根 `README.md`：更新当前工程入口、范围和目录事实。
- `platform-backend/ruoyi-interview/README.md`：正式模块路径改为根目录结构。

历史规格、决策、阶段和验证记录中的 `apps/...` 路径保持原文，表示 2026-08-30 提升前的结构；当前路径以根 README 和本记录为准。

## 3. 执行与验证

- 移动前停止从旧路径运行的 Admin、Portal、RuoYi backend 进程。
- 首次移动时 `platform-backend` 被 Maven `spring-boot:run` 外壳占用；其他六个目录已移动，操作停止且无数据丢失。
- 结束仅属于本项目的残留 Maven 外壳后，`platform-backend` 移动成功，空 `apps/` 删除成功。
- 从新根目录启动后，Admin `5173`、Portal `5174`、RuoYi backend `8081` 均监听；三个根 URL 均返回 HTTP `200`，后端日志出现 `Started RuoYiApplication`。
- Git `add/commit/push` 未执行；目录移动保持为工作区差异。
