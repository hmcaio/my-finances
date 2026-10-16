import { http, HttpResponse } from 'msw'
import { onResetStores } from '../store'

/** The one credential the mock backend accepts (F036). */
export const MOCK_USERNAME = 'owner'
export const MOCK_PASSWORD = 'correct-horse-battery'

/**
 * Whether the mock backend currently has a session. Starts `true` so every page/e2e test behaves
 * as a signed-in user without ceremony; a test of the login flow calls `signOutMockSession()`
 * first. Reset after every test (and per Playwright worker via `resetStores`).
 */
let authenticated = true
onResetStores(() => {
  authenticated = true
})

export function signOutMockSession(): void {
  authenticated = false
}

export function isMockSessionActive(): boolean {
  return authenticated
}

function problem(status: number, slug: string, title: string, instance: string) {
  return HttpResponse.json(
    { type: `urn:my-finances:error:${slug}`, title, status, instance },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

export const authHandlers = [
  http.get('/api/auth/session', () =>
    authenticated
      ? HttpResponse.json({ authenticated: true })
      : problem(401, 'unauthenticated', 'Unauthenticated', '/api/auth/session'),
  ),
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { username?: string; password?: string }
    if (body.username === MOCK_USERNAME && body.password === MOCK_PASSWORD) {
      authenticated = true
      return HttpResponse.json({ authenticated: true })
    }
    return problem(401, 'unauthenticated', 'Unauthenticated', '/api/auth/login')
  }),
  http.post('/api/auth/logout', () => {
    if (!authenticated) {
      return problem(401, 'unauthenticated', 'Unauthenticated', '/api/auth/logout')
    }
    authenticated = false
    return new HttpResponse(null, { status: 204 })
  }),
]
