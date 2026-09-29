import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAccounts, seedInvestmentAccount } from '../../mocks/handlers/accounts'
import { renderWithRouter } from '../../test/testUtils'
import { InvestmentsPage } from './InvestmentsPage'

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

  it('hints at adding an investment account when there is none', async () => {
    renderWithRouter(<InvestmentsPage />)

    expect(await screen.findByText(/No investment accounts yet/)).toBeInTheDocument()
  })

  it('switches to the Products tab and shows the global product list', async () => {
    const user = userEvent.setup()
    renderWithRouter(<InvestmentsPage />)
    await screen.findByRole('img', { name: 'Allocation by category' })

    await user.click(screen.getByRole('tab', { name: 'Products' }))

    const table = await screen.findByRole('table', { name: 'Investment products' })
    expect(await within(table).findByText('Bitcoin')).toBeInTheDocument()
    // "Old CDB"'s only holding is closed, so it's excluded under the default Open status filter.
    expect(within(table).queryByText('Old CDB')).not.toBeInTheDocument()
  })
})
