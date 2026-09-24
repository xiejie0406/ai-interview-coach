# 窗口 11 提示词：Domain 与 Application

```text
你负责 AI Interview Coach Phase 01 的 Domain/Application 代码，只能在共享脚手架窗口交接后启动。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

激活条件：Approved Feature tasks/执行包存在；窗口 10 已完成 POM/目录基线；本窗口代码范围已授权。否则停止。

完整读取：项目/用户 AGENTS.md、Approved PRD/原型/设计/tasks、phase-01、architecture-review、file-structure-blueprint、parallel-development-plan，以及 architecture/development/quality 规范。读取窗口 10 交接和当前目标目录，保护用户已有修改。

独占写范围：
- backend/interview-domain/src/main/java/**
- backend/interview-application/src/main/java/**
- 仅在执行包明确授权测试代码时：对应 src/test/java/**

Phase 01 目标只建立最小公共类型与端口，不实现 Identity/Catalog/Agent 业务：
- domain：基础值对象/标识接口、DomainEvent、确定性错误抽象；不得依赖 Spring/JPA/HTTP/SDK。
- application：ClockPort、IdGeneratorPort、CorrelationId/用例结果等批准的最小端口；不得放 Adapter 类型。
- 包结构预留逻辑域可以用 package-info/边界说明，但不创建空壳类海洋。

禁止：
- 不修改任何 POM、adapters、boot、frontend、contracts、migration 或 Canonical 文档。
- 不自行新增 DTO 字段、错误码、业务状态、框架/依赖。
- 发现契约缺口时提交给窗口 14/协调窗口，不能在本模块私建第二套。

验证仅按 Approved 执行包；未授权时不运行构建/测试。完成后只读自审依赖和文件范围，交接实际类/端口、未实现项、契约请求、证据状态和未执行 Git。
```
