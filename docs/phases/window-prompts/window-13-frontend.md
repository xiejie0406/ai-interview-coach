# 窗口 13 提示词：React 前端壳

```text
你负责 AI Interview Coach Phase 01 的独立 React + TypeScript + Vite 前端壳。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

激活条件：Approved Feature tasks/执行包明确前端框架、包管理器、Node 版本、允许文件和代码授权；窗口 10 共享基线完成。条件不满足则停止。

完整读取：项目/用户 AGENTS.md、Approved PRD/原型/设计/tasks、phase-01、file-structure-blueprint、parallel-development-plan、contracts 当前基线，以及 development/architecture/quality 规范。保护工作区已有文件。

独占写范围（以 tasks.md 为上限）：
- frontend/src/**
- frontend/package.json、vite.config.ts、tsconfig*.json、锁文件仅在执行包明确归本窗口时
- 仅明确授权的前端测试文件

Phase 01 目标：
- public/auth/workspace/admin 路由壳，不实现业务页面。
- 统一 API client、ErrorEnvelope 映射、RootErrorPage、RouteErrorPage、AsyncState、PermissionDenied。
- 默认同源，不保存 Provider Key/长期 token，不猜业务状态。
- 基础键盘焦点、语义标签、对比度和窄屏壳。
- 所有占位必须写明“规划/未实现”，不能做成假功能。

禁止：
- 不修改 backend、contracts、根/后端 POM、PRD/架构。
- 不添加第二套路由/状态/API 客户端；不自行扩充公共错误码。
- 不接真实后端/Provider，不把 Key 放 localStorage。

构建/类型检查/测试只按 Approved 命令执行；未授权则 NotRun。交接实际文件、路由表、契约依赖、无障碍考虑、未运行项和契约变更请求。
```
