import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { Route, Routes, useLocation, useNavigationType, type InitialEntry } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import { seedAccounts, seedInvestmentAccount } from '../../../mocks/handlers/accounts'
import { renderWithRouter } from '../../../test/utils/testUtils'
import { InvestmentsPage } from './InvestmentsPage'

function LocationProbe() {
  const { pathname, search } = useLocation()
  return (
    <>
      <div data-testid="url">{`${pathname}${search}`}</div>
      <div data-testid="navigation-type">{useNavigationType()}</div>
    </>
  )
}

function renderInvestments(entry: InitialEntry = '/investments') {
  return renderWithRouter(
    <>
      <Routes>
        <Route path="/investments" element={<InvestmentsPage />} />
      </Routes>
      <LocationProbe />
    </>,
    { initialEntries: [entry] },
  )
}

const url = () => screen.getByTestId('url').textContent

describe('InvestmentsPage', () => {
  it('shows the three allocation charts and, by default, the investment accounts tab', async () => {
    server.use(
      http.get('/api/accounts', () => HttpResponse.json([...seedAccounts, seedInvestmentAccount])),
    )
    renderWithRouter(<InvestmentsPage />)

    expect(await screen.findByRole('heading', { name: 'Investments' })).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: 'Allocation by category' })).toBeInTheDocument()
    expect(
      await screen.findByRole('img', { name: 'Allocation by sub-category' }),
    ).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: 'Allocation by account' })).toBeInTheDocument()

    const accounts = await screen.findByRole('table', { name: 'Investment accounts' })
    const link = await within(accounts).findByRole('link', { name: seedInvestmentAccount.name })
    expect(link).toHaveAttribute('href', `/accounts/${seedInvestmentAccount.id}`)
    // Other account types are not listed.
    expect(within(accounts).queryByText(seedAccounts[0].name)).not.toBeInTheDocument()
  })

  it('links to the snapshot sheet from the page header (F040)', async () => {
    renderWithRouter(<InvestmentsPage />)

    expect(await screen.findByRole('link', { name: 'Record snapshots' })).toHaveAttribute(
      'href',
      '/investments/snapshots',
    )
  })

  it('points buying and selling at the Ledger, not at the retired Transfers page (F040)', async () => {
    renderWithRouter(<InvestmentsPage />)

    expect(
      await screen.findByText(/Buy and sell from a product's page \(or the Ledger\)/),
    ).toBeVisible()
    expect(screen.queryByText(/Transfers page/)).not.toBeInTheDocument()
  })

  it('hints at adding an investment account when there is none', async () => {
    renderWithRouter(<InvestmentsPage />)

    expect(await screen.findByText(/No investment accounts yet/)).toBeInTheDocument()
  })

  it('switches to the Products tab and shows the global product list', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<InvestmentsPage />)
    await screen.findByRole('img', { name: 'Allocation by category' })

    await user.click(screen.getByRole('tab', { name: 'Products' }))

    const table = await screen.findByRole('table', { name: 'Investment products' })
    expect(await within(table).findByText('Bitcoin')).toBeInTheDocument()
    // "Old CDB"'s only holding is closed, so it's excluded under the default Open status filter.
    expect(within(table).queryByText('Old CDB')).not.toBeInTheDocument()
  })

  describe('the tab in the URL (F040)', () => {
    it('opens the Products tab when the URL says so, as after a reload', async () => {
      renderInvestments('/investments?tab=products')

      expect(await screen.findByRole('tab', { name: 'Products' })).toHaveAttribute(
        'aria-selected',
        'true',
      )
      expect(screen.getByRole('tab', { name: 'Accounts' })).toHaveAttribute(
        'aria-selected',
        'false',
      )
      expect(await screen.findByRole('table', { name: 'Investment products' })).toBeInTheDocument()
      expect(screen.queryByRole('table', { name: 'Investment accounts' })).not.toBeInTheDocument()
    })

    it('shows Accounts for an unknown tab value', async () => {
      renderInvestments('/investments?tab=zzz')

      expect(await screen.findByRole('tab', { name: 'Accounts' })).toHaveAttribute(
        'aria-selected',
        'true',
      )
      expect(await screen.findByRole('table', { name: 'Investment accounts' })).toBeInTheDocument()
    })

    it('writes the tab to the URL with replace, and omits it for Accounts', async () => {
      const user = userEvent.setup({ delay: null })
      renderInvestments()
      await screen.findByRole('img', { name: 'Allocation by category' })
      expect(url()).toBe('/investments')

      await user.click(screen.getByRole('tab', { name: 'Products' }))

      expect(url()).toBe('/investments?tab=products')
      expect(screen.getByTestId('navigation-type')).toHaveTextContent('REPLACE')
      expect(await screen.findByRole('table', { name: 'Investment products' })).toBeInTheDocument()

      await user.click(screen.getByRole('tab', { name: 'Accounts' }))

      expect(url()).toBe('/investments')
      expect(screen.getByTestId('navigation-type')).toHaveTextContent('REPLACE')
      expect(await screen.findByRole('table', { name: 'Investment accounts' })).toBeInTheDocument()
    })
  })
})
