const USER_KEY = 'oryxos.console.userId'
const SESSION_PREFIX = 'oryxos.console.session.'
const PROBE_KEY = 'oryxos.console.probe'
const TITLE_TEMPLATE_PREFIX = 'oryxos.console.titleTemplate.'

export interface LocalStore {
  getUserId(): string
  getSessionId(agent: string): string | null
  setSessionId(agent: string, sessionId: string): void
  clearSessionId(agent: string): void
  /** 读取 Agent 的默认会话名模板（支持 {date} 占位符），未设置返回 null。 */
  getTitleTemplate(agent: string): string | null
  /** 设置（或传 null 清除）Agent 的默认会话名模板。 */
  setTitleTemplate(agent: string, template: string | null): void
}

function storage(): Storage | null {
  try {
    const candidate = window.localStorage
    candidate.setItem(PROBE_KEY, '1')
    candidate.removeItem(PROBE_KEY)
    return candidate
  } catch {
    return null
  }
}

function randomId(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID()
  }
  return `anon-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function createLocalStore(): LocalStore {
  let memoryUserId: string | null = null

  return {
    getUserId(): string {
      const store = storage()
      if (!store) {
        memoryUserId ??= randomId()
        return memoryUserId
      }
      const existing = store.getItem(USER_KEY)
      if (existing) {
        return existing
      }
      const created = randomId()
      store.setItem(USER_KEY, created)
      return created
    },

    getSessionId(agent: string): string | null {
      return storage()?.getItem(SESSION_PREFIX + agent) ?? null
    },

    setSessionId(agent: string, sessionId: string): void {
      storage()?.setItem(SESSION_PREFIX + agent, sessionId)
    },

    clearSessionId(agent: string): void {
      storage()?.removeItem(SESSION_PREFIX + agent)
    },

    getTitleTemplate(agent: string): string | null {
      return storage()?.getItem(TITLE_TEMPLATE_PREFIX + agent) ?? null
    },

    setTitleTemplate(agent: string, template: string | null): void {
      const store = storage()
      if (!store) {
        return
      }
      if (template === null || template.trim().length === 0) {
        store.removeItem(TITLE_TEMPLATE_PREFIX + agent)
      } else {
        store.setItem(TITLE_TEMPLATE_PREFIX + agent, template.trim())
      }
    },
  }
}
