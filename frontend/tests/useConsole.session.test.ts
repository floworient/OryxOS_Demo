import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/http'
import { createConsole } from '@/state/useConsole'
import {
  createStore,
  makeDeps,
  makeSession,
  techProfile,
  weatherProfile,
} from './helpers'

describe('session persistence and isolation', () => {
  it('loads recent sessions and opens non-web history read-only', async () => {
    const summary = {
      id: 'cli-session', profileName: 'daily-weather', channel: 'cli', userId: 'user-1',
      status: 'ACTIVE' as const, createdAt: '2026-11-08T00:00:00Z',
      lastActiveAt: '2026-11-08T01:00:00Z', messageCount: 4, title: null as string | null,
    }
    const deps = makeDeps({
      fetchSessions: vi.fn(async () => [summary]),
      fetchSession: vi.fn(async () => makeSession({ id: summary.id, channel: 'cli' })),
    })
    const app = createConsole(deps)

    await app.initialize()
    await app.selectSession(summary)

    expect(app.state.sessions).toEqual([summary])
    expect(app.activeConversation.value?.sessionId).toBe('cli-session')
    expect(app.activeConversation.value?.status).toBe('readonly')
  })

  it('restores a saved session on startup', async () => {
    const store = createStore({ 'daily-weather': 'session-saved' })
    const deps = makeDeps({ store })
    const app = createConsole(deps)

    await app.initialize()

    expect(deps.fetchSession).toHaveBeenCalledWith('session-saved')
    const conversation = app.state.agents['daily-weather']
    expect(conversation.sessionId).toBe('session-saved')
    expect(conversation.status).toBe('ready')
    expect(conversation.messages).toHaveLength(3)
  })

  it('clears a missing session and returns to idle', async () => {
    const store = createStore({ 'daily-weather': 'session-gone' })
    const deps = makeDeps({
      store,
      fetchSession: async () => {
        throw new ApiError('SESSION_NOT_FOUND', 404)
      },
    })
    const app = createConsole(deps)

    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    expect(conversation.sessionId).toBeNull()
    expect(conversation.status).toBe('idle')
    expect(store.getSessionId('daily-weather')).toBeNull()
  })

  it('keeps an archived session read-only', async () => {
    const store = createStore({ 'daily-weather': 'session-archived' })
    const deps = makeDeps({
      store,
      fetchSession: async () =>
        makeSession({ id: 'session-archived', status: 'ARCHIVED', archivedAt: '2026-11-06T01:00:00Z' }),
    })
    const app = createConsole(deps)

    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    expect(conversation.status).toBe('archived')
    expect(conversation.messages).toHaveLength(3)
  })

  it('does not leak messages between agents', async () => {
    const deps = makeDeps({ fetchProfiles: async () => [weatherProfile, techProfile] })
    const app = createConsole(deps)
    await app.initialize()

    await app.selectAgent('daily-tech')
    const techConversation = app.state.agents['daily-tech']
    techConversation.draft = '生成今日科技日报'
    await app.send()

    expect(techConversation.messages.length).toBeGreaterThan(0)
    expect(app.state.agents['daily-weather'].messages).toHaveLength(0)

    await app.selectAgent('daily-weather')
    expect(app.activeConversation.value?.messages).toHaveLength(0)

    await app.selectAgent('daily-tech')
    expect(app.activeConversation.value?.messages).toHaveLength(techConversation.messages.length)
  })

  it('archives the active session before starting a new conversation', async () => {
    let created = 0
    const deps = makeDeps({
      createSession: vi.fn(async (profileName: string, userId: string) =>
        makeSession({ id: `session-${++created}`, profileName, userId }),
      ),
    })
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '第一轮'
    await app.send()
    const firstSessionId = conversation.sessionId
    expect(firstSessionId).toBe('session-1')

    await app.newConversation()

    expect(deps.archiveSession).toHaveBeenCalledWith('session-1')
    expect(conversation.sessionId).toBeNull()
    expect(conversation.messages).toHaveLength(0)
    expect(deps.store.getSessionId('daily-weather')).toBeNull()

    conversation.draft = '第二轮'
    await app.send()

    expect(conversation.sessionId).toBe('session-2')
    expect(conversation.sessionId).not.toBe(firstSessionId)
  })

  it('archives an existing session from the dedicated action', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.sessionId = 'session-existing'
    deps.store.setSessionId('daily-weather', 'session-existing')

    await app.archiveConversation()

    expect(deps.archiveSession).toHaveBeenCalledWith('session-existing')
    expect(conversation.sessionId).toBeNull()
    expect(conversation.status).toBe('idle')
    expect(deps.store.getSessionId('daily-weather')).toBeNull()
  })

  it('does not call archive when there is no session', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    await app.newConversation()

    expect(deps.archiveSession).not.toHaveBeenCalled()
    expect(app.state.agents['daily-weather'].status).toBe('idle')
  })

  it('archives any session by id from the sidebar list and refreshes it', async () => {
    const summary = {
      id: 'other-session', profileName: 'daily-tech', channel: 'web', userId: 'user-1',
      status: 'ACTIVE' as const, createdAt: '2026-11-08T00:00:00Z',
      lastActiveAt: '2026-11-08T01:00:00Z', messageCount: 2, title: null as string | null,
    }
    const deps = makeDeps({
      fetchSessions: vi.fn(async () => [summary]),
    })
    const app = createConsole(deps)
    await app.initialize()

    await app.archiveSessionById('other-session')

    expect(deps.archiveSession).toHaveBeenCalledWith('other-session')
    expect(deps.fetchSessions).toHaveBeenCalledTimes(2)
    // 当前 Agent 的会话不受影响
    expect(app.state.agents['daily-weather'].sessionId).toBeNull()
  })

  it('archives the current session by id and resets the conversation', async () => {
    const store = createStore({ 'daily-weather': 'session-current' })
    const deps = makeDeps({ store })
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    expect(conversation.sessionId).toBe('session-current')

    await app.archiveSessionById('session-current')

    expect(deps.archiveSession).toHaveBeenCalledWith('session-current')
    expect(conversation.sessionId).toBeNull()
    expect(conversation.status).toBe('idle')
    expect(store.getSessionId('daily-weather')).toBeNull()
  })

  it('treats a missing session as archived and still cleans up local state', async () => {
    const store = createStore({ 'daily-weather': 'session-gone' })
    const deps = makeDeps({
      store,
      archiveSession: async () => {
        throw new ApiError('SESSION_NOT_FOUND', 404)
      },
    })
    const app = createConsole(deps)
    await app.initialize()

    await app.archiveSessionById('session-gone')

    expect(app.state.banner).toBeNull()
    expect(app.state.agents['daily-weather'].sessionId).toBeNull()
    expect(store.getSessionId('daily-weather')).toBeNull()
  })

  it('reports a banner error when archiving fails and keeps the session', async () => {
    const store = createStore({ 'daily-weather': 'session-live' })
    const deps = makeDeps({
      store,
      archiveSession: async () => {
        throw new ApiError('INTERNAL_ERROR', 500)
      },
    })
    const app = createConsole(deps)
    await app.initialize()

    await app.archiveSessionById('session-live')

    expect(app.state.banner?.code).toBe('INTERNAL_ERROR')
    expect(app.state.agents['daily-weather'].sessionId).toBe('session-live')
    expect(store.getSessionId('daily-weather')).toBe('session-live')
  })

  it('restores an archived session by id and refreshes the list', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    await app.restoreSessionById('session-archived')

    expect(deps.restoreSession).toHaveBeenCalledWith('session-archived')
    expect(deps.fetchSessions).toHaveBeenCalledTimes(2)
    expect(app.state.banner).toBeNull()
  })

  it('reports a banner error when restoring fails', async () => {
    const deps = makeDeps({
      restoreSession: vi.fn(async () => {
        throw new ApiError('SESSION_NOT_ARCHIVED', 409)
      }),
    })
    const app = createConsole(deps)
    await app.initialize()

    await app.restoreSessionById('session-active')

    expect(deps.restoreSession).toHaveBeenCalledWith('session-active')
    expect(app.state.banner?.code).toBe('SESSION_NOT_ARCHIVED')
  })

  it('renames a session by id and refreshes the list', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    const ok = await app.renameSessionById('session-live', '  新会话名  ')

    expect(ok).toBe(true)
    expect(deps.renameSession).toHaveBeenCalledWith('session-live', '新会话名')
    expect(deps.fetchSessions).toHaveBeenCalledTimes(2)
    expect(app.state.toasts.some(toast => toast.kind === 'success' && toast.message.includes('会话名'))).toBe(true)
  })

  it('rejects blank titles and reports failures with a banner', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    const blank = await app.renameSessionById('session-live', '   ')
    expect(blank).toBe(false)
    expect(deps.renameSession).not.toHaveBeenCalled()

    const failing = makeDeps({
      renameSession: vi.fn(async () => {
        throw new ApiError('SESSION_NOT_FOUND', 404)
      }),
    })
    const failingApp = createConsole(failing)
    await failingApp.initialize()

    const failed = await failingApp.renameSessionById('session-live', '新会话名')
    expect(failed).toBe(false)
    expect(failingApp.state.banner?.code).toBe('SESSION_NOT_FOUND')
  })

  it('applies the agent title template to a newly created session', async () => {
    const store = createStore()
    store.setTitleTemplate('daily-weather', '{date} 天气简报')
    const deps = makeDeps({ store })
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '今天杭州天气怎么样？'
    await app.send('daily-weather')

    // 新会话发送首条消息后，模板渲染为当天日期开头的会话名
    expect(deps.renameSession).toHaveBeenCalledTimes(1)
    const [, title] = (deps.renameSession as ReturnType<typeof vi.fn>).mock.calls[0] as [string, string]
    expect(title).toMatch(/^\d{4}-\d{2}-\d{2} 天气简报$/)

    // 再发一条：不再重复应用模板
    conversation.draft = '再问一条'
    await app.send('daily-weather')
    expect(deps.renameSession).toHaveBeenCalledTimes(1)
  })

  it('keeps the default first-question title when no template is set', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '今天杭州天气怎么样？'
    await app.send('daily-weather')

    expect(deps.renameSession).not.toHaveBeenCalled()
  })
})
