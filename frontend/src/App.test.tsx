import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
})
