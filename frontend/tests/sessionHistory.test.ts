import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SessionHistory from '@/components/SessionHistory.vue'

const session = {
  id: 'session-1',
  profileName: 'daily-weather',
  channel: 'cli',
  userId: 'user-1',
  status: 'ACTIVE' as const,
  createdAt: '2026-11-08T00:00:00Z',
  lastActiveAt: '2026-11-08T01:00:00Z',
  messageCount: 4,
}

describe('SessionHistory', () => {
  it('renders and selects a real summary without message bodies', async () => {
    const wrapper = mount(SessionHistory, {
      props: { sessions: [session], status: 'ready', error: null, activeId: null },
    })

    expect(wrapper.text()).toContain('daily-weather')
    expect(wrapper.text()).toContain('cli · 4 条消息')
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([session])
  })

  it('covers loading empty and retryable error states', async () => {
    const loading = mount(SessionHistory, {
      props: { sessions: [], status: 'loading', error: null, activeId: null },
    })
    expect(loading.text()).toContain('正在加载会话')

    const empty = mount(SessionHistory, {
      props: { sessions: [], status: 'empty', error: null, activeId: null },
    })
    expect(empty.text()).toContain('暂无会话')

    const failed = mount(SessionHistory, {
      props: {
        sessions: [], status: 'error', activeId: null,
        error: { code: 'NETWORK_ERROR', message: '网络错误', retryable: true },
      },
    })
    await failed.find('button').trigger('click')
    expect(failed.emitted('retry')).toHaveLength(1)
  })
})
