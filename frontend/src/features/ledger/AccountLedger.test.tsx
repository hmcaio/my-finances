import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { server } from '../../mocks/server'
import { seedCheckingAccount } from '../../mocks/handlers/accounts'
import { seedGroceriesTransaction } from '../../mocks/handlers/transactions'
import { seedCreditCardPaymentTransfer } from '../../mocks/handlers/transfers'
import { renderWithRouter } from '../../test/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { AccountLedger } from './AccountLedger'

describe('AccountLedger', () => {
  beforeEach(() => {
    setViewportWidth(VIEWPORT.desktop)
    // The seeded rows are all in January 2026, and the embed opens on the current month: pin the
    // clock there (only Date is faked, so MSW and RTL timers keep running).
    vi.stubEnv('TZ', 'America/Sao_Paulo')
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 0, 15, 12, 0))
  })
  afterEach(() => {
    restoreViewport()
    vi.useRealTimers()
    vi.unstubAllEnvs()
  })

  it('asks for this account and the current month, and lists its transactions and transfers', async () => {
    const requests: URL[] = []
    server.use(
      http.get('/api/ledger', ({ request }) => {
        requests.push(new URL(request.url))
        return undefined
      }),
    )
    renderWithRouter(<AccountLedger accountId={seedCheckingAccount.id} />)

    expect(await screen.findByText(seedGroceriesTransaction.description)).toBeInTheDocument()
    expect(screen.getByText(seedCreditCardPaymentTransfer.description)).toBeInTheDocument()
    expect(requests[0].searchParams.get('accountId')).toBe(seedCheckingAccount.id)
    expect(requests[0].searchParams.get('dateFrom')).toBe('2026-01-01')
    expect(requests[0].searchParams.get('dateTo')).toBe('2026-01-31')
    expect(screen.getByLabelText('Month')).toHaveValue('2026-01')
  })

  it('shows the income, expense and net of the month', async () => {
    renderWithRouter(<AccountLedger accountId={seedCheckingAccount.id} />)
    await screen.findByText(seedGroceriesTransaction.description)

    const summary = screen.getByRole('group', { name: 'Summary of this period' })
    expect(within(summary).getAllByText('3,000.00')).toHaveLength(1)
    expect(within(summary).getByText('42.50')).toBeInTheDocument()
    expect(within(summary).getByText('2,957.50')).toBeInTheDocument()
  })

  it('steps through months in local state, re-requesting for the new month', async () => {
    const user = userEvent.setup({ delay: null })
    const requests: URL[] = []
    server.use(
      http.get('/api/ledger', ({ request }) => {
        requests.push(new URL(request.url))
        return undefined
      }),
    )
    renderWithRouter(<AccountLedger accountId={seedCheckingAccount.id} />)
    await screen.findByText(seedGroceriesTransaction.description)

    await user.click(screen.getByRole('button', { name: 'Next month' }))

    expect(screen.getByLabelText('Month')).toHaveValue('2026-02')
    expect(
      await screen.findByText('No transactions or transfers in this month.'),
    ).toBeInTheDocument()
    const last = requests[requests.length - 1]
    expect(last.searchParams.get('dateFrom')).toBe('2026-02-01')
    expect(last.searchParams.get('dateTo')).toBe('2026-02-28')
    expect(screen.queryByText(seedGroceriesTransaction.description)).not.toBeInTheDocument()
  })

  it('is read-only: no edit, delete or add controls', async () => {
    renderWithRouter(<AccountLedger accountId={seedCheckingAccount.id} />)
    await screen.findByText(seedGroceriesTransaction.description)

    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Search' })).not.toBeInTheDocument()
  })

  it('links to the full Ledger for this account and month', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithRouter(<AccountLedger accountId={seedCheckingAccount.id} />)
    await screen.findByText(seedGroceriesTransaction.description)

    expect(screen.getByRole('link', { name: 'View in Ledger' })).toHaveAttribute(
      'href',
      `/ledger?account=${seedCheckingAccount.id}&month=2026-01`,
    )

    await user.click(screen.getByRole('button', { name: 'Previous month' }))

    expect(screen.getByRole('link', { name: 'View in Ledger' })).toHaveAttribute(
      'href',
      `/ledger?account=${seedCheckingAccount.id}&month=2025-12`,
    )
  })
})
