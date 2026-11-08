import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/http'
import type { MessageReplyView } from '@/api/types'
import { createConsole, waitingText } from '@/state/useConsole'
import { makeDeps, makeSession } from './helpers'

describe('error and waiting states', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('keeps the draft and shows a mapped error when sending fails', async () => {
    const deps = makeDeps({
      sendMessage: async () => {
        throw new ApiError('PROVIDER_UNAVAILABLE', 503)
      },
    })
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '今天杭州天气怎么样？'

    await app.send()

    expect(conversation.status).toBe('error')
    expect(conversation.error?.code).toBe('PROVIDER_UNAVAILABLE')
    expect(conversation.error?.message).toBe('模型服务不可用，请检查 Provider 配置')
    expect(conversation.draft).toBe('今天杭州天气怎么样？')
    expect(conversation.messages).toHaveLength(0)
  })

  it('clears a session that the backend reports as missing during send', async () => {
    const deps = makeDeps({
      sendMessage: async () => {
        throw new ApiError('SESSION_NOT_FOUND', 404)
      },
    })
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.sessionId = 'session-missing'
    deps.store.setSessionId('daily-weather', 'session-missing')
    conversation.draft = '继续'

    await app.send()

    expect(conversation.sessionId).toBeNull()
    expect(deps.store.getSessionId('daily-weather')).toBeNull()
    expect(conversation.error?.code).toBe('SESSION_NOT_FOUND')
  })

  it('retries a failed message with the preserved draft', async () => {
    let attempt = 0
    const deps = makeDeps({
      sendMessage: async (sessionId: string) => {
        attempt += 1
        if (attempt === 1) {
          throw new ApiError('PROVIDER_UNAVAILABLE', 503)
        }
        return { sessionId, reply: '今天多云。' }
      },
    })
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '今天杭州天气怎么样？'
    await app.send()
    expect(conversation.status).toBe('error')

    await app.retry()

    expect(conversation.status).toBe('ready')
    expect(conversation.error).toBeNull()
    expect(conversation.messages.length).toBeGreaterThan(0)
  })

  it('counts waiting seconds and warns near the timeout', async () => {
    vi.useFakeTimers()
    let resolveSend: (value: MessageReplyView) => void = () => undefined
    const pending = new Promise<MessageReplyView>((resolve) => {
      resolveSend = resolve
    })
    const deps = makeDeps({ sendMessage: () => pending })
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '天气'
    const running = app.send()

    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
    expect(conversation.status).toBe('sending')

    vi.advanceTimersByTime(1000)
    expect(conversation.waitingSeconds).toBe(1)

    vi.advanceTimersByTime(54_000)
    expect(conversation.waitingSeconds).toBe(55)
    expect(waitingText(55)).toContain('即将超时')

    resolveSend({ sessionId: 'session-created', reply: '晴' })
    await running
    expect(conversation.status).toBe('ready')
  })

  it('ignores a duplicate send while a request is in flight', async () => {
    let resolveSend: (value: MessageReplyView) => void = () => undefined
    const pending = new Promise<MessageReplyView>((resolve) => {
      resolveSend = resolve
    })
    const deps = makeDeps({ sendMessage: vi.fn(() => pending) })
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '天气'
    const first = app.send()

    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
    await app.send()

    expect(deps.sendMessage).toHaveBeenCalledTimes(1)

    resolveSend({ sessionId: 'session-created', reply: '晴' })
    await first
  })

  it('maps a missing session during history restore to idle', async () => {
    const deps = makeDeps({
      fetchSession: async () => {
        throw new ApiError('SESSION_NOT_FOUND', 404)
      },
    })
    const app = createConsole(deps)
    await app.initialize()
    const conversation = app.state.agents['daily-weather']
    conversation.sessionId = 'session-old'

    await app.loadHistory('daily-weather')

    expect(conversation.status).toBe('idle')
    expect(conversation.sessionId).toBeNull()
  })

  it('keeps the current session id when archive fails for an unknown reason', async () => {
    const deps = makeDeps({
      archiveSession: async () => {
        throw new ApiError('STORAGE_ERROR', 500)
      },
    })
    const app = createConsole(deps)
    await app.initialize()
    const conversation = app.state.agents['daily-weather']
    conversation.sessionId = 'session-1'

    await app.archiveConversation()

    expect(conversation.sessionId).toBe('session-1')
    expect(conversation.status).toBe('error')
    expect(conversation.error?.code).toBe('STORAGE_ERROR')
  })

  it('rebuilds messages from the session response', async () => {
    const deps = makeDeps({ fetchSession: async () => makeSession({ historyTruncated: true }) })
    const app = createConsole(deps)
    await app.initialize()
    const conversation = app.state.agents['daily-weather']
    conversation.sessionId = 'session-1'

    await app.loadHistory('daily-weather')

    expect(conversation.historyTruncated).toBe(true)
    expect(conversation.messages[0].text).toBe('今天杭州天气怎么样？')
  })
})
