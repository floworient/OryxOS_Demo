import { computed, reactive } from 'vue'
import * as api from '@/api/oryxos'
import type {
  HealthView,
  MessageReplyView,
  ProfileSummary,
  SessionArchiveView,
  SessionRenameView,
  SessionView,
  SessionSummary,
  SystemInfo,
} from '@/api/types'
import { mapError, type UiError } from '@/utils/errors'
import { createLocalStore, type LocalStore } from './localStore'

export type BootStatus = 'loading' | 'ready' | 'empty' | 'offline'
export type ConversationStatus =
  | 'idle'
  | 'loadingHistory'
  | 'sending'
  | 'ready'
  | 'archived'
  | 'readonly'
  | 'error'
export type RuntimeHealth = 'UP' | 'DOWN' | 'UNKNOWN'

export interface DisplayMessage {
  key: string
  role: 'user' | 'assistant'
  text: string
  tools: string[]
}

export interface AgentConversation {
  sessionId: string | null
  status: ConversationStatus
  messages: DisplayMessage[]
  historyTruncated: boolean
  draft: string
  inputError: string | null
  waitingSeconds: number
  error: UiError | null
}

export interface Toast {
  id: number
  kind: 'success' | 'error'
  message: string
}

export interface ConsoleState {
  bootStatus: BootStatus
  profiles: ProfileSummary[]
  systemInfo: SystemInfo | null
  runtimeHealth: RuntimeHealth
  activeAgent: string | null
  agents: Record<string, AgentConversation>
  banner: UiError | null
  sessions: SessionSummary[]
  sessionsStatus: 'loading' | 'ready' | 'empty' | 'error'
  sessionsError: UiError | null
  toasts: Toast[]
}

export interface ConsoleDeps {
  fetchProfiles: () => Promise<ProfileSummary[]>
  fetchHealth: () => Promise<HealthView>
  fetchInfo: () => Promise<SystemInfo>
  createSession: (profileName: string, userId: string) => Promise<SessionView>
  sendMessage: (sessionId: string, message: string) => Promise<MessageReplyView>
  fetchSession: (sessionId: string) => Promise<SessionView>
  archiveSession: (sessionId: string) => Promise<SessionArchiveView>
  restoreSession: (sessionId: string) => Promise<SessionView>
  renameSession: (sessionId: string, title: string) => Promise<SessionRenameView>
  fetchSessions: (status?: 'active') => Promise<SessionSummary[]>
  store: LocalStore
}

const defaultDeps: ConsoleDeps = {
  fetchProfiles: api.fetchProfiles,
  fetchHealth: api.fetchHealth,
  fetchInfo: api.fetchInfo,
  createSession: api.createSession,
  sendMessage: api.sendMessage,
  fetchSession: api.fetchSession,
  archiveSession: api.archiveSession,
  fetchSessions: api.fetchSessions,
  restoreSession: api.restoreSession,
  renameSession: api.renameSession,
  store: createLocalStore(),
}

function emptyConversation(sessionId: string | null): AgentConversation {
  return {
    sessionId,
    status: 'idle',
    messages: [],
    historyTruncated: false,
    draft: '',
    inputError: null,
    waitingSeconds: 0,
    error: null,
  }
}

function uniqueToolNames(calls: { name: string }[]): string[] {
  return Array.from(new Set(calls.map((call) => call.name).filter((name) => name.length > 0)))
}

function toDisplayMessages(session: SessionView): DisplayMessage[] {
  const messages: DisplayMessage[] = []
  session.messages.forEach((message, index) => {
    if (message.role !== 'user' && message.role !== 'assistant') {
      return
    }
    const text = message.text ?? ''
    const tools = message.role === 'assistant' ? uniqueToolNames(message.toolCalls) : []
    if (message.role === 'assistant' && text.trim().length === 0 && tools.length === 0) {
      return
    }
    messages.push({ key: `${message.role}-${index}`, role: message.role, text, tools })
  })
  return messages
}

export function waitingText(seconds: number): string {
  return seconds >= 55 ? `已等待 ${seconds}s，即将超时` : `已等待 ${seconds}s`
}

