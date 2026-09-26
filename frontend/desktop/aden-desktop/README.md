# Aden Desktop

Aden 的 Windows 桌面操作壳。当前 `0.2.0` 已实现 main / preload 安全传输基座，
以及 renderer 登录、工作区选择、共享任务中心、任务详情和能力总览。链路包括 RuoYi
内存登录会话、受限 REST、可恢复 SSE、epoch 隔离和有界 IPC。不连接真实微信、
电商账号、ERP、模型或外部发送通道。

## 技术基线

- Vue 3 + TypeScript + Vite：与仓库现有前端保持同一技术体系。
- Electron：只承载窗口、托盘和后续受限本机 IPC；渲染进程启用 sandbox、
  `contextIsolation`，并关闭 `nodeIntegration`。
- RuoYi：通过版本化 API 提供身份、权限、任务、回执和只读投影；Token 仅驻留 main 内存。
- Python Runner：作为独立低权限进程接入；不会把任意 shell、路径或脚本能力暴露给页面。

## 本地检查

```powershell
npm install
npm test
npm run build
npm run smoke:electron
```

完整的合成端到端验收可直接运行：

```powershell
.\scripts\run-synthetic-e2e.ps1
```

脚本使用本机已安装的 MySQL 8 与 Redis 程序创建随机 loopback 端口的一次性实例，
并启动 RuoYi、Python Runner 和 Electron 完成成功任务与安全取消链路；不需要 Docker
Desktop，也不会启动或修改 Windows `MySQL80` 服务。默认会先执行后端 clean build 和
桌面端 build，结束后回收本次创建的临时进程与数据目录。

需要手工体验合成 CORE 时运行 `.\scripts\run-synthetic-e2e.ps1 -InteractiveUat`。
脚本先完成无人值守成功/取消链路，再在隔离库开启图片验证码，打开独立桌面窗口；
关闭该窗口后自动清理临时环境。若另一个进程占用后端目标 JAR，可在确认现有 JAR
版本适用且桌面 bundle 已构建后使用 `-SkipBuild -InteractiveUat`；这不构成本轮干净构建证据。

开发启动会创建本机桌面窗口：

```powershell
npm run dev
```

可用 `ADEN_API_BASE_URL` 指定 RuoYi 地址。未打包开发环境仅允许 loopback HTTP；release 打包环境必须显式配置 HTTPS 地址。local-test 安装版只读取安装目录 `resources/app/local-test.json` 中的 API 地址，只允许精确 loopback 主机；两种打包渠道均拒绝 LAN / 公网 HTTP、凭据、query、fragment。renderer CSP 禁止业务网络；所有 REST / SSE 都由 main 向冻结 Origin 发起并拒绝跳转，preload 不暴露任意 URL、header、channel 或 Token。

## Windows 本地测试安装版

在本目录运行 `npm ci` 后执行：

```powershell
npm run pack:local
```

安装器生成在 `dist/local-test/Aden-Local-Test-0.2.0-Setup.exe`。安装后默认位于 `%LOCALAPPDATA%\Programs\Aden Local Test`，可直接查看或修改 `resources\app\out` 下的**编译后** JS、CSS、HTML；修改后退出并重开程序。`.ts` / `.vue` 原始源码仍在本工程目录，修改它们后先退出安装版，再执行：

```powershell
pwsh -NoProfile -File .\scripts\sync-local-install.ps1
```

脚本只同步 `out`，并将旧版保存为安装目录内的 `out-backup-时间戳`。安装版的 API 地址写在 `resources\app\local-test.json`，默认 `http://127.0.0.1:8081`；必须先启动兼容的本机 RuoYi / Aden 服务才能登录。隔离合成环境可用 `pwsh -NoProfile -File .\scripts\run-synthetic-e2e.ps1 -SkipBuild -InstalledDesktop -InteractiveUat` 启动，其中脚本会先跑完整 E2E，再打开可手工操作的已安装窗口并在关闭后清理临时服务。合成账号只适用于该隔离数据库，验证码由窗口显示；不要把它用于真实环境。

可运行 `npm run pack:release-candidate` 构建使用 ASAR 的未签名发布形态候选包。它要求明确的 HTTPS `ADEN_API_BASE_URL` 才能启动；目前没有签名、更新源和真实服务验收，因此不能直接对外发布。两种安装版都只包含 Electron UI，不内置 RuoYi、MySQL、Redis 或 Python Runner。

云端候选包使用仓库的 `.github/workflows/aden-desktop-windows-package.yml`：在该文件被提交并推送到 GitHub 后，可从 Actions 手动运行 `Aden desktop Windows package`，下载该次运行的安装器与 `SHA256.txt`。工作流只构建和上传临时 artifact，不签名、不部署、不创建 Release；当前工作区未推送，线上构建结果尚未验证。

当前版本是合成 CORE 技术切片：真实 Provider、第三方连接器、UI Automation、
签名、发布和真实业务数据验收均不在本阶段的“已完成”范围内。
