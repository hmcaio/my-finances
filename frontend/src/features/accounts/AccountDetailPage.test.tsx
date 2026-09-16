import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { seedTransactions } from '../../mocks/handlers/transactions'
import { seedTransfers } from '../../mocks/handlers/transfers'
import { AccountDetailPage } from './AccountDetailPage'

function renderDetail(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/accounts/${id}`]}>
      <Routes>
        <Route path="/accounts/:id" element={<AccountDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AccountDetailPage', () => {
  it('renders the account name, balance, and metadata', async () => {
    const account = seedAccounts[0]
    renderDetail(account.id)

    expect(await screen.findByRole('heading', { name: account.name })).toBeInTheDocument()
    expect(screen.getByText(account.balance.toFixed(2))).toBeInTheDocument()
    // Institution/type render as one line ("Itau · Checking") - the account name itself also
    // contains "Itau", so match the full line rather than the institution substring alone.
    expect(screen.getByText(`${account.institution} · Checking`)).toBeInTheDocument()
  })

  it('embeds the transaction history pre-filtered to this account', async () => {
    const account = seedAccounts[0]
    renderDetail(account.id)

    await screen.findByRole('heading', { name: account.name })

    // Every seed transaction is posted to this account (acct-1) - all should render.
    for (const transaction of seedTransactions) {
      expect(await screen.findByText(transaction.date)).toBeInTheDocument()
    }
  })

  it('embeds the transfer history pre-filtered to this account', async () => {
    const account = seedAccounts[0]
    renderDetail(account.id)

    await screen.findByRole('heading', { name: account.name })

    // Every seed transfer touches this account (acct-1), on either side - all should render.
    for (const transfer of seedTransfers) {
      expect(await screen.findByText(transfer.description)).toBeInTheDocument()
    }
  })

  it('shows an error message for an unknown account', async () => {
    renderDetail('acct-does-not-exist')

    expect(await screen.findByText('Account not found.')).toBeInTheDocument()
  })
})
