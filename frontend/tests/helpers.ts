import { vi } from 'vitest'
import type { LocalStore } from '@/state/localStore'
import type { ConsoleDeps } from '@/state/useConsole'
import type { ProfileSummary, SessionView, SystemInfo } from '@/api/types'

export const weatherProfile: ProfileSummary = {
  name: 'daily-weather',
  description: '每日天气与穿搭建议',
  agentName: '每日天气助手',
  providerName: 'deepseek',
  model: 'deepseek-chat',
  tools: ['http_get'],
  mcpServers: [],
}

export const techProfile: ProfileSummary = {
  name: 'daily-tech',
  description: '每日科技日报',
  agentName: '科技日报助手',
  providerName: 'deepseek',
  model: 'deepseek-chat',
  tools: ['http_get', 'read_file'],
  mcpServers: [],
}

export const systemInfo: SystemInfo = {
  applicationName: 'oryxos',
  implementationVersion: '0.1.0',
  javaVersion: '21',
  profileCount: 1,
  toolCount: 14,
  providers: ['deepseek'],
}

export function createStore(initial: Record<string, string> = {}): LocalStore & {
  sessions: Map<string, string>
  titleTemplates: Map<string, string>
} {
  const sessions = new Map<string, string>(Object.entries(initial))
  const titleTemplates = new Map<string, string>()
  return {
    sessions,
    titleTemplates,
    getUserId: () => 'test-user',
    getSessionId: (agent) => sessions.get(agent) ?? null,
    setSessionId: (agent, sessionId) => {
      sessions.set(agent, sessionId)
    },
    clearSessionId: (agent) => {
      sessions.delete(agent)
    },
    getTitleTemplate: (agent) => titleTemplates.get(agent) ?? null,
    setTitleTemplate: (agent, template) => {
      if (template === null || template.trim().length === 0) {
        titleTemplates.delete(agent)
      } else {
        titleTemplates.set(agent, template.trim())
      }
    },
  }
}

export function makeSession(overrides: Partial<SessionView> = {}): SessionView {
  return {
    id: 'session-1',
    channel: 'web',
    userId: 'test-user',
    profileName: 'daily-weather',
    status: 'ACTIVE',
    createdAt: '2026-11-06T00:00:00Z',
    lastActiveAt: '2026-11-06T00:00:00Z',
    archivedAt: null,
    historyTruncated: false,
    messages: [
      { role: 'user', text: '今天杭州天气怎么样？', toolCalls: [], toolResults: [] },
      {
        role: 'assistant',
        text: '',
        toolCalls: [
          { name: 'http_get', id: 'call-1' },
          { name: 'http_get', id: 'call-2' },
        ],
        toolResults: [],
      },
      {
        role: 'tool',
        text: null,
        toolCalls: [],
        toolResults: [{ name: 'http_get', id: 'call-1' }],
      },
      { role: 'assistant', text: '今天杭州 18~27℃，多云。', toolCalls: [], toolResults: [] },
    ],
    ...overrides,
  }
}

export function makeDeps(overrides: Partial<ConsoleDeps> = {}): ConsoleDeps {
  return {
    fetchProfiles: vi.fn(async () => [weatherProfile]),
    fetchHealth: vi.fn(async () => ({ status: 'UP' })),
    fetchInfo: vi.fn(async () => systemInfo),
    createSession: vi.fn(async (profileName: string, userId: string) =>
      makeSession({ id: 'session-created', profileName, userId }),
    ),
    sendMessage: vi.fn(async (sessionId: string) => ({ sessionId, reply: '今天多云。' })),
    fetchSession: vi.fn(async (sessionId: string) => makeSession({ id: sessionId })),
    archiveSession: vi.fn(async (sessionId: string) => ({
      sessionId,
      status: 'ARCHIVED' as const,
    })),
    restoreSession: vi.fn(async (sessionId: string) =>
      makeSession({ id: sessionId, status: 'ACTIVE' as const }),
    ),
    renameSession: vi.fn(async (sessionId: string, title: string) => ({
      sessionId,
      title,
    })),
    fetchSessions: vi.fn(async () => []),
    store: createStore(),
    ...overrides,
  }
}
