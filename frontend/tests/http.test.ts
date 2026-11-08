import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getJson, postJson } from '@/api/http'

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response
}

describe('http client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns data from a success envelope', async () => {
    const fetchMock = vi.fn(async () =>
      response({ code: 'SUCCESS', message: 'success', data: { status: 'UP' }, timestamp: '' }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(getJson('/api/v1/health')).resolves.toEqual({ status: 'UP' })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/health', undefined)
  })

  it('throws ApiError with the backend business code', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        response(
          { code: 'SESSION_BUSY', message: 'busy', data: null, timestamp: '' },
          409,
        ),
      ),
    )

    await expect(getJson('/api/v1/sessions/s1/messages')).rejects.toMatchObject({
      code: 'SESSION_BUSY',
      status: 409,
    })
  })

  it('normalises non-JSON responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => {
          throw new SyntaxError('not json')
        },
      })),
    )

    await expect(getJson('/api/v1/info')).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
    })
  })

  it('normalises network failures', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('fetch failed')
      }),
    )

    await expect(getJson('/api/v1/profiles')).rejects.toMatchObject({
      code: 'NETWORK_ERROR',
    })
  })

  it('posts JSON bodies', async () => {
    const fetchMock = vi.fn(async () =>
      response({ code: 'SUCCESS', message: 'success', data: { ok: true }, timestamp: '' }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await postJson('/api/v1/sessions', { profileName: 'daily-weather', userId: 'u1' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ profileName: 'daily-weather', userId: 'u1' }),
    })
  })

  it('exposes ApiError for callers', async () => {
    const error = new ApiError('STORE_ERROR', 500)
    expect(error.code).toBe('STORE_ERROR')
    expect(error.status).toBe(500)
  })
})
