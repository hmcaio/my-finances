import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { server } from '../../mocks/server'
import { seedCheckingAccount, seedInvestmentAccount } from '../../mocks/handlers/accounts'
import { seedInvestmentProducts } from '../../mocks/handlers/investmentProducts'
import { BUILT_IN_INSTITUTION_ID, seedInstitutions } from '../../mocks/handlers/institutions'
import { seedGroceriesTransaction } from '../../mocks/handlers/transactions'
import { seedCreditCardPaymentTransfer } from '../../mocks/handlers/transfers'
import { renderWithRouter } from '../../test/testUtils'
import { formatMoney } from '../../i18n/format'
import { AccountDetailPage } from './AccountDetailPage'

function renderDetail(id: string) {
  return renderWithRouter(
    <Routes>
      <Route path="/accounts/:id" element={<AccountDetailPage />} />
    </Routes>,
    {
      initialEntries: [`/accounts/${id}`],
    },
  )
}

function institutionName(id: string) {
  return seedInstitutions.find((i) => i.id === id)!.name
}

describe('AccountDetailPage', () => {
  beforeEach(() => {
    // The embedded ledger opens on the current month and the seeded rows are all in January 2026:
    // pin the clock there (only Date is faked, so MSW and RTL timers keep running).
    vi.stubEnv('TZ', 'America/Sao_Paulo')
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 0, 15, 12, 0))
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllEnvs()
  })

  it('renders the account name, balance, and metadata', async () => {
    const account = seedCheckingAccount
    renderDetail(account.id)

    expect(await screen.findByRole('heading', { name: account.name })).toBeInTheDocument()
    expect(screen.getByText(formatMoney(account.balance, 'en'))).toBeInTheDocument()
    // The institution name (looked up from the institutions list) and type render as one line
    // ("Itau · Checking") - the account name itself also
    // contains "Itau", so match the full line rather than the institution substring alone.
    expect(
      screen.getByText(`${institutionName(account.institutionId)} · Checking`),
    ).toBeInTheDocument()
  })

  it('shows the built-in row for an account with no institution', async () => {
    const account = { ...seedCheckingAccount, institutionId: BUILT_IN_INSTITUTION_ID }
    server.use(http.get(`/api/accounts/${account.id}`, () => HttpResponse.json(account)))
    renderDetail(account.id)

    expect(await screen.findByText(`No institution · Checking`)).toBeInTheDocument()
  })

  it('shows an error when the institutions cannot be loaded', async () => {
    server.use(http.get('/api/institutions', () => new HttpResponse(null, { status: 500 })))
    renderDetail(seedCheckingAccount.id)

    // No conflictMessage applies to a GET, and the backend sends no body here, so this is
    // apiError.ts's own generic `toApiError` fallback ("Request failed with status ${status}") -
    // not Axios's literal default wording (which reads "status code", not "status").
    expect(await screen.findByText('Request failed with status 500')).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: seedCheckingAccount.name }),
    ).not.toBeInTheDocument()
  })

  it('embeds one read-only ledger section scoped to this account, transactions and transfers together', async () => {
    const account = seedCheckingAccount
    renderDetail(account.id)

    await screen.findByRole('heading', { name: account.name })

    // One section for both kinds (no separate Transactions / Transfers sections any more).
    expect(screen.getByRole('heading', { name: 'Transactions & transfers' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Transactions' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Transfers' })).not.toBeInTheDocument()
    expect(await screen.findByText(seedGroceriesTransaction.description)).toBeInTheDocument()
    expect(await screen.findByText(seedCreditCardPaymentTransfer.description)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View in Ledger' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
  })

  it('shows an error message for an unknown account', async () => {
    renderDetail('acct-does-not-exist')

    expect(await screen.findByText('Account not found.')).toBeInTheDocument()
  })

  it('shows a loading skeleton only when the account fetch is slow', async () => {
    const account = seedCheckingAccount
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

  it('lists the products of an INVESTMENT account above its ledger section', async () => {
    renderDetail(seedInvestmentAccount.id)

    expect(
      await screen.findByRole('heading', { name: seedInvestmentAccount.name }),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Products' })).toBeInTheDocument()
    for (const product of seedInvestmentProducts) {
      expect(await screen.findByText(product.name)).toBeInTheDocument()
    }
    // The same single ledger section follows (it takes no transactions, so it lists transfers).
    expect(screen.getByRole('heading', { name: 'Transactions & transfers' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View in Ledger' })).toBeInTheDocument()
  })

  it('handles the missing opening balance of an INVESTMENT account', async () => {
    renderDetail(seedInvestmentAccount.id)

    expect(await screen.findByText(/No opening balance/)).toBeInTheDocument()
    expect(screen.getByText('0.00')).toBeInTheDocument()
    expect(screen.queryByText(/Opening balance/)).not.toBeInTheDocument()
    expect(
      screen.getByText(`${institutionName(seedInvestmentAccount.institutionId)} · Investment`),
    ).toBeInTheDocument()
  })

  it('shows the ledger section but no products for a non-investment account', async () => {
    renderDetail(seedCheckingAccount.id)

    expect(
      await screen.findByRole('heading', { name: 'Transactions & transfers' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Products' })).not.toBeInTheDocument()
  })
})
