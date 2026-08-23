# 窗口 10 提示词：共享脚手架与版本协调

```text
你负责 AI Interview Coach Phase 01 的共享脚手架，是代码波次唯一允许修改根构建与 module POM 的窗口。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

先检查激活条件：
1. 找到 Approved 的 Phase 01 Feature 控制页、design.md、tasks.md 和执行包。
2. PRD/原型/技术设计为执行包引用的 Approved 版本。
3. 执行包明确授权本窗口的代码/配置文件；具体版本 DEC-033 已关闭。
4. 若测试代码、构建、测试、启动或 Git 没有 Approved，不得执行对应动作。

条件不满足就停止，只报告缺口，不创建文件。

完整读取：项目/用户 AGENTS.md、项目规范索引、上述 Approved Feature 包、docs/phases/phase-01-independent-foundation.md、parallel-development-plan.md、file-structure-blueprint.md、环境说明和 development/quality/architecture 规范。

独占允许范围（最终以 tasks.md 为准）：
- pom.xml
- backend/pom.xml
- backend/interview-domain/pom.xml
- backend/interview-application/pom.xml
- backend/interview-adapters/pom.xml
- backend/interview-boot/pom.xml
- backend/interview-test-support/pom.xml
- .editorconfig、.gitignore、.env.example（仅执行包明确授权时）
- backend/interview-boot/src/main/resources/application.yaml 的安全空基线
- 创建批准的空目录；不要放业务类

目标：
- 建立五 module 单向依赖与统一 Java/插件/依赖版本。
- domain 不引入 Spring/JPA/HTTP；application 只依赖 domain；adapters 依赖 application/domain；boot 负责装配；生产 module 不依赖 test-support。
- 不引入第二套 HTTP、JSON、数据库或 AI 框架。
- 不添加未经决策的 Provider SDK、Redis、对象存储、支付或前端依赖。

禁止修改：各 module 的 Java/测试源码、frontend/**、contracts/**、docs/product/**、docs/architecture/**、其他窗口独占文件。

验证：只有执行包明确批准精确命令时才运行；否则只做文件与依赖声明的只读自审，结果写 NotRun。禁止 Git。

交接必须给：实际文件、锁定版本/来源、module dependency 表、未运行项、窗口 11–14 的可启动信号、任何需要共享变更的停止项。
```
