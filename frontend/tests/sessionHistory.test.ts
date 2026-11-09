import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SessionHistory from '@/components/SessionHistory.vue'
import { techProfile, weatherProfile } from './helpers'
import type { SessionSummary } from '@/api/types'

const session: SessionSummary = {
  id: 'session-1',
  profileName: 'daily-weather',
  channel: 'cli',
  userId: 'user-1',
  status: 'ACTIVE',
  createdAt: '2026-11-08T00:00:00Z',
  lastActiveAt: '2026-11-08T01:00:00Z',
  messageCount: 4,
  title: null,
}

describe('SessionHistory', () => {
  it('renders and selects a real summary without message bodies', async () => {
    const wrapper = mount(SessionHistory, {
      props: {
        sessions: [session],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })

    expect(wrapper.text()).toContain('daily-weather')
    expect(wrapper.text()).toContain('cli · 4 条消息')
    await wrapper.find('.agent-item').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([session])
  })

  it('groups sessions by agent and orders groups by the profile list', () => {
    const anotherWeatherSession: SessionSummary = {
      ...session,
      id: 'session-2',
      channel: 'web',
      lastActiveAt: '2026-11-08T02:00:00Z',
      messageCount: 6,
    }
    const techSession: SessionSummary = {
      ...session,
      id: 'session-3',
      profileName: 'daily-tech',
      channel: 'web',
      messageCount: 2,
    }
    const wrapper = mount(SessionHistory, {
      props: {
        // 传入顺序刻意打乱：daily-tech 在前、daily-weather 在后
        sessions: [techSession, anotherWeatherSession, session],
        profiles: [weatherProfile, techProfile],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })

    const groups = wrapper.findAll('.session-group')
    expect(groups).toHaveLength(2)
    // 组顺序跟随 profiles 顺序：weather 组在前
    expect(groups[0].find('.session-group-title').text()).toBe('每日天气与穿搭建议')
    expect(groups[1].find('.session-group-title').text()).toBe('每日科技日报')
    // weather 组包含两个会话，tech 组一个
    expect(groups[0].find('.session-group-count').text()).toBe('2')
    expect(groups[1].find('.session-group-count').text()).toBe('1')
  })

  it('expands only the active agent group and collapses the others', async () => {
    const techSession: SessionSummary = {
      ...session,
      id: 'session-3',
      profileName: 'daily-tech',
      channel: 'web',
      messageCount: 2,
    }
    const wrapper = mount(SessionHistory, {
      props: {
        sessions: [session, techSession],
        profiles: [weatherProfile, techProfile],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })

    const [weatherGroup, techGroup] = wrapper.findAll('.session-group')
    // 初始：当前 Agent 的组展开，其他组收起
    expect(weatherGroup.find('.session-group-head').attributes('aria-expanded')).toBe('true')
    expect(techGroup.find('.session-group-head').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.agent-list').isVisible()).toBe(true)

    // 切换 Agent：自动展开新 Agent 的组并收起旧组
    await wrapper.setProps({ activeAgent: 'daily-tech' })
    expect(techGroup.find('.session-group-head').attributes('aria-expanded')).toBe('true')
    expect(weatherGroup.find('.session-group-head').attributes('aria-expanded')).toBe('false')

    // 点击组头可以手动展开另一组（同时收起当前组）
    await weatherGroup.find('.session-group-head').trigger('click')
    expect(weatherGroup.find('.session-group-head').attributes('aria-expanded')).toBe('true')
    expect(techGroup.find('.session-group-head').attributes('aria-expanded')).toBe('false')

    // 再次点击组头可收起
    await weatherGroup.find('.session-group-head').trigger('click')
    expect(weatherGroup.find('.session-group-head').attributes('aria-expanded')).toBe('false')
  })

  it('emits archive only after confirming the dialog for a web session', async () => {
    const webSession: SessionSummary = {
      ...session,
      id: 'session-web',
      channel: 'web',
    }
    const wrapper = mount(SessionHistory, {
      props: {
        sessions: [webSession],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })

    const archiveButton = wrapper.find('.session-archive-button')
    expect(archiveButton.attributes('aria-label')).toContain('daily-weather')

    // 点击归档按钮先弹确认框，尚未真正归档，也不触发选中
    await archiveButton.trigger('click')
    expect(wrapper.find('.dialog-card').exists()).toBe(true)
    expect(wrapper.emitted('archive')).toBeUndefined()
    expect(wrapper.emitted('select')).toBeUndefined()

    // 取消：不归档
    await wrapper.find('.dialog-card .ghost-button').trigger('click')
    expect(wrapper.emitted('archive')).toBeUndefined()

    // 重新点击并确认：才触发归档
    await archiveButton.trigger('click')
    await wrapper.find('.dialog-confirm').trigger('click')
    expect(wrapper.emitted('archive')?.[0]).toEqual([webSession])
  })

  it('hides the archive button for non-web channel sessions', () => {
    // 基准 session 的 channel 是 cli：后端 409 拒绝归档，按钮不应出现
    const wrapper = mount(SessionHistory, {
      props: {
        sessions: [session],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })
    expect(wrapper.find('.session-archive-button').exists()).toBe(false)
  })

  it('hides archived sessions by default and reveals them with the toggle', async () => {
    const archived: SessionSummary = {
      ...session,
      id: 'session-archived',
      channel: 'web',
      status: 'ARCHIVED',
    }
    const activeWeb: SessionSummary = {
      ...session,
      id: 'session-active',
      channel: 'web',
    }
    const wrapper = mount(SessionHistory, {
      props: {
        sessions: [archived, activeWeb],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })

    // 默认隐藏已归档会话，只显示 1 条
    expect(wrapper.findAll('.session-entry')).toHaveLength(1)
    expect(wrapper.find('.archived-badge').exists()).toBe(false)

    // 打开开关：归档会话显示并带徽标，且无删除按钮（已归档不可再归档）
    await wrapper.find('.archived-toggle input').setValue(true)
    expect(wrapper.findAll('.session-entry')).toHaveLength(2)
    expect(wrapper.find('.session-entry.archived').exists()).toBe(true)
    expect(wrapper.find('.session-entry.archived .archived-badge').text()).toBe('已归档')
    expect(wrapper.find('.session-entry.archived .session-archive-button').exists()).toBe(false)
  })

  it('emits restore when clicking the restore button of an archived session', async () => {
    const archived: SessionSummary = {
      ...session,
      id: 'session-archived',
      channel: 'web',
      status: 'ARCHIVED',
    }
    const wrapper = mount(SessionHistory, {
      props: {
        sessions: [archived],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })

    // 归档会话默认隐藏，先打开开关
    await wrapper.find('.archived-toggle input').setValue(true)
    const restoreButton = wrapper.find('.session-entry.archived .session-restore-button')
    expect(restoreButton.attributes('aria-label')).toContain('daily-weather')

    await restoreButton.trigger('click')
    expect(wrapper.emitted('restore')?.[0]).toEqual([archived])
    // 恢复是可逆操作，直接执行不弹确认框，也不触发选中
    expect(wrapper.find('.dialog-card').exists()).toBe(false)
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('highlights the active session inside its group', () => {
    const wrapper = mount(SessionHistory, {
      props: {
        sessions: [session],
        status: 'ready',
        error: null,
        activeId: session.id,
        activeAgent: 'daily-weather',
      },
    })
    expect(wrapper.find('.agent-item').classes()).toContain('active')
  })

  it('shows the session title and falls back to channel and count', () => {
    const titled: SessionSummary = {
      ...session,
      id: 'session-titled',
      title: '今天杭州天气怎么样？',
    }
    const titledWrapper = mount(SessionHistory, {
      props: {
        sessions: [titled],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })
    expect(titledWrapper.find('.agent-title').text()).toBe('今天杭州天气怎么样？')
    expect(titledWrapper.find('.agent-title').attributes('title')).toBe('今天杭州天气怎么样？')

    const untitled = mount(SessionHistory, {
      props: {
        sessions: [session],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })
    expect(untitled.find('.agent-title').text()).toBe('cli · 4 条消息')
  })

  it('emits rename after inline editing and ignores blank or unchanged titles', async () => {
    const titled: SessionSummary = {
      ...session,
      id: 'session-titled',
      channel: 'web',
      title: '旧会话名',
    }
    const wrapper = mount(SessionHistory, {
      props: {
        sessions: [titled],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
      },
    })

    const renameButton = wrapper.find('.session-rename-button')
    expect(renameButton.attributes('aria-label')).toContain('daily-weather')

    // 点击 ✏️ 弹出重命名弹窗，输入新名字确认提交
    await renameButton.trigger('click')
    const input = wrapper.find('.dialog-card .session-rename-input')
    expect((input.element as HTMLInputElement).value).toBe('旧会话名')
    await input.setValue('2026-11-09 每日简报')
    await wrapper.find('.dialog-card .dialog-confirm').trigger('click')
    expect(wrapper.emitted('rename')?.[0]).toEqual([titled, '2026-11-09 每日简报'])
    expect(wrapper.emitted('select')).toBeUndefined()
    expect(wrapper.find('.dialog-card').exists()).toBe(false)

    // 空名字：不触发重命名，弹窗关闭
    await renameButton.trigger('click')
    await wrapper.find('.dialog-card .session-rename-input').setValue('   ')
    await wrapper.find('.dialog-card .dialog-confirm').trigger('click')
    expect(wrapper.emitted('rename')).toHaveLength(1)

    // 未变化的名字：不触发重命名
    await renameButton.trigger('click')
    await wrapper.find('.dialog-card .session-rename-input').setValue('旧会话名')
    await wrapper.find('.dialog-card .dialog-confirm').trigger('click')
    expect(wrapper.emitted('rename')).toHaveLength(1)

    // 取消按钮：关闭弹窗不提交
    await renameButton.trigger('click')
    await wrapper.find('.dialog-card .session-rename-input').setValue('又一个名字')
    await wrapper.find('.dialog-card .ghost-button').trigger('click')
    expect(wrapper.emitted('rename')).toHaveLength(1)
    expect(wrapper.find('.dialog-card').exists()).toBe(false)
  })

  it('emits title template from the agent config dialog', async () => {
    const wrapper = mount(SessionHistory, {
      props: {
        sessions: [session],
        profiles: [weatherProfile],
        status: 'ready',
        error: null,
        activeId: null,
        activeAgent: 'daily-weather',
        titleTemplates: { 'daily-weather': '{date} 天气' },
      },
    })

    const configButton = wrapper.find('.session-group-config')
    expect(configButton.attributes('aria-label')).toContain('每日天气')

    // 打开弹窗：回显已有模板，输入新模板保存
    await configButton.trigger('click')
    const input = wrapper.find('.dialog-card .session-rename-input')
    expect((input.element as HTMLInputElement).value).toBe('{date} 天气')
    await input.setValue('{date} 天气简报')
    await wrapper.find('.dialog-card .dialog-confirm').trigger('click')
    expect(wrapper.emitted('titleTemplate')?.[0]).toEqual(['daily-weather', '{date} 天气简报'])

    // 清空保存：传 null 表示清除模板
    await configButton.trigger('click')
    await wrapper.find('.dialog-card .session-rename-input').setValue('')
    await wrapper.find('.dialog-card .dialog-confirm').trigger('click')
    expect(wrapper.emitted('titleTemplate')?.[1]).toEqual(['daily-weather', null])
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
