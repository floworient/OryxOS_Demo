import { flushPromises, mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import App from '@/App.vue'
import { weatherProfile } from './helpers'

it('runs and disables one task using v2 and refreshes its state', async () => {
  const path = '/api/v2/schedules'
  const item = {
    scheduleId: 'a9dfeef1-98c8-3d9e-9b55-e8b3fe0c0591',
    profileName: 'daily-weather', scheduleKey: 'daily', displayName: '天气任务',
    cron: '0 0 8 * * *', zone: 'Asia/Shanghai', nextRunAt: '2026-11-10T00:00:00Z',
    lastRunAt: null, lastStatus: null, runCount: 0, enabled: true,
  }
  let finishRun!: (response: Response) => void
  const json = (data: unknown) => new Response(JSON.stringify({ code: 'SUCCESS', data }))
  const fetcher = vi.fn(async (url: string, request?: RequestInit) => {
    if (url.endsWith('/profiles')) return json([weatherProfile])
    if (url.includes('/sessions')) return json([])
    if (url === path) return json([item])
    if (url.endsWith('/run')) return new Promise<Response>(resolve => { finishRun = resolve })
    if (request?.method === 'PUT') {
      item.enabled = false
      return json(item)
    }
    return json({ status: 'UP' })
  })
  vi.stubGlobal('fetch', fetcher)
  const app = mount(App)
  try {
    await flushPromises()
    await app.get('[data-section="schedules"]').trigger('click')
    await flushPromises()
    const row = app.get('tbody tr')
    expect(row.text()).toContain('天气任务')
    expect(row.text()).toContain('daily-weather')
    expect(row.text()).toContain('0 0 8 * * *')
    const [run, toggle] = row.findAll('button')
    await run.trigger('click')
    expect(run.attributes('disabled')).toBeDefined()
    expect(toggle.attributes('disabled')).toBeDefined()
    expect(fetcher).toHaveBeenCalledWith(`${path}/${item.scheduleId}/run`, expect.objectContaining({ method: 'POST' }))
    item.runCount = 1
    finishRun(json({ success: false, errorMessage: '模型调用失败' }))
    await flushPromises()
    expect(app.get('[role="alert"]').text()).toContain('模型调用失败')
    expect(row.text()).toContain('1')
    await toggle.trigger('click')
    await flushPromises()
    expect(fetcher).toHaveBeenCalledWith(`${path}/${item.scheduleId}`,
      expect.objectContaining({ method: 'PUT', body: '{"enabled":false}' }))
    expect(row.text()).toContain('已停用')
    expect(toggle.text()).toBe('启用')
  } finally {
    app.unmount()
    vi.unstubAllGlobals()
  }
})

it('shows empty state after loading schedules', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify({
    code: 'SUCCESS', data: url.endsWith('/profiles') ? [weatherProfile] : [],
  }))))
  const app = mount(App)
  try {
    await flushPromises()
    await app.get('[data-section="schedules"]').trigger('click')
    await flushPromises()
    expect(app.get('.management-panel').text()).toContain('暂无定时任务')
  } finally {
    app.unmount()
    vi.unstubAllGlobals()
  }
})

it('paginates the task list with 10 rows per page', async () => {
  const json = (data: unknown) => new Response(JSON.stringify({ code: 'SUCCESS', data }))
  const tasks = Array.from({ length: 15 }, (_, index) => ({
    scheduleId: `task-${index}`,
    profileName: 'daily-weather',
    scheduleKey: `key-${index}`,
    displayName: `天气任务${index}`,
    cron: '0 0 8 * * *', zone: 'Asia/Shanghai',
    nextRunAt: '2026-11-10T00:00:00Z',
    lastRunAt: null, lastStatus: null, runCount: 0, enabled: true,
  }))
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url.endsWith('/profiles')) return json([weatherProfile])
    if (url.includes('/sessions')) return json([])
    if (url === '/api/v2/schedules') return json(tasks)
    return json({ status: 'UP' })
  }))
  const app = mount(App)
  try {
    await flushPromises()
    await app.get('[data-section="schedules"]').trigger('click')
    await flushPromises()
    let rows = app.findAll('table[aria-label="定时任务"] tbody tr')
    expect(rows).toHaveLength(10)
    const nav = app.get('nav[aria-label="任务列表分页"]')
    expect(nav.text()).toContain('第 1 / 2 页')
    expect(nav.text()).toContain('共 15 条')
    const [previous, next] = nav.findAll('button')
    expect(previous.attributes('disabled')).toBeDefined()
    await next.trigger('click')
    rows = app.findAll('table[aria-label="定时任务"] tbody tr')
    expect(rows).toHaveLength(5)
    expect(rows[0].text()).toContain('天气任务10')
    const nextNavButtons = app.get('nav[aria-label="任务列表分页"]').findAll('button')
    expect(nextNavButtons[0].attributes('disabled')).toBeUndefined()
    expect(nextNavButtons[1].attributes('disabled')).toBeDefined()
  } finally {
    app.unmount()
    vi.unstubAllGlobals()
  }
})

it('paginates execution history with 10 rows per page', async () => {
  const json = (data: unknown) => new Response(JSON.stringify({ code: 'SUCCESS', data }))
  const task = {
    scheduleId: 'a9dfeef1-98c8-3d9e-9b55-e8b3fe0c0591',
    profileName: 'daily-weather', scheduleKey: 'daily', displayName: '天气任务',
    cron: '0 0 8 * * *', zone: 'Asia/Shanghai', nextRunAt: '2026-11-10T00:00:00Z',
    lastRunAt: null, lastStatus: null, runCount: 0, enabled: true,
  }
  const executions = Array.from({ length: 12 }, (_, index) => ({
    scheduleId: task.scheduleId,
    sessionId: `session-${index}`,
    startedAt: `2026-11-${String(index + 1).padStart(2, '0')}T08:00:00Z`,
    success: true, errorMessage: null, durationMs: 100 + index,
  }))
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url.endsWith('/profiles')) return json([weatherProfile])
    if (url.includes('/sessions')) return json([])
    if (url === '/api/v2/schedules') return json([task])
    if (url.endsWith('/executions')) return json(executions)
    return json({ status: 'UP' })
  }))
  const app = mount(App)
  try {
    await flushPromises()
    await app.get('[data-section="schedules"]').trigger('click')
    await flushPromises()
    const historyButton = app.get('table[aria-label="定时任务"] tbody tr').findAll('button')[2]
    await historyButton.trigger('click')
    await flushPromises()
    expect(app.get('.history-panel summary').text()).toContain('共 12 条')
    let rows = app.findAll('table[aria-label="执行历史"] tbody tr')
    expect(rows).toHaveLength(10)
    const nav = app.get('nav[aria-label="天气任务 执行历史分页"]')
    expect(nav.text()).toContain('第 1 / 2 页')
    await nav.findAll('button')[1].trigger('click')
    rows = app.findAll('table[aria-label="执行历史"] tbody tr')
    expect(rows).toHaveLength(2)
  } finally {
    app.unmount()
    vi.unstubAllGlobals()
  }
})
