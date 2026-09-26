import { spawn } from 'node:child_process'
import { resolve } from 'node:path'

const electron = resolve('node_modules/electron/dist/electron.exe')
const child = spawn(electron, [resolve('out/main/index.js')], {
  cwd: process.cwd(),
  env: {
    ...process.env,
    ADEN_DESKTOP_SMOKE: '1',
    ADEN_API_BASE_URL: 'http://127.0.0.1:8081'
  },
  stdio: ['ignore', 'pipe', 'pipe'],
  windowsHide: true
})

let stdout = ''
let stderr = ''
child.stdout.on('data', (chunk) => { stdout += chunk.toString() })
child.stderr.on('data', (chunk) => { stderr += chunk.toString() })
const timer = setTimeout(() => child.kill(), 20_000)
const exitCode = await new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code)))
clearTimeout(timer)
if (exitCode !== 0 || !stdout.includes('ADEN_ELECTRON_SMOKE_OK')) {
  throw new Error(`Electron 安全烟测失败（exit=${exitCode}）：${stderr.slice(0, 500)}`)
}
process.stdout.write('Electron 实窗安全烟测通过\n')
