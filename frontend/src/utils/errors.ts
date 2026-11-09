import { ApiError } from '@/api/http'

export interface UiError {
  code: string
  message: string
  retryable: boolean
}

const MESSAGES: Record<string, Omit<UiError, 'code'>> = {
  VALIDATION_ERROR: { message: '消息不合法，请修改后重试', retryable: true },
  AGENT_NOT_FOUND: { message: 'Agent 已不存在，请刷新 Agent 列表', retryable: true },
  SESSION_NOT_FOUND: { message: '会话已失效，已为你开始新对话', retryable: true },
  SESSION_BUSY: { message: '上一轮还在处理中，请稍候', retryable: true },
  SESSION_CHANNEL_NOT_WRITABLE: { message: '当前会话不允许写入，请开始新对话', retryable: true },
  SESSION_NOT_ARCHIVED: { message: '会话不是归档状态，无需恢复', retryable: true },
  PROVIDER_UNAVAILABLE: { message: '模型服务不可用，请检查 Provider 配置', retryable: true },
  MEMORY_UNAVAILABLE: { message: '记忆服务不可用，请稍后重试', retryable: true },
  AGENT_TIMEOUT: { message: '本轮超过 60 秒已取消，请重试', retryable: true },
  STORAGE_ERROR: { message: '会话存储不可用，请稍后重试', retryable: true },
  INTERNAL_ERROR: { message: '服务器内部错误，请稍后重试', retryable: true },
  NETWORK_ERROR: { message: '无法连接运行时，请确认后端已启动', retryable: true },
  INVALID_RESPONSE: { message: '运行时返回了无法识别的响应', retryable: true },
}

const UNKNOWN_MESSAGE = { message: '运行时返回了未识别的错误，请稍后重试', retryable: true }

export function mapError(error: unknown): UiError {
  const code = error instanceof ApiError ? error.code : 'INTERNAL_ERROR'
  const known = MESSAGES[code]
  return { code, ...(known ?? UNKNOWN_MESSAGE) }
}
