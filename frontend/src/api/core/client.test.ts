import { isAxiosError } from 'axios'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '../../mocks/server'
import { logger } from '../../utils/logger'
import { ApiError, unwrap } from './apiError'
import { apiClient } from './client'

const REQUEST_BODY_MARKER = 'DISTINCTIVE-REQUEST-BODY-4711'
const RESPONSE_BODY_MARKER = 'DISTINCTIVE-RESPONSE-BODY-4711'
const QUERY_MARKER = 'DISTINCTIVE-QUERY-4711'

describe('apiClient request/response logging', () => {
  let debugSpy: ReturnType<typeof vi.spyOn>
  let warnSpy: ReturnType<typeof vi.spyOn>
  let errorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    debugSpy = vi.spyOn(logger, 'debug').mockImplementation(() => {})
    warnSpy = vi.spyOn(logger, 'warn').mockImplementation(() => {})
    errorSpy = vi.spyOn(logger, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function everythingLogged(): string {
    return JSON.stringify([debugSpy.mock.calls, warnSpy.mock.calls, errorSpy.mock.calls])
  }

  it('sends a distinct X-Request-Id header on every request', async () => {
    const seen: (string | null)[] = []
    server.use(
      http.get('/api/things', ({ request }) => {
        seen.push(request.headers.get('X-Request-Id'))
        return HttpResponse.json([])
      }),
    )

    await apiClient.get('/things')
    await apiClient.get('/things')

    expect(seen).toHaveLength(2)
    expect(seen[0]).toMatch(/^[A-Za-z0-9-]{8,64}$/)
    expect(seen[1]).toMatch(/^[A-Za-z0-9-]{8,64}$/)
    expect(seen[0]).not.toBe(seen[1])
  })

  it('still sends a request id where crypto.randomUUID is unavailable (insecure context)', async () => {
    vi.stubGlobal('crypto', { getRandomValues: crypto.getRandomValues.bind(crypto) })
    try {
      let seen: string | null = null
      server.use(
        http.get('/api/things', ({ request }) => {
          seen = request.headers.get('X-Request-Id')
          return HttpResponse.json([])
        }),
      )

      await apiClient.get('/things')

      expect(seen).toMatch(/^[A-Za-z0-9-]{8,64}$/)
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('logs a successful request at debug with method, relative url, status, duration and id', async () => {
    let sentId: string | null = null
    server.use(
      http.get('/api/things', ({ request }) => {
        sentId = request.headers.get('X-Request-Id')
        return HttpResponse.json([])
      }),
    )

    await apiClient.get('/things')

    expect(debugSpy).toHaveBeenCalledOnce()
    const [message, context] = debugSpy.mock.calls[0]
    expect(message).toMatch(/^GET \/things 200 \(\d+ ms\)$/)
    expect(context).toEqual({ requestId: sentId })
    expect(warnSpy).not.toHaveBeenCalled()
    expect(errorSpy).not.toHaveBeenCalled()
  })

  it('logs a 409 at warn, and still rejects as an ApiError carrying the call-site conflictMessage', async () => {
    let sentId: string | null = null
    server.use(
      http.delete('/api/things/1', ({ request }) => {
        sentId = request.headers.get('X-Request-Id')
        return HttpResponse.json({ message: RESPONSE_BODY_MARKER }, { status: 409 })
      }),
    )

    const error: unknown = await unwrap(apiClient.delete('/things/1'), 'Reassign them first').catch(
      (err: unknown) => err,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe('Reassign them first')
    expect(warnSpy).toHaveBeenCalledOnce()
    const [message, context] = warnSpy.mock.calls[0]
    expect(message).toMatch(/^DELETE \/things\/1 409 \(\d+ ms\)$/)
    expect(context).toEqual({ requestId: sentId })
    expect(errorSpy).not.toHaveBeenCalled()
  })

  it('re-rejects the original AxiosError untouched', async () => {
    server.use(
      http.get('/api/things', () => HttpResponse.json({ message: 'nope' }, { status: 404 })),
    )

    const error: unknown = await apiClient.get('/things').catch((err: unknown) => err)

    expect(isAxiosError(error)).toBe(true)
    expect(isAxiosError(error) && error.response?.status).toBe(404)
    expect(isAxiosError(error) && error.response?.data).toEqual({ message: 'nope' })
    expect(warnSpy).toHaveBeenCalledOnce()
  })

  it('logs a 500 at error', async () => {
    server.use(http.get('/api/things', () => new HttpResponse(null, { status: 500 })))

    await expect(unwrap(apiClient.get('/things'))).rejects.toMatchObject({ status: 500 })

    expect(errorSpy).toHaveBeenCalledOnce()
    expect(errorSpy.mock.calls[0][0]).toMatch(/^GET \/things 500 \(\d+ ms\)$/)
    expect(warnSpy).not.toHaveBeenCalled()
  })

  it('logs a network failure (no response) at error and still rejects as ApiError status 0', async () => {
    server.use(http.get('/api/things', () => HttpResponse.error()))

    const error: unknown = await unwrap(apiClient.get('/things')).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(0)
    expect(errorSpy).toHaveBeenCalledOnce()
    const [message, context] = errorSpy.mock.calls[0]
    expect(message).toMatch(/^GET \/things failed with no response \(\d+ ms\)$/)
    expect(context).toEqual({ requestId: expect.any(String) })
    expect(warnSpy).not.toHaveBeenCalled()
  })

  it('never logs request/response bodies, params or the query string', async () => {
    server.use(
      http.post('/api/things', () =>
        HttpResponse.json({ message: RESPONSE_BODY_MARKER, amount: RESPONSE_BODY_MARKER }),
      ),
      http.post('/api/conflicts', () =>
        HttpResponse.json({ message: RESPONSE_BODY_MARKER }, { status: 409 }),
      ),
      http.post('/api/boom', () =>
        HttpResponse.json({ message: RESPONSE_BODY_MARKER }, { status: 500 }),
      ),
    )
    const body = { description: REQUEST_BODY_MARKER, amount: REQUEST_BODY_MARKER }

    await apiClient.post('/things', body, { params: { q: QUERY_MARKER } })
    await apiClient.post(`/conflicts?filter=${QUERY_MARKER}`, body).catch(() => undefined)
    await apiClient.post('/boom', body, { params: { q: QUERY_MARKER } }).catch(() => undefined)

    expect(debugSpy).toHaveBeenCalledOnce()
    expect(warnSpy).toHaveBeenCalledOnce()
    expect(errorSpy).toHaveBeenCalledOnce()
    const logged = everythingLogged()
    expect(logged).not.toContain(REQUEST_BODY_MARKER)
    expect(logged).not.toContain(RESPONSE_BODY_MARKER)
    expect(logged).not.toContain(QUERY_MARKER)
    expect(warnSpy.mock.calls[0][0]).toMatch(/^POST \/conflicts 409 \(\d+ ms\)$/)
  })
})
