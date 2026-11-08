import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/http'
import { createConsole } from '@/state/useConsole'
import { makeDeps, techProfile, weatherProfile } from './helpers'

describe('console bootstrap', () => {
  it('loads real profiles and runtime information', async () => {
    const deps = makeDeps()
    const app = createConsole(deps)

    await app.initialize()

    expect(app.state.bootStatus).toBe('ready')
    expect(app.state.profiles).toEqual([weatherProfile])
    expect(app.state.activeAgent).toBe('daily-weather')
    expect(app.state.runtimeHealth).toBe('UP')
    expect(app.state.systemInfo?.toolCount).toBe(14)
  })

  it('enters empty state when the workspace has no agents', async () => {
    const deps = makeDeps({ fetchProfiles: async () => [] })
    const app = createConsole(deps)

    await app.initialize()

    expect(app.state.bootStatus).toBe('empty')
    expect(app.state.activeAgent).toBeNull()
  })

  it('keeps the agent list when health or info fails', async () => {
    const deps = makeDeps({
      fetchHealth: async () => {
        throw new ApiError('NETWORK_ERROR', 0)
      },
      fetchInfo: async () => {
        throw new ApiError('NETWORK_ERROR', 0)
      },
    })
    const app = createConsole(deps)

    await app.initialize()

    expect(app.state.bootStatus).toBe('ready')
    expect(app.state.profiles).toEqual([weatherProfile])
    expect(app.state.runtimeHealth).toBe('DOWN')
    expect(app.state.systemInfo).toBeNull()
  })

  it('enters offline state when profiles cannot be loaded', async () => {
    const deps = makeDeps({
      fetchProfiles: async () => {
        throw new ApiError('NETWORK_ERROR', 0)
      },
    })
    const app = createConsole(deps)

    await app.initialize()

    expect(app.state.bootStatus).toBe('offline')
    expect(app.state.banner?.code).toBe('NETWORK_ERROR')
  })

  it('switches the active agent', async () => {
    const deps = makeDeps({ fetchProfiles: async () => [weatherProfile, techProfile] })
    const app = createConsole(deps)
    await app.initialize()

    await app.selectAgent('daily-tech')

    expect(app.state.activeAgent).toBe('daily-tech')
    expect(app.state.agents['daily-tech']).toBeDefined()
  })
})
