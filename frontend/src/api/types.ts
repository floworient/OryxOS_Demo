export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T | null
  timestamp: string
}

export interface ProfileSummary {
  name: string
  description: string
  agentName: string
  providerName: string
  model: string
  tools: string[]
  mcpServers: string[]
}

export interface SystemInfo {
  applicationName: string
  implementationVersion: string
  javaVersion: string
  profileCount: number
  toolCount: number
  providers: string[]
}

export type SessionStatus = 'ACTIVE' | 'ARCHIVED'

export interface ToolReferenceView {
  name: string
  id: string
}

export interface MessageView {
  role: 'user' | 'assistant' | 'tool'
  text: string | null
  toolCalls: ToolReferenceView[]
  toolResults: ToolReferenceView[]
}

export interface SessionView {
  id: string
  channel: string
  userId: string
  profileName: string
  status: SessionStatus
  createdAt: string
  lastActiveAt: string
  archivedAt: string | null
  historyTruncated: boolean
  messages: MessageView[]
}

export interface SessionSummary {
  id: string
  profileName: string
  channel: string
  userId: string
  status: SessionStatus
  createdAt: string
  lastActiveAt: string
  messageCount: number
}

export interface MessageReplyView {
  sessionId: string
  reply: string
}

export interface SessionArchiveView {
  sessionId: string
  status: 'ARCHIVED'
}

export interface HealthView {
  status: string
}

export interface ToolSummary {
  name: string
  description: string
  source: string
  sourceName: string
}

export interface SandboxAllowlist {
  httpDomains: string[]
  fileRootCount: number
  commandAliases: string[]
}

export interface MemoryEntry {
  id: string
  content: string
  createdAt: string
}

export interface MemorySummary {
  backend: string
  archivalTruncated: boolean
  core: MemoryEntry[]
  archival: MemoryEntry[]
}
export interface ScheduledTaskSummary {
  scheduleId: string
  profileName: string
  scheduleKey: string
  displayName: string
  cron: string
  zone: string
  enabled: boolean
  nextRunAt: string | null
  lastRunAt: string | null
  lastStatus: 'success' | 'failed' | null
  runCount: number
}

export interface ScheduledExecution {
  scheduleId: string
  sessionId: string | null
  startedAt: string
  success: boolean
  errorMessage: string | null
  durationMs: number
}

// ── Notification Channels (US2) ──────────────────────────────

export interface NotifyChannelSummary {
  name: string
  type: string
  url: string | null
  description: string | null
  config: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
}

export interface NotifyChannelCreateInput {
  name: string
  type: string
  url?: string
  description?: string
  config?: Record<string, unknown>
}

export interface NotifyChannelUpdateInput {
  type?: string
  url?: string
  description?: string
  config?: Record<string, unknown>
}

export interface NotifyChannelDeleteView {
  channelName: string
  status: 'DELETED'
}

// ── Agent Invoke (US3) ──────────────────────────────────────

export interface AgentSingleInvokeResponse {
  reply: string
}
