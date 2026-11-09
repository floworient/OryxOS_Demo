export const consoleSections = [
  { key: 'agents', label: 'Agent' },
  { key: 'providers', label: 'Provider' },
  { key: 'tools', label: 'Tool' },
  { key: 'sandbox', label: 'Sandbox 白名单' },
  { key: 'memory', label: '长期记忆' },
  { key: 'schedules', label: '定时任务' },
  { key: 'runtime', label: '运行状态' },
  { key: 'sessions', label: '会话' },
] as const

export type ConsoleSection = typeof consoleSections[number]['key']
