import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ToastStack from '@/components/ToastStack.vue'
import { createConsole } from '@/state/useConsole'
import { makeDeps } from './helpers'

describe('ToastStack', () => {
  it('renders toasts by kind and emits close with the toast id', async () => {
    const wrapper = mount(ToastStack, {
      props: {
        toasts: [
          { id: 1, kind: 'success', message: '刷新成功' },
          { id: 2, kind: 'error', message: '归档失败' },
        ],
      },
    })

    expect(wrapper.findAll('.toast')).toHaveLength(2)
    expect(wrapper.find('.toast.success').text()).toContain('刷新成功')
    expect(wrapper.find('.toast.error').text()).toContain('归档失败')

    await wrapper.findAll('.toast-close')[0].trigger('click')
    expect(wrapper.emitted('close')?.[0]).toEqual([1])
  })
})

describe('toast notifications', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('stays silent on boot but announces a manual refresh success', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)

    await app.initialize()
    expect(app.state.toasts).toHaveLength(0)

    await app.initialize(true)
    expect(
      app.state.toasts.some((toast) => toast.kind === 'success' && toast.message === '刷新成功'),
    ).toBe(true)
  })

  it('announces a refresh failure with the mapped error message', async () => {
    const deps = makeDeps({
      fetchProfiles: async () => {
        throw new Error('offline')
      },
    })
    const app = createConsole(deps)

    await app.initialize(true)

    const toast = app.state.toasts.find((item) => item.kind === 'error')
    expect(toast?.message).toContain('服务器内部错误')
  })

  it('announces session refresh success and failure via refreshSessions', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)

    await app.refreshSessions(true)
    expect(
      app.state.toasts.some((toast) => toast.kind === 'success' && toast.message === '会话已刷新'),
    ).toBe(true)

    const failing = createConsole(
      makeDeps({
        fetchSessions: async () => {
          throw new Error('network down')
        },
      }),
    )
    await failing.refreshSessions(true)
    expect(
      failing.state.toasts.some(
        (toast) => toast.kind === 'error' && toast.message.includes('刷新失败'),
      ),
    ).toBe(true)
  })

  it('auto-dismisses toasts after 3 seconds', async () => {
    const app = createConsole(makeDeps())
    await app.initialize(true)
    expect(app.state.toasts).toHaveLength(1)

    vi.advanceTimersByTime(3000)
    expect(app.state.toasts).toHaveLength(0)
  })

  it('announces archive success', async () => {
    const deps = makeDeps({ archiveSession: vi.fn(async () => ({ sessionId: 'session-1', status: 'ARCHIVED' as const })) })
    const app = createConsole(deps)

    await app.initialize()
    await app.archiveSessionById('session-1')

    expect(
      app.state.toasts.some((toast) => toast.kind === 'success' && toast.message === '会话已归档'),
    ).toBe(true)
  })
})
