import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/http'
import { mapError } from '@/utils/errors'

describe('mapError', () => {
  it('maps known business codes to fixed Chinese messages', () => {
    expect(mapError(new ApiError('SESSION_BUSY', 409))).toEqual({
      code: 'SESSION_BUSY',
      message: '上一轮还在处理中，请稍候',
      retryable: true,
    })
    expect(mapError(new ApiError('PROVIDER_UNAVAILABLE', 503))).toEqual({
      code: 'PROVIDER_UNAVAILABLE',
      message: '模型服务不可用，请检查 Provider 配置',
      retryable: true,
    })
    expect(mapError(new ApiError('AGENT_TIMEOUT', 504))).toEqual({
      code: 'AGENT_TIMEOUT',
      message: '本轮超过 60 秒已取消，请重试',
      retryable: true,
    })
  })

  it('maps transport failures', () => {
    expect(mapError(new ApiError('NETWORK_ERROR', 0)).message).toBe(
      '无法连接运行时，请确认后端已启动',
    )
    expect(mapError(new ApiError('INVALID_RESPONSE', 200)).message).toBe(
      '运行时返回了无法识别的响应',
    )
  })

  it('keeps unknown codes but uses a generic message', () => {
    const mapped = mapError(new ApiError('SOMETHING_NEW', 500))
    expect(mapped.code).toBe('SOMETHING_NEW')
    expect(mapped.message).toContain('未识别')
    expect(mapped.retryable).toBe(true)
  })

  it('maps non-API errors to a generic internal error', () => {
    const mapped = mapError(new Error('boom'))
    expect(mapped.code).toBe('INTERNAL_ERROR')
    expect(mapped.message).toBe('服务器内部错误，请稍后重试')
  })
})
