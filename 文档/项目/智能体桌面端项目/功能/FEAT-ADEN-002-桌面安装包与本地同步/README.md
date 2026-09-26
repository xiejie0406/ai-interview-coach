# Aden 桌面安装包与本地同步

> 文档类型：Feature 控制页兼需求、设计与任务事实源；Feature：FEAT-ADEN-002；版本：0.1.0；文档状态：Draft  
> owner：用户决定验收与发布；Codex 实施本地测试切片；创建与更新：2026-09-26  
> 风险：仅合成数据、本机安装和回环地址的测试切片为 L2；正式签名和线上发布另行评估  
> 决定依据：用户要求用安装版验收，并要求本机安装后可查看和修改代码文件、同步源码变更。

## 目标与边界

原有 `aden-desktop` 只生成 `out/` 应用 bundle，未生成 Windows 安装包。本 Feature 增加独立的 **Aden Local Test** 安装版和正式形态的打包配置。本地版使用可见的 `resources/app/out` 编译代码目录，支持修改安装目录内的编译后 JS/CSS/HTML，或在源码目录改 `.ts/.vue` 后构建并同步到安装目录。正式形态使用 ASAR，不允许 HTTP API；ASAR 本身不是签名或防篡改保证。

本地版只连接本机精确回环地址，使用合成账号、隔离服务和数据。它不随安装包内置 RuoYi、MySQL、Redis、Python Runner，也不提供真实账号、对外发送或自动更新。正式版的签名证书、HTTPS 服务地址、更新源、发布与用户接受不在本切片内。

## 需求和验收条件

| ID | 优先级 | 条件 |
| --- | --- | --- |
| ADEN-PKG-AC-01 | P0 | Windows 本地测试安装包能安装到当前用户范围，独立于正式 Aden；安装目录可找到 `resources/app/out` 的编译代码文件，双击可启动。 |
| ADEN-PKG-AC-02 | P0 | 本地测试安装版读取同目录 `local-test.json` 的 API 地址；只接受精确 loopback 主机的 HTTP 或 HTTPS，拒绝 LAN、公网地址、凭据、查询和片段。发布形态仍只接受显式 HTTPS。 |
| ADEN-PKG-AC-03 | P0 | 修改源码后执行同步命令，目标安装目录中的 `out` 与新构建内容一致；不会覆盖其他安装路径或删除无关内容。运行中的程序先退出再同步。 |
| ADEN-PKG-AC-04 | P0 | 使用实际安装版完成启动安全烟测，确认页面从安装目录加载，renderer 无 Node 能力；连接隔离合成服务后执行登录主流程。未具备环境的步骤保留 NotRun/Blocked。 |
| ADEN-PKG-AC-05 | P1 | 正式形态可构建成 ASAR 安装包；正式上线前另验签名、HTTPS 配置、更新/回退和用户验收。 |
| ADEN-PKG-AC-06 | P1 | 仓库提供手动触发的 Windows 云端构建流程，执行同一 release candidate 构建并上传安装器与哈希；不自动发布。只有实际云端运行和产物回读后才可记为验证通过。 |

## 技术决定与实施范围

- 继续使用 Electron/Vue 三层结构，不修改 RuoYi、Python、公共契约或登录协议。用 `electron-builder` 生成 Windows NSIS 当前用户安装包。
- 构建时固化 `local-test` 或 `release` 渠道。仅 local-test 渠道读取安装目录内的 `local-test.json`；校验逻辑继续冻结 API Origin 和拒绝重定向。运行时环境变量不得将 release 渠道降级为 HTTP。
- local-test 使用独立 appId、应用名、数据目录和 `asar: false`；release 使用另一个 appId 和 `asar: true`。本地测试包不包含源码 `.ts/.vue`，也不从安装目录动态执行任意源码。
- 同步脚本只接受经检查的本机 Local Test 安装目录，构建成功后复制 `out/`，保留配置与用户数据；失败停止，不删除既有安装包或用户资料。
- 验证依次为类型/单测、两渠道构建、安装目录回读与启动烟测、隔离合成登录。签名和发布另有门禁。
- 线上打包仅用手动 GitHub Actions workflow 生成候选安装器并上传到该次运行的 artifact；它与正式对外发布分开。当前没有 Git 推送授权，云端运行保持 NotRun。

## 阶段与任务

当前主阶段：8 审查验证 / InProgress（原轮本地安装版 6 Pass、云端构建 1 NotRun；2026-09-26 并行环境复验为 5 Pass、同步重验 1 Blocked、云端构建 1 NotRun，用户接受决定待确认）。当前请求授权本地依赖安装、构建、当前用户范围的测试安装及合成环境验证；未授权 Git 写操作、真实账号/业务数据、发布或签名。

| 任务 | 内容 | 状态 |
| --- | --- | --- |
| ADEN-PKG-TASK-01 | 两渠道构建与安全配置 | Completed：本地安装器和 ASAR 发布候选均已构建；发布候选未签名 |
| ADEN-PKG-TASK-02 | 安装目录源码同步命令与使用说明 | Completed：同步后哈希与启动烟测通过 |
| ADEN-PKG-TASK-03 | 构建、安装、启动和合成登录证据 | Completed：安装版合成登录、成功、取消和退出已逐项截图复验 |
| ADEN-PKG-TASK-04 | 手动云端 Windows 打包工作流 | 配置已写入并通过 YAML 静态解析；未推送和未运行 |

最新环境归属与定向结果见[并行环境复验清单](证据/2026-09-26-并行环境复验/验收清单.md)及[HTML 报告](证据/2026-09-26-并行环境复验/verification-report.html)；原轮结果保留于[验证记录](验证记录.md)、[原轮清单](证据/2026-09-26-安装版验收/验收清单.md)和[原轮报告](证据/2026-09-26-安装版验收/verification-report.html)。FEAT-ADEN-001 的原有打包态 HTTPS 规则仍适用于 release；local-test 是仅供合成数据的独立安装版。
