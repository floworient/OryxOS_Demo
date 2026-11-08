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
      lastActiveAt: '2026-11-08T01:00:00Z', messageCount: 4,
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
})
