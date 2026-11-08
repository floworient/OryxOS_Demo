import { describe, expect, it } from 'vitest'
import { createConsole } from '@/state/useConsole'
import { makeDeps } from './helpers'

describe('send flow', () => {
  it('creates a session, sends the message and rebuilds history with tool names', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '今天杭州天气怎么样？'

    await app.send()

    expect(deps.createSession).toHaveBeenCalledWith('daily-weather', 'test-user')
    expect(deps.sendMessage).toHaveBeenCalledWith('session-created', '今天杭州天气怎么样？')
    expect(deps.fetchSession).toHaveBeenCalledWith('session-created')
    expect(conversation.sessionId).toBe('session-created')
    expect(conversation.status).toBe('ready')
    expect(conversation.messages.map((message) => message.role)).toEqual([
      'user',
      'assistant',
      'assistant',
    ])
    expect(conversation.messages[1].tools).toEqual(['http_get'])
    expect(conversation.messages[2].text).toBe('今天杭州 18~27℃，多云。')
    expect(conversation.draft).toBe('')
  })

  it('rejects blank messages without calling the API', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '   '

    await app.send()

    expect(conversation.inputError).toBe('消息不能为空')
    expect(deps.createSession).not.toHaveBeenCalled()
    expect(deps.sendMessage).not.toHaveBeenCalled()
  })

  it('reuses an existing session for later messages', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.sessionId = 'session-existing'
    conversation.draft = '那明天呢？'

    await app.send()

    expect(deps.createSession).not.toHaveBeenCalled()
    expect(deps.sendMessage).toHaveBeenCalledWith('session-existing', '那明天呢？')
  })

  it('drops tool role messages but keeps assistant tool-only turns', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)
    await app.initialize()

    const conversation = app.state.agents['daily-weather']
    conversation.draft = '天气'
    await app.send()

    expect(conversation.messages.some((message) => message.role === ('tool' as never))).toBe(false)
    expect(conversation.messages[1].text).toBe('')
    expect(conversation.messages[1].tools).toEqual(['http_get'])
  })
})
