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
