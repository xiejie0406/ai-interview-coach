# 窗口 12 提示词：Adapters 与 Boot

```text
你负责 AI Interview Coach Phase 01 的 Adapters/Boot 代码，只能在窗口 10 共享基线完成后启动。

项目目录：D:\2025Ai\26-05-23\ai-interview-coach

激活条件：Approved Feature tasks/执行包、本窗口代码授权、窗口 10 交接均存在。若 application port 尚未稳定，只实现不依赖该 port 的最小入口并报告阻塞。

完整读取：项目/用户 AGENTS.md、Approved Feature 包、phase-01、architecture-review、file-structure-blueprint、parallel-development-plan、环境说明，以及 architecture/development/quality 规范。读取窗口 10/11 当前交接。

独占写范围：
- backend/interview-adapters/src/main/java/**
- backend/interview-boot/src/main/java/**
- backend/interview-boot/src/main/resources/db/migration/**（migration 编号先按执行包预约）
- 仅经明确授权的对应测试源码

Phase 01 最小目标：
- Spring Boot main 与装配，不放业务规则。
- health endpoint/adapter、CorrelationId filter、统一异常映射的实现壳，严格遵守窗口 14 契约。
- Flyway baseline/必要 schema 约定；不得创建未来业务表。
- 配置属性只读取本项目 INTERVIEW_*，不读取 PaiCLI 配置；Secret 不入文件/日志。

禁止：
- 不修改任何 POM、application.yaml 基线、domain/application、frontend、contracts、PRD/架构。
- 不引入真实 Provider、Redis、对象存储、支付或邮件。
- 不自行改变 ErrorEnvelope/EventEnvelope；提出契约变更请求。

验证只按执行包精确命令；未授权则 NotRun。交接实际 Java/migration、错误/健康契约实现、配置缺口、migration 预约、未运行项和未执行 Git。
```
