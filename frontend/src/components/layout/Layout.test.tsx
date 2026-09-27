import type { ReactNode } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { getTheme } from '../../theme'
import { ColorModeProvider } from '../../hooks/colorMode/ColorModeProvider'
import { restoreViewport, setViewportWidth } from '../../test/viewport'
import { Layout } from './Layout'

function renderLayout({ pageContent }: { pageContent?: ReactNode } = {}) {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <ThemeProvider theme={getTheme('light')}>
        <ColorModeProvider>
          <Layout>{pageContent ?? <p>page content</p>}</Layout>
        </ColorModeProvider>
      </ThemeProvider>
    </MemoryRouter>,
  )
}

describe('Layout', () => {
  afterEach(() => {
    restoreViewport()
  })

  it('shows a permanent drawer and no hamburger on desktop', () => {
    setViewportWidth(1280)
    renderLayout()

    expect(screen.getByRole('link', { name: 'Transactions' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Open navigation' })).not.toBeInTheDocument()
    expect(document.querySelector('.MuiDrawer-docked')).toBeInTheDocument()
  })

  it.each([
    ['tablet', 768],
    ['mobile', 390],
    ['just below lg', 1199],
  ])('hides the nav behind a hamburger on %s', async (_name, width) => {
    setViewportWidth(width)
    const user = userEvent.setup()
    renderLayout()

    expect(document.querySelector('.MuiDrawer-docked')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Transactions' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Open navigation' }))

    expect(await screen.findByRole('link', { name: 'Transactions' })).toBeVisible()
  })

  it('closes the temporary drawer when a nav link is followed', async () => {
    setViewportWidth(390)
    const user = userEvent.setup()
    renderLayout()
    await user.click(screen.getByRole('button', { name: 'Open navigation' }))

    await user.click(await screen.findByRole('link', { name: 'Accounts' }))

    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Accounts' })).not.toBeInTheDocument(),
    )
  })

  it('stays closed when in-page navigation returns to the pathname the drawer was opened from', async () => {
    setViewportWidth(390)
    const user = userEvent.setup()
    renderLayout({ pageContent: <Link to="/">Go home</Link> })

    // Opened while on "/" (the initial route), then navigated away via the drawer link — the
    // drawer correctly closes because the pathname changed.
    await user.click(screen.getByRole('button', { name: 'Open navigation' }))
    await user.click(await screen.findByRole('link', { name: 'Accounts' }))
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Accounts' })).not.toBeInTheDocument(),
    )

    // Page-level navigation (not the hamburger) back to "/" — the exact pathname the drawer was
    // last opened from. It must stay closed: nothing was clicked to open it.
    await user.click(screen.getByRole('link', { name: 'Go home' }))

    expect(screen.queryByRole('link', { name: 'Transactions' })).not.toBeInTheDocument()
  })

  it('keeps the dark-mode toggle reachable at every size', () => {
    for (const width of [390, 768, 1280]) {
      setViewportWidth(width)
      const { unmount } = renderLayout()
      expect(screen.getByRole('button', { name: 'Toggle dark mode' })).toBeVisible()
      unmount()
    }
  })

  it('groups the settings pages under a Settings subheader and keeps Export as a normal item', () => {
    setViewportWidth(1280)
    renderLayout()

    expect(screen.getByText('Settings')).toBeVisible()
    for (const label of [
      'Categories',
      'Investment Categories',
      'Institutions',
      'Payment Methods',
      'Export',
    ]) {
      expect(screen.getByRole('link', { name: label })).toBeVisible()
    }
    expect(screen.getByRole('link', { name: 'Payment Methods' })).toHaveAttribute(
      'href',
      '/settings/payment-methods',
    )
  })

  it('pads the content with 16px below sm and 24px from sm up, capped at 1600px', () => {
    setViewportWidth(390)
    const mobile = renderLayout()
    expect(screen.getByRole('main')).toHaveStyle({ padding: '16px', maxWidth: '1600px' })
    mobile.unmount()

    setViewportWidth(768)
    renderLayout()
    expect(screen.getByRole('main')).toHaveStyle({ padding: '24px', maxWidth: '1600px' })
  })
})
