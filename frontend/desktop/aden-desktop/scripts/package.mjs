import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'

const channel = process.argv[2]
if (process.platform !== 'win32') throw new Error('Aden 安装包当前只支持 Windows 构建')
if (channel !== 'local-test' && channel !== 'release') throw new Error('渠道必须为 local-test 或 release')

const env = { ...process.env, ADEN_PACKAGE_CHANNEL: channel }
const nativeHost = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', 'scripts/build-collector-host.ps1'], {
  cwd: resolve(import.meta.dirname, '..'), env, stdio: 'inherit', windowsHide: true
})
if (nativeHost.error) throw nativeHost.error
if (nativeHost.status !== 0) process.exit(nativeHost.status || 1)
const build = spawnSync('cmd.exe', ['/d', '/c', 'npm.cmd run build'], {
  cwd: resolve(import.meta.dirname, '..'), env, stdio: 'inherit'
})
if (build.error) throw build.error
if (build.status !== 0) process.exit(build.status || 1)

const builder = spawnSync('cmd.exe', [
  '/d', '/c',
  `node_modules\\.bin\\electron-builder.cmd --config builder.${channel}.cjs --win nsis`
], { cwd: resolve(import.meta.dirname, '..'), env, stdio: 'inherit' })
if (builder.error) throw builder.error
process.exit(builder.status ?? 1)
