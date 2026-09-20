import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { server } from '../../mocks/server'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { BUILT_IN_INSTITUTION_ID, seedInstitutions } from '../../mocks/handlers/institutions'
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

function institutionName(id: string) {
  return seedInstitutions.find((i) => i.id === id)!.name
}

describe('AccountDetailPage', () => {
  it('renders the account name, balance, and metadata', async () => {
    const account = seedAccounts[0]
    renderDetail(account.id)

    expect(await screen.findByRole('heading', { name: account.name })).toBeInTheDocument()
    expect(screen.getByText(account.balance.toFixed(2))).toBeInTheDocument()
    // The institution name (looked up from the institutions list) and type render as one line
    // ("Itau · Checking") - the account name itself also
    // contains "Itau", so match the full line rather than the institution substring alone.
    expect(
      screen.getByText(`${institutionName(account.institutionId)} · Checking`),
    ).toBeInTheDocument()
  })

  it('shows the built-in row for an account with no institution', async () => {
    const account = { ...seedAccounts[0], institutionId: BUILT_IN_INSTITUTION_ID }
    server.use(http.get(`/api/accounts/${account.id}`, () => HttpResponse.json(account)))
    renderDetail(account.id)

    expect(await screen.findByText(`No institution · Checking`)).toBeInTheDocument()
  })

  it('shows an error when the institutions cannot be loaded', async () => {
    server.use(http.get('/api/institutions', () => new HttpResponse(null, { status: 500 })))
    renderDetail(seedAccounts[0].id)

    expect(await screen.findByText(/Request failed/)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: seedAccounts[0].name })).not.toBeInTheDocument()
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

  it('shows a loading skeleton only when the account fetch is slow', async () => {
    const account = seedAccounts[0]
    server.use(
      http.get(`/api/accounts/${account.id}`, async () => {
        await delay(400)
        return HttpResponse.json(account)
      }),
    )
    renderDetail(account.id)

    expect(await screen.findByRole('status', { name: 'Loading account' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: account.name })).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Loading account' })).not.toBeInTheDocument()
  })
})
