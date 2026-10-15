import { afterEach, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { MOCK_PASSWORD, MOCK_USERNAME, signOutMockSession } from '../../mocks/handlers/auth'
import { apiClient } from '../core/client'
import { ApiError } from '../core/apiError'
import { getSession, login, logout } from './auth'

describe('apiClient credentials (F036)', () => {
  afterEach(() => {
    // Expire the cookie the test set so it can't leak into another test file's requests.
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('is configured to carry the session cookie and echo the XSRF cookie', () => {
    expect(apiClient.defaults.withCredentials).toBe(true)
    expect(apiClient.defaults.withXSRFToken).toBe(true)
  })

  it('sends the XSRF-TOKEN cookie value as the X-XSRF-TOKEN header', async () => {
    document.cookie = 'XSRF-TOKEN=token-from-cookie; path=/'
    let received: string | null = null
    server.use(
      http.post('/api/categories', ({ request }) => {
        received = request.headers.get('X-XSRF-TOKEN')
        return HttpResponse.json({}, { status: 201 })
      }),
    )

    await apiClient.post('/categories', {})

    expect(received).toBe('token-from-cookie')
  })
})

describe('auth API', () => {
  it('getSession reports an active session', async () => {
    await expect(getSession()).resolves.toEqual({ authenticated: true })
  })

  it('getSession folds a 401 into { authenticated: false }', async () => {
    signOutMockSession()

    await expect(getSession()).resolves.toEqual({ authenticated: false })
  })

  it('getSession still throws for a non-401 failure', async () => {
    server.use(http.get('/api/auth/session', () => new HttpResponse(null, { status: 500 })))

    await expect(getSession()).rejects.toMatchObject({ status: 500 })
  })

  it('login resolves for the right credential and rejects with a 401 ApiError otherwise', async () => {
    signOutMockSession()

    await expect(login({ username: MOCK_USERNAME, password: MOCK_PASSWORD })).resolves.toEqual({
      authenticated: true,
    })
    const failure = await login({ username: MOCK_USERNAME, password: 'nope' }).catch(
      (e: unknown) => e,
    )
    expect(failure).toBeInstanceOf(ApiError)
    expect(failure).toMatchObject({ status: 401, type: 'urn:my-finances:error:unauthenticated' })
  })

  it('logout ends the session', async () => {
    await logout()

    await expect(getSession()).resolves.toEqual({ authenticated: false })
  })
})
