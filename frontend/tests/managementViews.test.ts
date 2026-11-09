import { flushPromises, mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import App from '@/App.vue'
import { weatherProfile } from './helpers'

it('lets the memory view retry when the agent list could not load', async () => {
  let offline = true
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url.endsWith('/profiles') && offline) throw new Error('offline')
    const data = url.endsWith('/profiles') ? [weatherProfile]
      : url.includes('/sessions') ? []
      : url.includes('/memory?') ? { core: [], archival: [] } : { providers: [] }
    return new Response(JSON.stringify({ code: 'SUCCESS', data }))
  }))
  const app = mount(App)
  try {
    await flushPromises()
    await app.get('nav button[data-section="memory"]').trigger('click')
    expect(app.get('.management-panel [role="alert"]').text()).toContain('Agent 列表加载失败')
    offline = false
    await app.get('.management-panel button').trigger('click')
    await flushPromises()
    expect(app.get('.management-panel').text()).toContain('核心记忆')
  } finally {
    app.unmount()
    vi.unstubAllGlobals()
  }
})

it('renders runtime queries and retries a failed tool lookup', async () => {
  let failTools = true
  const fetcher = vi.fn(async (url: string) => {
    if (url.includes('/tools') && failTools) throw new Error('offline')
    const data = url.endsWith('/profiles') ? [weatherProfile]
      : url.includes('/sessions') ? []
      : url.endsWith('/health') ? { status: 'UP' }
      : url.includes('/tools') ? [{ name: 'http_get', source: 'BUILTIN' }]
      : url.includes('/memory?') ? { core: [{ id: '1', content: '核心偏好' }], archival: [] }
      : url.endsWith('/allowlist') ? { httpDomains: ['example.com'], fileRootCount: 0, commandAliases: [] }
      : { providers: ['deepseek'], implementationVersion: 'test-version' }
    return new Response(JSON.stringify({ code: 'SUCCESS', data }))
  })
  vi.stubGlobal('fetch', fetcher)
  const app = mount(App)
  try {
    const open = async (key: string) => {
      await app.get(`nav button[data-section="${key}"]`).trigger('click')
      await flushPromises()
      return app.get('.management-panel')
    }
    await flushPromises()
    const agents = await open('agents')
    await agents.get('button[data-agent]').trigger('click')
    expect(app.get('nav button[aria-current="page"]').text()).toBe('会话')
    expect((await open('providers')).text()).toContain('deepseek')
    const tools = await open('tools')
    expect(tools.get('[role="alert"]').text()).toContain('加载失败')
    failTools = false
    await tools.get('button').trigger('click')
    await flushPromises()
    await tools.get('select').setValue(weatherProfile.name)
    await flushPromises()
    expect(fetcher).toHaveBeenCalledWith('/api/v1/tools?agentName=daily-weather', undefined)
    expect(tools.text()).toContain('http_get')
    expect((await open('sandbox')).text()).toContain('example.com')
    expect((await open('memory')).text()).toContain('核心偏好')
    expect((await open('runtime')).text()).toContain('test-version')
  } finally {
    app.unmount()
    vi.unstubAllGlobals()
  }
})

it('keeps the current tool results when an earlier query finishes late', async () => {
  let finishOld!: (response: Response) => void
  const oldQuery = new Promise<Response>(resolve => { finishOld = resolve })
  const json = (data: unknown) => new Response(JSON.stringify({ code: 'SUCCESS', data }))
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url.endsWith('/tools')) return oldQuery
    if (url.includes('/tools?')) return json([{ name: 'current-tool' }])
    if (url.endsWith('/profiles')) return json([weatherProfile])
    if (url.includes('/sessions')) return json([])
    if (url.endsWith('/health')) return json({ status: 'UP' })
    return json({ providers: [] })
  }))
  const app = mount(App)
  try {
    await flushPromises()
    await app.get('nav button[data-section="tools"]').trigger('click')
    await app.get('.management-panel select').setValue(weatherProfile.name)
    await flushPromises()
    finishOld(json([{ name: 'old-tool' }]))
    await flushPromises()
    expect(app.get('.management-panel').text()).toContain('current-tool')
    expect(app.get('.management-panel').text()).not.toContain('old-tool')
  } finally {
    app.unmount()
    vi.unstubAllGlobals()
  }
})
