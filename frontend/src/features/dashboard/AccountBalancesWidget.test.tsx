import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { server } from '../../mocks/server'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { AccountBalancesWidget } from './AccountBalancesWidget'

describe('AccountBalancesWidget', () => {
  it('lists each open account with its balance, linking to its detail page', async () => {
    render(
      <MemoryRouter>
        <AccountBalancesWidget />
      </MemoryRouter>,
    )

    const open = seedAccounts.filter((a) => !a.closed)
    for (const account of open) {
      expect(await screen.findByRole('link', { name: account.name })).toHaveAttribute(
        'href',
        `/accounts/${account.id}`,
      )
      expect(
        screen.getAllByText(new RegExp(`^${account.balance.toFixed(2)}`)).length,
      ).toBeGreaterThan(0)
    }
    for (const account of seedAccounts.filter((a) => a.closed)) {
      expect(screen.queryByRole('link', { name: account.name })).not.toBeInTheDocument()
    }
  })

  it('marks a credit card balance as owed', async () => {
    server.use(
      http.get('/api/accounts', () =>
        HttpResponse.json([{ ...seedAccounts[0], type: 'CREDIT_CARD', balance: 80 }]),
      ),
    )

    render(
      <MemoryRouter>
        <AccountBalancesWidget />
      </MemoryRouter>,
    )

    expect(await screen.findByText('80.00 owed')).toBeInTheDocument()
  })

  it('shows a retryable notice when the load fails', async () => {
    server.use(http.get('/api/accounts', () => HttpResponse.json({}, { status: 500 })))

    render(
      <MemoryRouter>
        <AccountBalancesWidget />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('button', { name: /retry/i })).toBeInTheDocument()
  })
})
