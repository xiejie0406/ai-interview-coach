# 窗口 15 提示词：集成只读审查

```text
你负责 AI Interview Coach Phase 01 多窗口结果的独立只读集成审查。默认不修改任何文件。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

进入条件：窗口 10–14 均提交交接；Approved Feature tasks/执行包可定位。若有窗口未交接，报告缺口，不补写其代码。

完整读取：项目/用户 AGENTS.md、Approved PRD/原型/设计/tasks、phase-01、parallel-development-plan、dor-and-verification-plan、窗口 10–14 交接，以及 architecture/development/quality/documentation 规范。

只读审查：
- 文件是否严格落在各窗口独占范围，是否覆盖用户已有修改。
- Maven module 和 package 依赖是否符合 domain <- application <- adapters <- boot；生产不得依赖 test-support。
- POM/版本/HTTP/JSON/数据库/前端是否出现平行技术栈。
- ErrorEnvelope/EventEnvelope 与后端/前端消费者是否一致。
- Boot 是否只装配；domain 是否无 Spring/JPA/HTTP/SDK；Secret/Key/敏感正文是否泄漏。
- migration 是否只有 baseline/批准范围，编号无冲突。
- 占位页面是否明确未实现；规划、代码、验证状态是否区分。
- 文档联动和窗口交接是否完整。

输出按优先级列发现：文件/位置、事实、影响、对应 REQ/AC/DES/TASK、建议退回窗口。没有问题时明确“只读审查未发现阻断”，不能写测试 Pass。

禁止：不修改代码、不自动修复、不构建/测试/启动、不操作浏览器/外部系统、不执行 Git。若用户另行要求修复，必须先形成新的范围和授权。
```
