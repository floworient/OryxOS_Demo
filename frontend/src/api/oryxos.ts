import { deleteJson, getJson, postJson } from './http'
import type {
  HealthView,
  MessageReplyView,
  ProfileSummary,
  SessionArchiveView,
  SessionView,
  SessionSummary,
  SystemInfo,
  ToolSummary,
  SandboxAllowlist,
  MemorySummary,
} from './types'

const BASE = '/api/v1'

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
