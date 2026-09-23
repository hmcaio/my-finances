import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedAccounts, seedInvestmentAccount } from '../../mocks/handlers/accounts'
import { renderWithRouter } from '../../test/testUtils'
import { InvestmentsPage } from './InvestmentsPage'

describe('InvestmentsPage', () => {
  it('shows the allocation chart and the investment accounts with links to their pages', async () => {
    server.use(
      http.get('/api/accounts', () => HttpResponse.json([...seedAccounts, seedInvestmentAccount])),
    )
    renderWithRouter(<InvestmentsPage />)

    expect(await screen.findByRole('heading', { name: 'Investments' })).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: 'Allocation by category' })).toBeInTheDocument()
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
})
