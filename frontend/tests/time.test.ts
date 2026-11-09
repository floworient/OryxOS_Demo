import { describe, expect, it } from 'vitest'
import { formatRelativeTime } from '@/utils/time'

describe('formatRelativeTime', () => {
  const now = new Date('2026-11-09T12:00:00Z')

  it('formats recent activity as relative time', () => {
    expect(formatRelativeTime('2026-11-09T11:59:30Z', now)).toBe('刚刚')
    expect(formatRelativeTime('2026-11-09T11:45:00Z', now)).toBe('15 分钟前')
    expect(formatRelativeTime('2026-11-09T07:00:00Z', now)).toBe('5 小时前')
    expect(formatRelativeTime('2026-11-07T12:00:00Z', now)).toBe('2 天前')
  })

  it('falls back to a date beyond 7 days and echoes invalid input', () => {
    expect(formatRelativeTime('2026-10-01T00:00:00Z', now)).toBe('2026/10/1')
    expect(formatRelativeTime('not-a-date', now)).toBe('not-a-date')
  })
})
