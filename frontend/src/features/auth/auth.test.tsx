import { beforeEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  MOCK_PASSWORD,
  MOCK_USERNAME,
  isMockSessionActive,
  signOutMockSession,
} from '../../mocks/handlers/auth'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import App from '../../App'

// jsdom has no matchMedia; ColorModeProvider and Layout read it (same stub as App.test.tsx).
function stubMatchMedia() {
  window.matchMedia = ((query: string) => ({
    matches: query.includes('min-width'),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  })) as unknown as typeof window.matchMedia
}

function problem(status: number, slug: string) {
  return HttpResponse.json(
    { type: `urn:my-finances:error:${slug}`, title: slug, status, instance: '/api/x' },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

async function signIn(
  user: ReturnType<typeof userEvent.setup>,
  username = MOCK_USERNAME,
  password = MOCK_PASSWORD,
) {
  await user.type(await screen.findByLabelText(/username/i), username)
  await user.type(screen.getByLabelText(/password/i), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('authentication (F036)', () => {
  beforeEach(() => {
    stubMatchMedia()
    window.history.pushState({}, '', '/')
  })

  describe('route guard', () => {
    it('redirects a signed-out visit to /login and remembers the requested path', async () => {
      signOutMockSession()
      window.history.pushState({}, '', '/accounts?includeClosed=true')
      renderWithQueryClient(<App />)

      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
      expect(window.location.pathname).toBe('/login')
      // Nothing behind the guard rendered or fetched.
      expect(screen.queryByRole('link', { name: 'Accounts' })).not.toBeInTheDocument()
    })

    it('returns to the originally requested page after a successful login', async () => {
      signOutMockSession()
      window.history.pushState({}, '', '/accounts')
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<App />)

      await signIn(user)

      expect(
        await screen.findByRole('switch', { name: 'Show closed accounts' }),
      ).toBeInTheDocument()
      expect(window.location.pathname).toBe('/accounts')
    })

    it('goes to the dashboard after login when nothing in particular was requested', async () => {
      signOutMockSession()
      window.history.pushState({}, '', '/login')
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<App />)

      await signIn(user)

      expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
      expect(window.location.pathname).toBe('/')
    })

    it('sends a signed-in visit to /login straight to the app', async () => {
      window.history.pushState({}, '', '/login')
      renderWithQueryClient(<App />)

      expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
      expect(window.location.pathname).toBe('/')
    })

    it('redirects to /login when any API call, not just the session check, answers 401', async () => {
      // The session check still says "signed in" - only a data call finds out it has expired.
      server.use(
        http.get('/api/accounts', () => problem(401, 'unauthenticated')),
        http.get('/api/categories', () => problem(401, 'unauthenticated')),
      )
      window.history.pushState({}, '', '/accounts')
      renderWithQueryClient(<App />)

      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
      expect(window.location.pathname).toBe('/login')
    })

    it('offers a retry instead of the login form when the session check itself fails', async () => {
      server.use(http.get('/api/auth/session', () => new HttpResponse(null, { status: 503 })))
      renderWithQueryClient(<App />)

      expect(await screen.findByRole('button', { name: 'Retry' })).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument()
    })
  })

  describe('login form', () => {
    it('shows one fixed message for wrong credentials and stays on /login', async () => {
      signOutMockSession()
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<App />)

      await signIn(user, MOCK_USERNAME, 'not-the-password')

      expect(await screen.findByText('Incorrect username or password.')).toBeInTheDocument()
      expect(window.location.pathname).toBe('/login')
      expect(isMockSessionActive()).toBe(false)
    })

    it('shows the cooldown message on 429', async () => {
      signOutMockSession()
      server.use(http.post('/api/auth/login', () => problem(429, 'too-many-login-attempts')))
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<App />)

      await signIn(user)

      expect(await screen.findByText(/too many failed sign-in attempts/i)).toBeInTheDocument()
    })

    it('can retry after a failure', async () => {
      signOutMockSession()
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<App />)
      await signIn(user, MOCK_USERNAME, 'wrong')
      await screen.findByText('Incorrect username or password.')

      await user.clear(screen.getByLabelText(/password/i))
      await user.type(screen.getByLabelText(/password/i), MOCK_PASSWORD)
      await user.click(screen.getByRole('button', { name: 'Sign in' }))

      expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    })
  })

  describe('logout', () => {
    it('ends the session from the nav and lands on /login', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<App />)
      await screen.findByRole('heading', { name: 'Dashboard' })

      await user.click(screen.getByRole('button', { name: 'Log out' }))

      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
      expect(window.location.pathname).toBe('/login')
      expect(isMockSessionActive()).toBe(false)
    })
  })
})
