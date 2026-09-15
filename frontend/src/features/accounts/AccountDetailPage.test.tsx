import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { seedAccounts } from '../../mocks/handlers/accounts'
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

  it('shows a placeholder for transaction/transfer history', async () => {
    const account = seedAccounts[0]
    renderDetail(account.id)

    await screen.findByRole('heading', { name: account.name })

    expect(screen.getByText(/Not available yet/)).toBeInTheDocument()
  })

  it('shows an error message for an unknown account', async () => {
    renderDetail('acct-does-not-exist')

    expect(await screen.findByText('Account not found.')).toBeInTheDocument()
  })
})
