# Aden Desktop

Aden 的 Windows 桌面端。当前源码版本 `0.2.0` 包含登录、工作区选择、共享任务中心、
任务详情、能力总览，以及商品采集库：用户在 Chrome 自行打开京东商品详情页后，
通过扩展主动采集，桌面端查看快照和图片、手动新增与补充、删除／恢复、导出 XLSX／ZIP。
已实现当前用户 Native Host 桥接、图片分片保存和分页历史版本读取。

链路包括 RuoYi 内存登录会话、受限 REST、可恢复 SSE、epoch 隔离和有界 IPC。
京东 DOM 适配及图片来源域名仍需真实页面验证；源码实现、合成检查、安装版验收和真实京东
验收分别记录，不能从功能入口或安装包存在推断真实平台已经通过。用户自行搜索、登录和打开详情，
首版不自动搜索、翻页、滚动或切换规格。

## 技术基线

- Vue 3 + TypeScript + Vite：与仓库现有前端保持同一技术体系。
- Electron：只承载窗口、托盘和后续受限本机 IPC；渲染进程启用 sandbox、
  `contextIsolation`，并关闭 `nodeIntegration`。
- RuoYi：通过版本化 API 提供身份、权限、任务、回执和只读投影；Token 仅驻留 main 内存。
- Python Runner：作为独立低权限进程接入；不会把任意 shell、路径或脚本能力暴露给页面。

## 本地检查

```powershell
npm ci
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

构建机需要 Node.js 24、Python 3.12 和独立的 Native Host 构建虚拟环境。在本目录执行：

```powershell
npm ci
py -3.12 -m venv .\test-results\collector-host-build\.venv
$env:ADEN_HOST_BUILD_PYTHON = (Resolve-Path .\test-results\collector-host-build\.venv\Scripts\python.exe).Path
& $env:ADEN_HOST_BUILD_PYTHON -m pip install -r .\scripts\requirements-collector-host.txt
npm run pack:local
```

`scripts/package.mjs` 会先调用 `build-collector-host.ps1`，使用锁定版本的 PyInstaller 构建
`AdenCollectorHost.exe`，然后构建 Electron 并生成安装器。环境变量
`ADEN_HOST_BUILD_PYTHON` 必须指向上述虚拟环境中的 `python.exe`；不需要把依赖安装到全局 Python。

安装器生成在 `dist/local-test/Aden-Local-Test-0.2.0-Setup.exe`。安装后默认位于 `%LOCALAPPDATA%\Programs\Aden Local Test`，可直接查看或修改 `resources\app\out` 下的**编译后** JS、CSS、HTML；修改后退出并重开程序。`.ts` / `.vue` 原始源码仍在本工程目录，修改它们后先退出安装版，再执行：

```powershell
pwsh -NoProfile -File .\scripts\sync-local-install.ps1
```

脚本只同步 `out`，并将旧版保存为安装目录内的 `out-backup-时间戳`。**它不会更新 Native Host、
管道脚本、注册脚本或 Chrome 扩展。** 修改这些组件或跨组件协议后，需要重新打包并安装；
扩展源码变化还需在 Chrome 扩展管理页重新加载对应扩展。仅同步 `out` 不能作为这些变化的安装版验证。

安装版的 API 地址写在 `resources\app\local-test.json`，默认 `http://127.0.0.1:8081`；必须先启动兼容的本机 RuoYi / Aden 服务才能登录。隔离合成环境可用 `pwsh -NoProfile -File .\scripts\run-synthetic-e2e.ps1 -SkipBuild -InstalledDesktop -InteractiveUat` 启动，其中脚本会先跑完整 E2E，再打开可手工操作的已安装窗口并在关闭后清理临时服务。合成账号只适用于该隔离数据库，验证码由窗口显示；不要把它用于真实环境。

可在同一 Host 构建环境中运行 `npm run pack:release-candidate` 构建使用 ASAR 的未签名发布形态候选包。
它要求明确的 HTTPS `ADEN_API_BASE_URL` 才能启动；签名、更新源和真实服务验收仍是后续发布条件。
两种安装包均包含 Electron UI、独立 `AdenCollectorHost.exe`、管道代理及注册／卸载脚本，位于
`resources\collector`。**安装端不需要另外安装 Python 或 PyInstaller。** 安装器在当前用户范围注册 Host；
注册冲突会明确提示，不覆盖另一安装的注册。包内没有 RuoYi、MySQL、Redis 或完整的合成任务 Python Runner。

## 启用商品采集

1. 后端通过 Aden 专用迁移入口完成 V5 迁移，加载 `ruoyi-backend/sql/aden-collection-permissions.sql` 的权限定义，
   按实际角色授予 `aden:collection:read/capture/create/edit/delete/restore/export` 中需要的权限，并具备对应 Workspace 成员资格。
   权限 SQL 不自动给正式角色授权；普通应用启动只校验 Schema，不自动迁移。
2. 配置后端采集功能与受控存储目录。附件保存到服务端数据目录，不能放进桌面安装目录：

   ```yaml
   aden:
     collection:
       enabled: true
       storage-root: D:/AdenData/collection
       workspace-quota-bytes: 1073741824
   ```

   默认关闭采集。`fixture-origin` 只允许在隔离测试环境填写一个精确的
   `http://127.0.0.1:端口` 或 `http://localhost:端口`；正式环境留空。
3. 安装 Aden 并登录、选择工作空间。当前 Chrome 扩展使用开发通道，需要用户在 Chrome 的扩展管理页启用开发者模式，
   “加载已解压的扩展程序”选择相邻的 `frontend/desktop/aden-collector-extension` 目录。
   安装 Aden 不会静默安装扩展；开发 ID 和 Host 信息见[扩展说明](../aden-collector-extension/README.md)。
4. 用户打开支持的商品详情页，点击扩展工具栏授权当前页，在面板连接 Aden 并完成首次配对，之后点击采集。
   商品库只把服务端已确认的内容显示为已保存，图片失败或未加载内容会显示缺失；不会自动绕过验证或限制。

真实京东页面、来源允许范围和实际图片下载能力尚未完成验收。安装版与合成页面的检查不能代替真实平台试点。

## 云端候选构建

云端候选包使用仓库的 `.github/workflows/aden-desktop-windows-package.yml`：在相关变更进入 GitHub 后，
从 Actions 手动运行 `Aden desktop Windows package`，下载该次运行的安装器与 `SHA256.txt`。
工作流使用 Windows 2022、Node.js 24、Python 3.12，在 runner 临时目录创建虚拟环境，安装
`scripts/requirements-collector-host.txt`，通过 `GITHUB_ENV` 设置 `ADEN_HOST_BUILD_PYTHON` 后再打包。
包检查要求同时存在 ASAR、Host 可执行文件及配套脚本；文件存在检查不等于安装与协议运行验收。
工作流仅 `workflow_dispatch` 触发，只上传保留 7 天的 artifact，不签名、不部署、不创建 Release。
本次修改没有运行线上 CI，其成功状态仍需实际运行记录确认。

CORE 合成任务与商品采集库是独立能力；真实 Provider、微信／ERP 连接器、UI Automation、
签名、发布和真实业务数据验收不能从当前采集库实现推断完成。
