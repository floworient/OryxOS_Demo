import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createLocalStore } from '@/state/localStore'

describe('localStore', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('creates and reuses one anonymous user id', () => {
    const store = createLocalStore()
    const first = store.getUserId()

    expect(first.length).toBeGreaterThan(0)
    expect(store.getUserId()).toBe(first)
    expect(window.localStorage.getItem('oryxos.console.userId')).toBe(first)
  })

  it('keeps session ids isolated per agent', () => {
    const store = createLocalStore()

    store.setSessionId('daily-weather', 'session-weather')
    store.setSessionId('daily-tech', 'session-tech')

    expect(store.getSessionId('daily-weather')).toBe('session-weather')
    expect(store.getSessionId('daily-tech')).toBe('session-tech')

    store.clearSessionId('daily-weather')
    expect(store.getSessionId('daily-weather')).toBeNull()
    expect(store.getSessionId('daily-tech')).toBe('session-tech')
  })

  it('degrades safely when storage is unavailable', () => {
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage denied')
    })

    try {
      const store = createLocalStore()
      const first = store.getUserId()

      expect(store.getUserId()).toBe(first)
      expect(() => store.setSessionId('daily-weather', 'session-1')).not.toThrow()
      expect(store.getSessionId('daily-weather')).toBeNull()
      expect(() => store.clearSessionId('daily-weather')).not.toThrow()
    } finally {
      spy.mockRestore()
    }
  })
})
