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

开发启动会创建本机桌面窗口：

```powershell
npm run dev
```

可用 `ADEN_API_BASE_URL` 指定 RuoYi 地址。开发 / 测试环境仅允许 loopback HTTP；打包环境必须显式配置 HTTPS 地址，缺失或非法时显示启动错误并退出。renderer CSP 禁止业务网络；所有 REST / SSE 都由 main 向冻结 Origin 发起并拒绝跳转，preload 不暴露任意 URL、header、channel 或 Token。

当前版本是合成 CORE 技术切片：真实 Provider、第三方连接器、UI Automation、安装包、
签名、发布和真实业务数据验收均不在本阶段的“已完成”范围内。