export function createConsole(overrides: Partial<ConsoleDeps> = {}) {
  const deps: ConsoleDeps = { ...defaultDeps, ...overrides }
  const state = reactive<ConsoleState>({
    bootStatus: 'loading',
    profiles: [],
    systemInfo: null,
    runtimeHealth: 'UNKNOWN',
    activeAgent: null,
    agents: {},
    banner: null,
    sessions: [],
    sessionsStatus: 'loading',
    sessionsError: null,
    toasts: [],
  })
  const timers = new Map<string, ReturnType<typeof setInterval>>()

  // ── Toast 通知（瞬时反馈，约 3 秒自动消失；持久错误仍走 banner） ──
  let toastSeq = 0
  const TOAST_TTL_MS = 3000

  function pushToast(kind: Toast['kind'], message: string): void {
    const id = ++toastSeq
    state.toasts.push({ id, kind, message })
    setTimeout(() => dismissToast(id), TOAST_TTL_MS)
  }

  function dismissToast(id: number): void {
    state.toasts = state.toasts.filter((toast) => toast.id !== id)
  }

  function ensureConversation(agent: string): AgentConversation {
    let conversation = state.agents[agent]
    if (!conversation) {
      conversation = reactive(emptyConversation(deps.store.getSessionId(agent)))
      state.agents[agent] = conversation
    }
    return conversation
  }

  function applySession(conversation: AgentConversation, session: SessionView): void {
    conversation.sessionId = session.id
    conversation.messages = toDisplayMessages(session)
    conversation.historyTruncated = session.historyTruncated
    conversation.error = null
    conversation.inputError = null
    conversation.status = session.status === 'ARCHIVED'
      ? 'archived'
      : session.channel === 'web' ? 'ready' : 'readonly'
  }

  function startWaiting(agent: string, conversation: AgentConversation): void {
    stopWaiting(agent)
    conversation.waitingSeconds = 0
    timers.set(
      agent,
      setInterval(() => {
        conversation.waitingSeconds += 1
      }, 1000),
    )
  }

  function stopWaiting(agent: string): void {
    const timer = timers.get(agent)
    if (timer) {
      clearInterval(timer)
      timers.delete(agent)
    }
  }

  async function loadHistory(agent: string): Promise<void> {
    const conversation = ensureConversation(agent)
    const sessionId = conversation.sessionId
    if (!sessionId) {
      return
    }
    conversation.status = 'loadingHistory'
    try {
      const session = await deps.fetchSession(sessionId)
      applySession(conversation, session)
    } catch (error) {
      const mapped = mapError(error)
      if (mapped.code === 'SESSION_NOT_FOUND') {
        conversation.sessionId = null
        deps.store.clearSessionId(agent)
        conversation.messages = []
        conversation.historyTruncated = false
        conversation.status = 'idle'
        conversation.error = null
      } else {
        conversation.status = 'error'
        conversation.error = mapped
      }
    }
  }

  async function initialize(announce = false): Promise<void> {
    state.bootStatus = 'loading'
    state.banner = null

    const [profilesResult, healthResult, infoResult, sessionsResult] = await Promise.allSettled([
      deps.fetchProfiles(),
      deps.fetchHealth(),
      deps.fetchInfo(),
      deps.fetchSessions(),
    ])

    if (sessionsResult.status === 'fulfilled') {
      state.sessions = sessionsResult.value
      state.sessionsStatus = state.sessions.length === 0 ? 'empty' : 'ready'
      state.sessionsError = null
    } else {
      state.sessionsStatus = 'error'
      state.sessionsError = mapError(sessionsResult.reason)
    }

    if (profilesResult.status === 'rejected') {
      state.runtimeHealth = 'UNKNOWN'
      state.banner = mapError(profilesResult.reason)
      state.bootStatus = state.profiles.length > 0 ? 'ready' : 'offline'
      if (announce) pushToast('error', state.banner.message)
      return
    }

    state.profiles = profilesResult.value
    state.runtimeHealth =
      healthResult.status === 'fulfilled' && healthResult.value.status === 'UP' ? 'UP' : 'DOWN'
    state.systemInfo = infoResult.status === 'fulfilled' ? infoResult.value : null

    if (state.profiles.length === 0) {
      state.activeAgent = null
      state.bootStatus = 'empty'
      return
    }

    const current = state.profiles.find((profile) => profile.name === state.activeAgent)
    state.activeAgent = current?.name ?? state.profiles[0].name
    state.bootStatus = 'ready'

    const conversation = ensureConversation(state.activeAgent)
    const shouldRestore =
      conversation.sessionId !== null &&
      conversation.messages.length === 0 &&
      conversation.status !== 'sending' &&
      conversation.status !== 'loadingHistory'
    if (shouldRestore) {
      await loadHistory(state.activeAgent)
    }
    if (announce) pushToast('success', '刷新成功')
  }

  async function selectAgent(agent: string): Promise<void> {
    if (!state.profiles.some((profile) => profile.name === agent)) {
      return
    }
    state.activeAgent = agent
    const conversation = ensureConversation(agent)
    const shouldRestore =
      conversation.sessionId !== null &&
      conversation.messages.length === 0 &&
      conversation.status !== 'sending' &&
      conversation.status !== 'loadingHistory'
    if (shouldRestore) {
      await loadHistory(agent)
    }
  }

  async function selectSession(summary: SessionSummary): Promise<void> {
    const profile = state.profiles.find((item) => item.name === summary.profileName)
    if (!profile) return
    state.activeAgent = profile.name
    const conversation = ensureConversation(profile.name)
    conversation.sessionId = summary.id
    await loadHistory(profile.name)
  }

  async function refreshSessions(announce = false): Promise<void> {
    state.sessionsStatus = 'loading'
    state.sessionsError = null
    try {
      state.sessions = await deps.fetchSessions()
      state.sessionsStatus = state.sessions.length === 0 ? 'empty' : 'ready'
      if (announce) pushToast('success', '会话已刷新')
    } catch (error) {
      const mapped = mapError(error)
      state.sessionsStatus = 'error'
      state.sessionsError = mapped
      if (announce) pushToast('error', `刷新失败：${mapped.message}`)
    }
  }

  async function send(agent = state.activeAgent ?? undefined): Promise<void> {
    if (!agent) {
      return
    }
    const conversation = ensureConversation(agent)
    if (conversation.status === 'sending' || conversation.status === 'loadingHistory') {
      return
    }
    const text = conversation.draft.trim()
    if (text.length === 0) {
      conversation.inputError = '消息不能为空'
      return
    }

    conversation.inputError = null
    conversation.error = null
    const draft = conversation.draft
    conversation.draft = ''
    conversation.status = 'sending'
    conversation.messages = [
      ...conversation.messages,
      { key: 'pending-user', role: 'user', text, tools: [] },
    ]
    startWaiting(agent, conversation)

    try {
      let sessionId = conversation.sessionId
      let createdNew = false
      if (!sessionId) {
        const session = await deps.createSession(agent, deps.store.getUserId())
        sessionId = session.id
        createdNew = true
        conversation.sessionId = sessionId
        deps.store.setSessionId(agent, sessionId)
      }
      await deps.sendMessage(sessionId, text)
      if (createdNew) {
        // 新会话首条消息后应用该 Agent 的默认会话名模板（如 {date}）
        const template = deps.store.getTitleTemplate(agent)
        if (template) {
          const title = renderTitleTemplate(template)
          if (title.length > 0) {
            await deps.renameSession(sessionId, title).catch(() => undefined)
          }
        }
      }
      const session = await deps.fetchSession(sessionId)
      applySession(conversation, session)
      await refreshSessions()
    } catch (error) {
      conversation.messages = conversation.messages.filter((message) => message.key !== 'pending-user')
      const mapped = mapError(error)
      if (mapped.code === 'SESSION_NOT_FOUND') {
        conversation.sessionId = null
        deps.store.clearSessionId(agent)
      }
      conversation.status = 'error'
      conversation.error = mapped
      conversation.draft = draft
    } finally {
      stopWaiting(agent)
    }
  }

  function resetConversation(agent: string, conversation: AgentConversation): void {
    stopWaiting(agent)
    conversation.sessionId = null
    conversation.status = 'idle'
    conversation.messages = []
    conversation.historyTruncated = false
    conversation.draft = ''
    conversation.inputError = null
    conversation.waitingSeconds = 0
    conversation.error = null
  }

  async function endConversation(agent = state.activeAgent ?? undefined): Promise<void> {
    if (!agent) {
      return
    }
    const conversation = ensureConversation(agent)
    if (conversation.status === 'sending') {
      return
    }
    const sessionId = conversation.sessionId
    if (sessionId) {
      try {
        await deps.archiveSession(sessionId)
      } catch (error) {
        const mapped = mapError(error)
        if (mapped.code !== 'SESSION_NOT_FOUND') {
          conversation.status = 'error'
          conversation.error = mapped
          return
        }
      }
      deps.store.clearSessionId(agent)
    }
    resetConversation(agent, conversation)
  }

  /** 归档侧边栏会话列表中的任意会话（不一定是当前会话）。 */
  async function archiveSessionById(sessionId: string): Promise<void> {
    try {
      await deps.archiveSession(sessionId)
    } catch (error) {
      const mapped = mapError(error)
      if (mapped.code === 'SESSION_CHANNEL_NOT_WRITABLE') {
        // 渠道约束（CLI/调度会话不可从 Web 归档）：明确提示；按钮层已隐藏，此为竞态兜底
        state.banner = { ...mapped, message: '该会话来自其他渠道，不支持在控制台删除' }
        pushToast('error', '该会话来自其他渠道，不支持在控制台删除')
        await refreshSessions()
        return
      }
      if (mapped.code !== 'SESSION_NOT_FOUND') {
        state.banner = mapped
        pushToast('error', `归档失败：${mapped.message}`)
        await refreshSessions()
        return
      }
      // 会话已不存在，视为已归档，继续清理本地状态
    }
    for (const [agent, conversation] of Object.entries(state.agents)) {
      if (conversation.sessionId === sessionId && conversation.status !== 'sending') {
        deps.store.clearSessionId(agent)
        resetConversation(agent, conversation)
      }
    }
    pushToast('success', '会话已归档')
    await refreshSessions()
  }

  /** 撤销归档：把已归档会话恢复为活动状态。 */
  async function restoreSessionById(sessionId: string): Promise<void> {
    try {
      await deps.restoreSession(sessionId)
    } catch (error) {
      const mapped = mapError(error)
      state.banner = mapped
      pushToast('error', `恢复失败：${mapped.message}`)
      await refreshSessions()
      return
    }
    pushToast('success', '会话已恢复')
    await refreshSessions()
  }

  /** 重命名会话（纯元数据操作，不限渠道）。 */
  async function renameSessionById(sessionId: string, title: string): Promise<boolean> {
    const trimmed = title.trim()
    if (trimmed.length === 0) {
      pushToast('error', '会话名不能为空')
      return false
    }
    try {
      await deps.renameSession(sessionId, trimmed)
    } catch (error) {
      const mapped = mapError(error)
      state.banner = mapped
      pushToast('error', `重命名失败：${mapped.message}`)
      await refreshSessions()
      return false
    }
    pushToast('success', '会话名已更新')
    await refreshSessions()
    return true
  }

  /** 读取 / 设置 Agent 的默认会话名模板（支持 {date} 占位符）。 */
  function getTitleTemplate(agent: string): string | null {
    return deps.store.getTitleTemplate(agent)
  }

  function setTitleTemplate(agent: string, template: string | null): void {
    deps.store.setTitleTemplate(agent, template)
  }

  /** 把模板渲染为会话名：{date} 替换为当天日期（YYYY-MM-DD）。 */
  function renderTitleTemplate(template: string): string {
    const now = new Date()
    const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    return template.replaceAll('{date}', date).trim()
  }

  async function retry(agent = state.activeAgent ?? undefined): Promise<void> {
    if (!agent) {
      return
    }
    const conversation = ensureConversation(agent)
    if (conversation.draft.trim().length === 0) {
      return
    }
    await send(agent)
  }

  const activeConversation = computed<AgentConversation | null>(() =>
    state.activeAgent ? (state.agents[state.activeAgent] ?? null) : null,
  )
  const isSending = computed(() => activeConversation.value?.status === 'sending')

  return {
    state,
    activeConversation,
    isSending,
    initialize,
    selectAgent,
    selectSession,
    refreshSessions,
    loadHistory,
    send,
    newConversation: endConversation,
    archiveConversation: endConversation,
    archiveSessionById,
    restoreSessionById,
    renameSessionById,
    getTitleTemplate,
    setTitleTemplate,
    pushToast,
    dismissToast,
    retry,
  }
}

export type Console = ReturnType<typeof createConsole>
