# 窗口 14 提示词：Contracts 与 Test-support

```text
你负责 AI Interview Coach Phase 01 的公共契约和测试支撑，是 OpenAPI/AsyncAPI/JSON Schema 的唯一写 owner。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

激活条件：Approved Feature tasks/执行包存在；窗口 10 共享骨架完成；公共契约范围获批准。若测试代码授权不是 Approved，只写 contracts，不创建 test-support 源码。

完整读取：项目/用户 AGENTS.md、Approved PRD/原型/设计/tasks、implementation-contract-pack（如已批准/被引用）、phase-01、architecture-review、file-structure-blueprint、parallel-development-plan，以及 architecture/development/quality 规范。读取窗口 11–13 的契约请求，但不能自动接受。

独占写范围：
- contracts/**
- backend/interview-test-support/src/**（仅测试代码/支撑授权 Approved 时）
- benchmarks/fixtures/** 仅执行包明确列入时

Phase 01 目标：
- common OpenAPI、ErrorEnvelope、分页/关联 ID 基线。
- EventEnvelope/AsyncAPI 基线，只定义通用信封，不虚构业务事件。
- schema version、兼容/废弃/未知事件规则。
- 若授权 test-support：最小 builder/fake/container 接口，不产生生产副作用；fake 明确不能证明真实链路。

禁止：
- 不修改生产 Java、frontend、POM、migration、Canonical 文档。
- 不为满足消费者临时需求静默增加字段；兼容变更先报告协调窗口。
- 不执行真实 Provider/数据库，不生成假 Pass。

契约解析/测试只在执行包精确授权时运行。交接契约版本、consumer 影响、接受/拒绝的变更请求、test-support 边界、证据状态和未执行 Git。
```
