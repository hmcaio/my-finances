import { afterEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClientProvider } from '@tanstack/react-query'
import { getTheme } from '../theme'
import { ColorModeProvider } from '../hooks/colorMode/ColorModeProvider'
import { LocaleProvider } from '../hooks/locale/LocaleProvider'
import { createTestQueryClient } from '../test/renderWithQueryClient'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../test/viewport'
import { Layout } from '../components/layout/Layout'
import { NetWorthTrendChart } from '../features/netWorth/NetWorthTrendChart'

/**
 * F033/ADR 0027's one integration-level test (spec's Testing section): the real header toggle,
 * through the real `LocaleProvider`, switching both a translated nav string (`Layout`) and a
 * locale-formatted amount (`NetWorthTrendChart`'s `formatMoney`) in the same render - unlike
 * `useLocale.test.tsx` (the provider in isolation) or `format.test.ts` (the formatter functions in
 * isolation), neither of which exercises the real toggle driving a real page.
 */
describe('i18n end-to-end', () => {
  afterEach(() => {
    restoreViewport()
  })

  it("toggling the header language control switches a nav string and an amount's separator", async () => {
    setViewportWidth(VIEWPORT.desktop)
    const user = userEvent.setup({ delay: null })
    const client = createTestQueryClient()

    render(
      <MemoryRouter initialEntries={['/']}>
        <ThemeProvider theme={getTheme('light')}>
          <ColorModeProvider>
            <LocaleProvider>
              <QueryClientProvider client={client}>
                <Layout>
                  <NetWorthTrendChart />
                </Layout>
              </QueryClientProvider>
            </LocaleProvider>
          </ColorModeProvider>
        </ThemeProvider>
      </MemoryRouter>,
    )

    // English by default (jsdom's navigator.language): the nav label and the latest point's net
    // worth (1700, from the seeded trend) both render in English/dot-decimal.
    expect(screen.getByRole('link', { name: 'Transactions' })).toBeInTheDocument()
    expect(await screen.findByText(/^As of .*net worth 1,700\.00/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Switch to Portuguese (Brazil)' }))

    // Same nav item, now in Portuguese; same amount, now comma-decimal/dot-thousands - both
    // switch immediately, with no remount and no re-fetch (the data is already in cache).
    expect(screen.getByRole('link', { name: 'Transações' })).toBeInTheDocument()
    expect(await screen.findByText(/^Em .*patrimônio líquido 1\.700,00/)).toBeInTheDocument()
  })
})
