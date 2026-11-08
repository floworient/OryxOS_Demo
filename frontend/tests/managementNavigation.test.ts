import { flushPromises, mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import App from '@/App.vue'
import { weatherProfile } from './helpers'

it('opens seven sections and preserves the conversation draft', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    const data = url.endsWith('/profiles') ? [weatherProfile]
      : url.endsWith('/health') ? { status: 'UP' } : { providers: ['deepseek'] }
    return new Response(JSON.stringify({ code: 'SUCCESS', data }))
  }))
  const app = mount(App)
  try {
    await flushPromises()
    const nav = app.get('nav[aria-label="控制台导航"]')
    expect(nav.findAll('button').map(button => button.text())).toEqual(
      ['Agent', 'Provider', 'Tool', 'Sandbox 白名单', '长期记忆', '运行状态', '会话'],
    )
    const draft = app.get('textarea')
    await draft.setValue('未发送的草稿')
    await nav.get('button[data-section="providers"]').trigger('click')
    await nav.get('button[data-section="sessions"]').trigger('click')
    expect(app.get('textarea').element.value).toBe('未发送的草稿')
  } finally {
    app.unmount()
    vi.unstubAllGlobals()
  }
})
