import { beforeEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { seedCheckingAccount } from './mocks/handlers/accounts'
import { findRow } from './test/testUtils'
import App from './App'
import { renderWithQueryClient } from './test/renderWithQueryClient'

// jsdom does not implement `matchMedia`, and ColorModeProvider (rendered by App) reads it to pick
// the initial theme - same stub as useColorMode.test.tsx. `min-width` queries match so Layout
// renders its desktop (permanent) drawer and the nav links are in the DOM without opening it.
function stubMatchMedia() {
  window.matchMedia = ((query: string) => ({
    matches: query.includes('min-width'),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  })) as unknown as typeof window.matchMedia
}

describe('App', () => {
  // BrowserRouter reads the real jsdom URL, which the navigation test leaves on /accounts.
  beforeEach(() => window.history.pushState({}, '', '/'))

  it('renders the nav on the dashboard and navigates to another page on a link click', async () => {
    stubMatchMedia()
    const user = userEvent.setup()
    renderWithQueryClient(<App />)

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Accounts' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Accounts' }))

    // AccountsPage renders once routed to - its "Show closed accounts" toggle is a good marker
    // that the Accounts route (not just the nav highlight) actually took over the page.
    expect(await screen.findByRole('switch', { name: 'Show closed accounts' })).toBeInTheDocument()
  })

  it('routes the Export nav link to the data export page', async () => {
    stubMatchMedia()
    const user = userEvent.setup()
    renderWithQueryClient(<App />)
    await screen.findByRole('heading', { name: 'Dashboard' })

    await user.click(screen.getByRole('link', { name: 'Export' }))

    expect(await screen.findByRole('button', { name: 'Download' })).toBeInTheDocument()
  })

  describe('onboarding gate (F011)', () => {
    it('shows onboarding instead of the app shell when no account exists, closed ones included', async () => {
      stubMatchMedia()
      let includeClosed: string | null = null
      server.use(
        http.get('/api/accounts', ({ request }) => {
          includeClosed = new URL(request.url).searchParams.get('includeClosed')
          return HttpResponse.json([])
        }),
      )
      renderWithQueryClient(<App />)

      expect(await screen.findByRole('heading', { name: /welcome/i })).toBeInTheDocument()
      expect(includeClosed).toBe('true')
      expect(screen.queryByRole('link', { name: 'Accounts' })).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Dashboard' })).not.toBeInTheDocument()
    })

    it('returns to onboarding after the last account is deleted', async () => {
      stubMatchMedia()
      window.history.pushState({}, '', '/accounts')
      // One open account with no history; the delete empties the list the gate refetches.
      const only = { ...seedCheckingAccount }
      let rows = [only]
      server.use(
        http.get('/api/accounts', () => HttpResponse.json(rows)),
        http.delete('/api/accounts/:id', () => {
          rows = []
          return new HttpResponse(null, { status: 204 })
        }),
      )
      const user = userEvent.setup()
      renderWithQueryClient(<App />)

      const row = await findRow(only.name)
      await user.click(row.getByRole('button', { name: 'Delete' }))
      await user.click(screen.getByRole('button', { name: 'Delete account' }))

      expect(await screen.findByRole('heading', { name: /welcome/i })).toBeInTheDocument()
    })

    it('moves into the normal app after the first account is created', async () => {
      stubMatchMedia()
      // An empty database until the first account is created (a refetch must see it).
      let created = false
      server.use(
        http.get('/api/accounts', () => (created ? undefined : HttpResponse.json([]))),
        http.post('/api/accounts', () => {
          created = true
        }),
      )
      const user = userEvent.setup()
      renderWithQueryClient(<App />)

      await screen.findByRole('heading', { name: /welcome/i })
      await screen.findByRole('combobox', { name: 'Institution' })
      await user.type(screen.getByLabelText('Name'), 'Main checking')
      await user.click(screen.getByRole('button', { name: 'Create account' }))

      expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: /welcome/i })).not.toBeInTheDocument()
    })

    it('shows a retryable load error, not onboarding, when the accounts check fails', async () => {
      stubMatchMedia()
      server.use(
        http.get('/api/accounts', () => new HttpResponse(null, { status: 500 }), { once: true }),
      )
      const user = userEvent.setup()
      renderWithQueryClient(<App />)

      expect(await screen.findByText(/Could not load data/)).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: /welcome/i })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Retry' }))

      expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    })
  })
})
