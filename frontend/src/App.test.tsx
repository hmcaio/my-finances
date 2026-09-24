import { beforeEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import App from './App'

// jsdom does not implement `matchMedia`, and ColorModeProvider (rendered by App) reads it to pick
// the initial theme - same stub as useColorMode.test.tsx.
function stubMatchMedia() {
  window.matchMedia = ((query: string) => ({
    matches: false,
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
    // The default `GET /api/health` handler already answers UP - no override needed.
    const user = userEvent.setup()
    render(<App />)

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
    render(<App />)
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
      render(<App />)

      expect(await screen.findByRole('heading', { name: /welcome/i })).toBeInTheDocument()
      expect(includeClosed).toBe('true')
      expect(screen.queryByRole('link', { name: 'Accounts' })).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Dashboard' })).not.toBeInTheDocument()
    })

    it('moves into the normal app after the first account is created', async () => {
      stubMatchMedia()
      server.use(http.get('/api/accounts', () => HttpResponse.json([])))
      const user = userEvent.setup()
      render(<App />)

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
      render(<App />)

      expect(await screen.findByText(/Could not load data/)).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: /welcome/i })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Retry' }))

      expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    })
  })
})
