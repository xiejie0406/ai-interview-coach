export interface SessionContext {
  readonly authenticated: boolean
  readonly sessionEpoch: number
  readonly workspaceEpoch: number
  readonly workspaceId: string | null
}

/** Token 只存在于 main 内存；epoch 负责让旧异步结果失效。 */
export class SessionController {
  #token: string | null = null
  #sessionEpoch = 0
  #workspaceEpoch = 0
  #workspaceId: string | null = null
  readonly #controllers = new Set<AbortController>()

  token(): string | null {
    return this.#token
  }

  context(): SessionContext {
    return Object.freeze({
      authenticated: this.#token !== null,
      sessionEpoch: this.#sessionEpoch,
      workspaceEpoch: this.#workspaceEpoch,
      workspaceId: this.#workspaceId
    })
  }

  authenticate(token: string): SessionContext {
    if (!token || token.length > 8192) {
      throw new TypeError('登录 Token 非法')
    }
    this.#abortAll('session-replaced')
    this.#token = token
    this.#workspaceId = null
    this.#sessionEpoch += 1
    this.#workspaceEpoch += 1
    return this.context()
  }

  clear(): SessionContext {
    this.#abortAll('session-cleared')
    this.#token = null
    this.#workspaceId = null
    this.#sessionEpoch += 1
    this.#workspaceEpoch += 1
    return this.context()
  }

  selectWorkspace(workspaceId: string): SessionContext {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(workspaceId)) {
      throw new TypeError('workspaceId 必须是 canonical UUID v4')
    }
    this.#abortAll('workspace-switched')
    this.#workspaceId = workspaceId
    this.#workspaceEpoch += 1
    return this.context()
  }

  trackedAbortController(): AbortController {
    const controller = new AbortController()
    this.#controllers.add(controller)
    controller.signal.addEventListener('abort', () => this.#controllers.delete(controller), { once: true })
    return controller
  }

  release(controller: AbortController): void {
    this.#controllers.delete(controller)
  }

  isCurrent(context: SessionContext): boolean {
    return context.sessionEpoch === this.#sessionEpoch
      && context.workspaceEpoch === this.#workspaceEpoch
      && context.workspaceId === this.#workspaceId
  }

  #abortAll(reason: string): void {
    for (const controller of this.#controllers) {
      controller.abort(reason)
    }
    this.#controllers.clear()
  }
}
