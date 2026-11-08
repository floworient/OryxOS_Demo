const USER_KEY = 'oryxos.console.userId'
const SESSION_PREFIX = 'oryxos.console.session.'
const PROBE_KEY = 'oryxos.console.probe'

export interface LocalStore {
  getUserId(): string
  getSessionId(agent: string): string | null
  setSessionId(agent: string, sessionId: string): void
  clearSessionId(agent: string): void
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
  }
}
