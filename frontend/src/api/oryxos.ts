import { deleteJson, deleteNoContent, getJson, postJson, putJson } from './http'
import type {
  AgentSingleInvokeResponse,
  HealthView,
  MessageReplyView,
  NotifyChannelCreateInput,
  NotifyChannelDeleteView,
  NotifyChannelSummary,
  NotifyChannelUpdateInput,
  ProfileSummary,
  SessionArchiveView,
  SessionView,
  SessionSummary,
  SystemInfo,
  ToolSummary,
  SandboxAllowlist,
  MemorySummary,
  ScheduledExecution,
  ScheduledTaskSummary,
} from './types'

const BASE = '/api/v1'
const SCHEDULES = '/api/v2/schedules'

export function fetchSchedules(): Promise<ScheduledTaskSummary[]> {
  return getJson(SCHEDULES)
}

export function runSchedule(scheduleId: string): Promise<ScheduledExecution> {
  return postJson(`${SCHEDULES}/${encodeURIComponent(scheduleId)}/run`, undefined)
}

export function setScheduleEnabled(scheduleId: string, enabled: boolean): Promise<ScheduledTaskSummary> {
  return putJson(`${SCHEDULES}/${encodeURIComponent(scheduleId)}`, { enabled })
}

export function fetchProfiles(): Promise<ProfileSummary[]> {
  return getJson<ProfileSummary[]>(`${BASE}/profiles`)
}

export function fetchHealth(): Promise<HealthView> {
  return getJson<HealthView>(`${BASE}/health`)
}

export function fetchInfo(): Promise<SystemInfo> {
  return getJson<SystemInfo>(`${BASE}/info`)
}

export function fetchTools(agentName?: string): Promise<ToolSummary[]> {
  const query = agentName ? `?agentName=${encodeURIComponent(agentName)}` : ''
  return getJson<ToolSummary[]>(`${BASE}/tools${query}`)
}

export function fetchMemory(agentName: string): Promise<MemorySummary> {
  return getJson<MemorySummary>(`${BASE}/memory?agentName=${encodeURIComponent(agentName)}`)
}

/** GET /api/v1/agents/{name}/memory — 通过路径指定 Agent 的记忆 */
export function fetchAgentMemory(name: string): Promise<MemorySummary> {
  return getJson<MemorySummary>(`${BASE}/agents/${encodeURIComponent(name)}/memory`)
}

export function fetchSandboxAllowlist(): Promise<SandboxAllowlist> {
  return getJson<SandboxAllowlist>(`${BASE}/sandbox/allowlist`)
}

export function createSession(profileName: string, userId: string): Promise<SessionView> {
  return postJson<SessionView>(`${BASE}/sessions`, { profileName, userId })
}

export function fetchSessions(status?: 'active'): Promise<SessionSummary[]> {
  const query = status ? `?status=${status}` : ''
  return getJson<SessionSummary[]>(`${BASE}/sessions${query}`)
}

export function sendMessage(sessionId: string, message: string): Promise<MessageReplyView> {
  return postJson<MessageReplyView>(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages`, {
    message,
  })
}

export function fetchSession(sessionId: string): Promise<SessionView> {
  return getJson<SessionView>(`${BASE}/sessions/${encodeURIComponent(sessionId)}`)
}

export function archiveSession(sessionId: string): Promise<SessionArchiveView> {
  return deleteJson<SessionArchiveView>(`${BASE}/sessions/${encodeURIComponent(sessionId)}`)
}

// ── Execution History (US1) ────────────────────────────────

export function fetchExecutionHistory(scheduleId: string): Promise<ScheduledExecution[]> {
  return getJson<ScheduledExecution[]>(
    `${SCHEDULES}/${encodeURIComponent(scheduleId)}/executions`,
  )
}

// ── V1 Schedules (legacy, uses scheduleKey as ID) ───────────

const V1_SCHEDULES = '/api/v1/schedules'

export function fetchV1Schedules(): Promise<ScheduledTaskSummary[]> {
  return getJson<ScheduledTaskSummary[]>(V1_SCHEDULES)
}

export function fetchV1ExecutionHistory(id: string): Promise<ScheduledExecution[]> {
  return getJson<ScheduledExecution[]>(`${V1_SCHEDULES}/${encodeURIComponent(id)}/executions`)
}

export function runV1Schedule(id: string): Promise<ScheduledExecution> {
  return postJson<ScheduledExecution>(`${V1_SCHEDULES}/${encodeURIComponent(id)}/run`, undefined)
}

export function setV1ScheduleEnabled(id: string, enabled: boolean): Promise<ScheduledTaskSummary> {
  return putJson<ScheduledTaskSummary>(`${V1_SCHEDULES}/${encodeURIComponent(id)}`, { enabled })
}

// ── Agent + Key Schedule Run (US4) ─────────────────────────

export function agentRunSchedule(agentName: string, scheduleKey: string): Promise<ScheduledExecution> {
  return postJson<ScheduledExecution>(
    `/api/v1/agents/${encodeURIComponent(agentName)}/schedules/${encodeURIComponent(scheduleKey)}/run`,
    undefined,
  )
}

// ── Notification Channels (US2) ────────────────────────────

export function fetchNotifyChannels(): Promise<NotifyChannelSummary[]> {
  return getJson<NotifyChannelSummary[]>(`${BASE}/notify-channels`)
}

export function getNotifyChannel(name: string): Promise<NotifyChannelSummary> {
  return getJson<NotifyChannelSummary>(`${BASE}/notify-channels/${encodeURIComponent(name)}`)
}

export function createNotifyChannel(input: NotifyChannelCreateInput): Promise<NotifyChannelSummary> {
  return postJson<NotifyChannelSummary>(`${BASE}/notify-channels`, input)
}

export function updateNotifyChannel(
  name: string,
  input: NotifyChannelUpdateInput,
): Promise<NotifyChannelSummary> {
  return putJson<NotifyChannelSummary>(`${BASE}/notify-channels/${encodeURIComponent(name)}`, input)
}

export function deleteNotifyChannel(name: string): Promise<void> {
  return deleteNoContent(`${BASE}/notify-channels/${encodeURIComponent(name)}`)
}

// ── Agent Single Invoke (US3) ──────────────────────────────

export function invokeAgent(agentName: string, message: string): Promise<AgentSingleInvokeResponse> {
  return postJson<AgentSingleInvokeResponse>(
    `/api/v1/agents/${encodeURIComponent(agentName)}/invoke`,
    { message },
  )
}
