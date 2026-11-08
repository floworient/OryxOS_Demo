import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ChatComposer from '@/components/ChatComposer.vue'
import ConversationPanel from '@/components/ConversationPanel.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import StatusBar from '@/components/StatusBar.vue'
import ToolChips from '@/components/ToolChips.vue'
import type { AgentConversation } from '@/state/useConsole'
import { weatherProfile } from './helpers'

function conversation(overrides: Partial<AgentConversation> = {}): AgentConversation {
  return {
    sessionId: 'session-1',
    status: 'ready',
    messages: [],
    historyTruncated: false,
    draft: '',
    inputError: null,
    waitingSeconds: 0,
    error: null,
    ...overrides,
  }
}

describe('ToolChips', () => {
  it('renders unique tool names only', () => {
    const wrapper = mount(ToolChips, {
      props: { tools: ['http_get', 'http_get', 'notify'] },
    })

    const chips = wrapper.findAll('.tool-chip')
    expect(chips).toHaveLength(2)
    expect(chips[0].text()).toContain('http_get')
    expect(chips[1].text()).toContain('notify')
  })
})

describe('MessageBubble', () => {
  it('keeps user text line breaks and renders tool chips for assistants', () => {
    const wrapper = mount(MessageBubble, {
      props: {
        message: {
          key: 'assistant-1',
          role: 'assistant',
          text: '第一行\n第二行',
          tools: ['http_get'],
        },
      },
    })

    expect(wrapper.find('.message-text').text()).toContain('第一行')
    expect(wrapper.find('.message-text').text()).toContain('第二行')
    expect(wrapper.findAll('.tool-chip')).toHaveLength(1)
  })
})

describe('ChatComposer', () => {
  const baseProps = {
    modelValue: '天气',
    disabled: false,
    sending: false,
    waitingSeconds: 0,
    inputError: null,
  }

  it('emits send on Enter but not Shift+Enter', async () => {
    const wrapper = mount(ChatComposer, { props: baseProps })

    await wrapper.find('textarea').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('send')).toHaveLength(1)

    await wrapper.find('textarea').trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(wrapper.emitted('send')).toHaveLength(1)
  })

  it('does not emit while disabled', async () => {
    const wrapper = mount(ChatComposer, { props: { ...baseProps, disabled: true } })

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('send')).toBeUndefined()
  })

  it('shows the waiting seconds while sending', () => {
    const wrapper = mount(ChatComposer, {
      props: { ...baseProps, sending: true, waitingSeconds: 12 },
    })

    expect(wrapper.text()).toContain('已等待 12s')
  })
})

describe('StatusBar', () => {
  it('shows runtime counts and providers', () => {
    const wrapper = mount(StatusBar, {
      props: { health: 'UP', profileCount: 1, toolCount: 14, providers: ['deepseek'] },
    })

    expect(wrapper.text()).toContain('健康')
    expect(wrapper.text()).toContain('Agents 1')
    expect(wrapper.text()).toContain('Tools 14')
    expect(wrapper.text()).toContain('deepseek')
  })
})

describe('ConversationPanel', () => {
  it('shows an explicit offline state', () => {
    const wrapper = mount(ConversationPanel, {
      props: {
        profile: null,
        conversation: null,
        bootStatus: 'offline',
        banner: null,
        runtimeHealth: 'UNKNOWN',
      },
    })

    expect(wrapper.text()).toContain('无法连接运行时')
  })

  it('shows workspace guidance when there are no agents', () => {
    const wrapper = mount(ConversationPanel, {
      props: {
        profile: null,
        conversation: null,
        bootStatus: 'empty',
        banner: null,
        runtimeHealth: 'UP',
      },
    })

    expect(wrapper.text()).toContain('工作区还没有 Agent')
  })

  it('makes an archived conversation read-only', () => {
    const wrapper = mount(ConversationPanel, {
      props: {
        profile: weatherProfile,
        conversation: conversation({ status: 'archived' }),
        bootStatus: 'ready',
        banner: null,
        runtimeHealth: 'UP',
      },
    })

    expect(wrapper.text()).toContain('该会话已归档')
    expect(wrapper.text()).toContain('开始新对话')
    expect(wrapper.find('.composer').exists()).toBe(false)
  })

  it('shows a tool-only assistant turn from history', () => {
    const wrapper = mount(ConversationPanel, {
      props: {
        profile: weatherProfile,
        conversation: conversation({
          messages: [
            { key: 'user-0', role: 'user', text: '天气', tools: [] },
            { key: 'assistant-1', role: 'assistant', text: '', tools: ['http_get'] },
          ],
        }),
        bootStatus: 'ready',
        banner: null,
        runtimeHealth: 'UP',
      },
    })

    expect(wrapper.findAll('.tool-chip')).toHaveLength(1)
  })
})
