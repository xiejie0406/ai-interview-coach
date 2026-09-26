import { spawn, spawnSync, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { join } from 'node:path'
import type { SessionController, SessionContext } from '../auth/session-controller'
import type { AdenApiClient } from '../transport/api-client'
import { assertCollectorSession, collectorUuid, COLLECTOR_EXTENSION_ID, MAX_COLLECTOR_FRAME, parseCollectorRequest } from './protocol'

export interface CollectorBridgeOptions {
  helperPath: string
  session: SessionController
  api: AdenApiClient
  confirmPair: (workspaceId: string) => Promise<boolean>
  openLibrary: () => void
  onFailure?: (code: string) => void
  pipePath?: string
}

/** 不监听TCP。带当前用户ACL的Windows管道由随包helper创建。 */
export class CollectorBridge {
  #child: ChildProcessWithoutNullStreams | null = null
  #context: SessionContext | null = null
  #connectionGeneration = 0
  #buffer = ''
  #queue: Promise<void> = Promise.resolve()
  #disposed = false

  constructor(readonly options: CollectorBridgeOptions) {}

  start(): void {
    if (process.platform !== 'win32' || this.#child) return
    const systemRoot = process.env.SystemRoot ?? 'C:\\Windows'
    let pipeName: string
    if (this.options.pipePath) {
      const prefix = '\\\\.\\pipe\\'
      if (!this.options.pipePath.startsWith(prefix)) throw new Error('采集管道路径必须是本机命名管道')
      pipeName = this.options.pipePath.slice(prefix.length)
    } else {
      const identity = spawnSync(join(systemRoot, 'System32', 'whoami.exe'), ['/user', '/fo', 'csv', '/nh'], {
        encoding: 'utf8', windowsHide: true, timeout: 5000
      })
      const sid = identity.stdout?.match(/S-1-5-(?:\d+-)*\d+/)?.[0]
      if (identity.status !== 0 || !sid) throw new Error('无法确定当前Windows用户')
      pipeName = `aden-collector-${sid}`
    }
    if (!/^aden-collector-[A-Za-z0-9-]{1,120}$/.test(pipeName)) throw new Error('采集管道名称不合法')
    const child = spawn(join(systemRoot, 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe'), [
      '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', this.options.helperPath, '-PipeName', pipeName
    ], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] })
    this.#child = child
    child.stdout.setEncoding('utf8')
    child.stdout.on('data', (data: string) => this.#receive(data))
    // 只报告稳定错误码，不把用户页面或PowerShell原始输出送入日志。
    child.stderr.on('data', () => this.options.onFailure?.('COLLECTOR_PIPE_ERROR'))
    child.on('error', () => this.options.onFailure?.('COLLECTOR_PIPE_UNAVAILABLE'))
    child.on('exit', () => {
      this.#context = null
      this.#child = null
      this.#connectionGeneration++
      if (!this.#disposed) this.options.onFailure?.('COLLECTOR_PIPE_STOPPED')
    })
  }

  dispose(): void {
    this.#disposed = true
    this.#context = null
    this.#connectionGeneration++
    this.#child?.stdin.end()
    this.#child?.kill()
    this.#child = null
  }

  #receive(data: string): void {
    this.#buffer += data
    if (Buffer.byteLength(this.#buffer) > MAX_COLLECTOR_FRAME * 3) {
      this.dispose()
      this.options.onFailure?.('COLLECTOR_PIPE_FRAME_LIMIT')
      return
    }
    let newline: number
    while ((newline = this.#buffer.indexOf('\n')) >= 0) {
      const line = this.#buffer.slice(0, newline).trim()
      this.#buffer = this.#buffer.slice(newline + 1)
      if (!line) continue
      let event: { kind?: string; message?: unknown }
      try { event = JSON.parse(line) } catch { this.dispose(); return }
      if (event.kind === 'connected' || event.kind === 'disconnected') {
        this.#context = null
        this.#connectionGeneration++
      } else if (event.kind === 'message') {
        const connectionGeneration = this.#connectionGeneration
        this.#queue = this.#queue.then(async () => {
          const response = await this.handle(event.message)
          if (connectionGeneration === this.#connectionGeneration && !this.#disposed) {
            const body = JSON.stringify(response)
            if (Buffer.byteLength(body) > MAX_COLLECTOR_FRAME) {
              this.#child?.stdin.write(JSON.stringify({ protocolVersion: 1, messageId: response.messageId, ok: false,
                error: { code: 'RESPONSE_TOO_LARGE', message: '响应过大，请在桌面端查看' } }) + '\n')
            } else this.#child?.stdin.write(body + '\n')
          }
        }).catch(() => { this.options.onFailure?.('COLLECTOR_BRIDGE_FAILED') })
      }
    }
  }

  async handle(value: unknown): Promise<Record<string, unknown>> {
    let messageId = 'invalid'
    try {
      const request = parseCollectorRequest(value)
      messageId = request.messageId
      const data = await this.#dispatch(request.type, request.payload)
      return { protocolVersion: 1, messageId, ok: true, data }
    } catch (error) {
      const message = error instanceof Error ? error.message : '采集操作失败'
      const code = error && typeof error === 'object' && 'code' in error && typeof error.code === 'string'
        ? error.code : 'COLLECTOR_REQUEST_REJECTED'
      return { protocolVersion: 1, messageId, ok: false, error: { code, message: message.slice(0, 300) } }
    }
  }

  async #dispatch(type: string, payload: Record<string, unknown>): Promise<unknown> {
    const { session, api } = this.options
    if (type === 'hello') {
      if (payload.extensionId !== COLLECTOR_EXTENSION_ID) throw new Error('扩展身份不匹配')
      const context = session.context()
      if (!context.authenticated || !context.workspaceId) throw Object.assign(new Error('请先登录 Aden 并选择工作空间'), { code: 'AUTH_REQUIRED' })
      const generation = this.#connectionGeneration
      if (!this.#context || !session.isCurrent(this.#context)) {
        if (!await this.options.confirmPair(context.workspaceId)) throw new Error('用户未允许插件连接')
      }
      const identity = await api.request<{ user?: { userId?: unknown } }>({ path: '/getInfo' })
      const principalId = String(identity.data.user?.userId ?? '')
      if (!/^[0-9]{1,20}$/.test(principalId)) throw new Error('无法确定当前账号身份')
      if (!session.isCurrent(context) || generation !== this.#connectionGeneration) throw Object.assign(new Error('连接期间会话已变化，请重新连接'), { code: 'SESSION_REVOKED' })
      this.#context = context
      return { connected: true, workspaceId: context.workspaceId, principalId, protocolVersion: 1,
        maxChunkBytes: 128 * 1024, maxFrameBytes: MAX_COLLECTOR_FRAME }
    }
    const bound = this.#context
    assertCollectorSession(bound, session.context())
    const base = `/api/v1/aden/collection/workspaces/${encodeURIComponent(bound.workspaceId!)}`
    if ('workspaceId' in payload) throw new Error('插件不能指定工作空间')
    if (type === 'library.open') { this.options.openLibrary(); return { opened: true } }
    if (type === 'capture.lookup') {
      if (typeof payload.sku !== 'string' || !/^\d{1,32}$/.test(payload.sku)) throw new Error('商品SKU非法')
      const response = await api.request({ path: `${base}/items/lookup?sku=${encodeURIComponent(payload.sku)}` })
      assertCollectorSession(bound, session.context())
      return response.data
    }
    let path: string
    let body: unknown = payload
    let method: 'POST' | 'GET' = 'POST'
    if (type === 'capture.prepare') {
      collectorUuid(payload.captureId)
      if (payload.source !== 'JD') throw new Error('插件只允许采集京东商品')
      path = `${base}/captures`
    } else {
      const captureId = collectorUuid(payload.captureId)
      if (type === 'capture.status') {
        path = `${base}/captures/${captureId}`
        method = 'GET'
        body = undefined
      } else if (type === 'capture.commit') {
        path = `${base}/captures/${captureId}/complete`
        const { captureId: _captureId, ...rest } = payload
        body = rest
      } else if (type === 'asset.chunk') {
        if (typeof payload.imageId !== 'string' || !/^[A-Za-z0-9_-]{1,80}$/.test(payload.imageId)) throw new Error('图片标识非法')
        path = `${base}/captures/${captureId}/assets/${encodeURIComponent(payload.imageId)}/chunks`
        const { captureId: _captureId, imageId: _imageId, ...rest } = payload
        body = rest
      } else throw new Error('采集操作不支持')
    }
    const response = await api.request({ path, method, body })
    assertCollectorSession(bound, session.context())
    return response.data
  }
}
