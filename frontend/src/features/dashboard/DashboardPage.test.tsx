import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { server } from '../../mocks/server'
import { seedDebitCardPaymentMethod } from '../../mocks/handlers/paymentMethods'
import { findRow } from '../../test/testUtils'
import { DashboardPage } from './DashboardPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

function renderDashboard() {
  return renderWithQueryClient(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
}

describe('DashboardPage', () => {
  it('renders every widget with data from its owning feature', async () => {
    renderDashboard()

    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()

    const spend = screen.getByRole('region', { name: 'Spend by category' })
    expect(await within(spend).findByText('Total 42.50')).toBeInTheDocument()

    const budgets = screen.getByRole('region', { name: 'Budget vs. actual' })
    expect(await within(budgets).findByText(/620\.00 \/ 500\.00 — over budget/)).toBeInTheDocument()

    const accounts = screen.getByRole('region', { name: 'Account balances' })
    expect(await within(accounts).findByRole('link', { name: 'Itau Checking' })).toBeInTheDocument()

    const netWorth = screen.getByRole('region', { name: 'Net worth' })
    expect(
      await within(netWorth).findByRole('img', { name: 'Net worth by month' }),
    ).toBeInTheDocument()

    const allocation = screen.getByRole('region', { name: 'Investment allocation' })
    expect(await within(allocation).findByRole('img')).toBeInTheDocument()

    expect(screen.getByRole('heading', { name: 'Upcoming recurring bills' })).toBeInTheDocument()
    expect(await findRow('Rent')).toBeDefined()
  })

  it('refreshes the transaction-dependent widgets after confirming a pending occurrence', async () => {
    const user = userEvent.setup({ delay: null })
    let spendRequests = 0
    server.use(
      http.get('/api/transactions/spend-by-category', () => {
        spendRequests += 1
        return HttpResponse.json(
          spendRequests === 1
            ? [{ categoryId: 'cat-1', total: 42.5 }]
            : [{ categoryId: 'cat-1', total: 1692.5 }],
        )
      }),
    )
    renderDashboard()
    const spend = screen.getByRole('region', { name: 'Spend by category' })
    expect(await within(spend).findByText('Total 42.50')).toBeInTheDocument()

    const row = await findRow('Rent')
    await user.click(row.getByRole('button', { name: 'Confirm occurrence' }))
    await user.click(screen.getByLabelText('Payment Method'))
    await user.click(await screen.findByRole('option', { name: seedDebitCardPaymentMethod.name }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await within(spend).findByText('Total 1692.50')).toBeInTheDocument()
    expect(screen.getByText('Nothing pending right now.')).toBeInTheDocument()
  })

  it('removes a dismissed occurrence without refetching the other widgets', async () => {
    const user = userEvent.setup({ delay: null })
    let spendRequests = 0
    server.use(
      http.get('/api/transactions/spend-by-category', () => {
        spendRequests += 1
        return HttpResponse.json([{ categoryId: 'cat-1', total: 42.5 }])
      }),
    )
    renderDashboard()
    const row = await findRow('Rent')

    await user.click(row.getByRole('button', { name: 'Dismiss occurrence' }))
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))

    expect(await screen.findByText('Nothing pending right now.')).toBeInTheDocument()
    expect(spendRequests).toBe(1)
  })
})
