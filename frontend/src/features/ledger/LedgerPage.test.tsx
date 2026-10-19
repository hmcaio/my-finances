import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes, useLocation, type InitialEntry } from 'react-router-dom'
import { server } from '../../mocks/server'
import {
  accountsWithInvestmentHandler,
  seedAccounts,
  seedCheckingAccount,
} from '../../mocks/handlers/accounts'
import {
  seedCategories,
  seedFuelCategory,
  seedGroceriesCategory,
  seedSalaryCategory,
} from '../../mocks/handlers/categories'
import { seedDebitCardPaymentMethod } from '../../mocks/handlers/paymentMethods'
import {
  seedGroceriesTransaction,
  transactionClosedAccountConflictHandler,
} from '../../mocks/handlers/transactions'
import {
  seedBitcoinBuyTransfer,
  seedCreditCardPaymentTransfer,
} from '../../mocks/handlers/transfers'
import { resolveErrorMessage } from '../../test/errorMessages'
import { expectLoadStates } from '../../test/loadStates'
import { findRow, renderWithRouter, selectOption } from '../../test/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { LedgerPage } from './LedgerPage'

/** The seeded rows all fall in January 2026: pin the view to it instead of "this month". */
const JANUARY = '/ledger?month=2026-01'

function LocationProbe() {
  const { pathname, search } = useLocation()
  return <div data-testid="url">{`${pathname}${search}`}</div>
}

function renderLedger(entry: InitialEntry = JANUARY) {
  return renderWithRouter(
    <>
      <Routes>
        <Route path="/ledger" element={<LedgerPage />} />
      </Routes>
      <LocationProbe />
    </>,
    { initialEntries: [entry] },
  )
}

const url = () => screen.getByTestId('url').textContent

/** Rows of the table in document order, as `date description` pairs. */
function rowTexts() {
  return within(screen.getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((row) => row.textContent ?? '')
}

describe('LedgerPage', () => {
  // jsdom has no viewport, which MUI treats as the tablet band: pin the desktop layout.
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  it('lists the month interleaved newest first, with the summary of the whole scope', async () => {
    renderLedger()

    await screen.findByText(seedGroceriesTransaction.description)
    const rows = rowTexts()
    expect(rows).toHaveLength(4)
    expect(rows[0]).toContain('2026-01-25')
    expect(rows[1]).toContain('2026-01-20')
    expect(rows[2]).toContain('2026-01-10')
    expect(rows[3]).toContain('2026-01-05')

    const summary = screen.getByRole('group', { name: 'Summary of this period' })
    expect(within(summary).getByText('3,000.00')).toBeInTheDocument()
    expect(within(summary).getByText('42.50')).toBeInTheDocument()
    expect(within(summary).getByText('2,957.50')).toBeInTheDocument()
  })

  it('marks income green with +, expense red with -, and a transfer neutral and unsigned', async () => {
    renderLedger()
    await screen.findByText(seedGroceriesTransaction.description)

    const income = await findRow('Monthly salary deposit')
    expect(income.getByRole('img', { name: 'Income' })).toBeInTheDocument()
    expect(income.getByText('+3,000.00')).toBeInTheDocument()
    const expense = await findRow(seedGroceriesTransaction.description)
    expect(expense.getByRole('img', { name: 'Expense' })).toBeInTheDocument()
    expect(expense.getByText('-42.50')).toBeInTheDocument()
    const transfer = await findRow(seedCreditCardPaymentTransfer.description)
    expect(transfer.getByRole('img', { name: 'Transfer' })).toBeInTheDocument()
    expect(transfer.getByText('200.00')).toBeInTheDocument()
    expect(transfer.getByText('Itau Checking → Nubank Credit Card')).toBeInTheDocument()
  })

  describe('current month default', () => {
    beforeEach(() => {
      // 23:30 on 31 March in UTC-3 is already April in UTC: the default must be the local month.
      vi.stubEnv('TZ', 'America/Sao_Paulo')
      vi.useFakeTimers({ toFake: ['Date'] })
      vi.setSystemTime(new Date(2026, 2, 31, 23, 30))
    })
    afterEach(() => {
      vi.useRealTimers()
      vi.unstubAllEnvs()
    })

    it('opens on the current local month', async () => {
      renderLedger('/ledger')

      expect(await screen.findByLabelText('Month')).toHaveValue('2026-03')
      expect(await screen.findByText('No transactions or transfers found.')).toBeInTheDocument()
    })
  })

  describe('month navigation', () => {
    it('steps with the arrows, updating the list, the summary and the URL', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)

      await user.click(screen.getByRole('button', { name: 'Next month' }))

      expect(url()).toBe('/ledger?month=2026-02')
      expect(screen.getByLabelText('Month')).toHaveValue('2026-02')
      expect(await screen.findByText('No transactions or transfers found.')).toBeInTheDocument()
      const summary = screen.getByRole('group', { name: 'Summary of this period' })
      expect(within(summary).getAllByText('0.00')).toHaveLength(3)

      await user.click(screen.getByRole('button', { name: 'Previous month' }))
      await user.click(screen.getByRole('button', { name: 'Previous month' }))

      expect(url()).toBe('/ledger?month=2025-12')
    })

    it('rolls the year over and allows future months', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger('/ledger?month=2026-12')

      await user.click(await screen.findByRole('button', { name: 'Next month' }))

      expect(url()).toBe('/ledger?month=2027-01')
    })

    it('follows a month picked in the picker', async () => {
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)

      fireEvent.change(screen.getByLabelText('Month'), { target: { value: '2031-05' } })

      expect(url()).toBe('/ledger?month=2031-05')
    })

    it('restores the month from the URL on load', async () => {
      renderLedger('/ledger?month=2026-01&type=expense')

      expect(await screen.findByLabelText('Month')).toHaveValue('2026-01')
      expect(await screen.findByText(seedGroceriesTransaction.description)).toBeInTheDocument()
      expect(screen.queryByText('Monthly salary deposit')).not.toBeInTheDocument()
    })

    it('falls back to the current month for an invalid month', async () => {
      renderLedger('/ledger?month=garbage')

      const value = (await screen.findByLabelText('Month')) as HTMLInputElement
      expect(value.value).toMatch(/^\d{4}-\d{2}$/)
    })
  })

  describe('range and all time', () => {
    it('switches to a custom range prefilled with the month, and to all time by clearing the dates', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)

      await user.click(screen.getByRole('button', { name: 'Range' }))

      expect(url()).toBe('/ledger?scope=range&from=2026-01-01&to=2026-01-31')
      expect(screen.getByLabelText('From')).toHaveValue('2026-01-01')
      expect(screen.getByLabelText('To')).toHaveValue('2026-01-31')
      expect(screen.queryByRole('button', { name: 'Next month' })).not.toBeInTheDocument()

      fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-01-15' } })
      await waitFor(() =>
        expect(screen.queryByText(seedGroceriesTransaction.description)).toBeNull(),
      )
      expect(url()).toBe('/ledger?scope=range&from=2026-01-15&to=2026-01-31')

      fireEvent.change(screen.getByLabelText('From'), { target: { value: '' } })
      fireEvent.change(screen.getByLabelText('To'), { target: { value: '' } })

      expect(url()).toBe('/ledger?scope=range')
      // All time: the August Bitcoin trades appear too, and the summary covers the whole history.
      // The trade's description also shows as a "Buy Bitcoin" chip, hence the plural query.
      expect(await screen.findAllByText(seedBitcoinBuyTransfer.description)).not.toHaveLength(0)
      expect(screen.getByText(seedGroceriesTransaction.description)).toBeInTheDocument()
    })

    it('goes back to a month view from the Range toggle', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger('/ledger?scope=range&from=2026-01-10&to=2026-02-10')
      await screen.findByLabelText('From')

      await user.click(screen.getByRole('button', { name: 'Month' }))

      expect(url()).toBe('/ledger?month=2026-01')
    })
  })

  describe('filters', () => {
    it('type Transfer shows only transfers and disables category and payment method', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)

      await selectOption(user, 'Type filter', 'Transfer')

      expect(url()).toBe('/ledger?month=2026-01&type=transfer')
      await waitFor(() =>
        expect(screen.queryByText(seedGroceriesTransaction.description)).toBeNull(),
      )
      expect(screen.getByText(seedCreditCardPaymentTransfer.description)).toBeInTheDocument()
      expect(screen.getByRole('combobox', { name: 'Category filter' })).toHaveAttribute(
        'aria-disabled',
        'true',
      )
      expect(screen.getByRole('combobox', { name: 'Payment method filter' })).toHaveAttribute(
        'aria-disabled',
        'true',
      )
    })

    it('choosing Transfer clears a category chosen earlier', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)
      await selectOption(user, 'Category filter', seedGroceriesCategory.name)
      expect(url()).toContain('category=cat-1')

      await selectOption(user, 'Type filter', 'Transfer')

      expect(url()).toBe('/ledger?month=2026-01&type=transfer')
    })

    it('a category hides the transfers and the summary covers only that category', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedCreditCardPaymentTransfer.description)

      await selectOption(user, 'Category filter', seedSalaryCategory.name)

      await waitFor(() =>
        expect(screen.queryByText(seedCreditCardPaymentTransfer.description)).toBeNull(),
      )
      expect(screen.getByText('Monthly salary deposit')).toBeInTheDocument()
      const summary = screen.getByRole('group', { name: 'Summary of this period' })
      // Income and net are both 3,000.00; expense is 0.00 (the groceries are another category).
      expect(within(summary).getAllByText('3,000.00')).toHaveLength(2)
      expect(within(summary).getByText('0.00')).toBeInTheDocument()
    })

    it('offers closed accounts in the account filter, matching either side of a transfer', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)
      const closed = seedAccounts.find((a) => a.closed)!

      await user.click(screen.getByRole('combobox', { name: 'Account filter' }))
      expect(await screen.findByRole('option', { name: closed.name })).toBeInTheDocument()
      await user.click(screen.getByRole('option', { name: 'Nubank Credit Card' }))

      await waitFor(() =>
        expect(screen.queryByText(seedGroceriesTransaction.description)).toBeNull(),
      )
      expect(screen.getByText(seedCreditCardPaymentTransfer.description)).toBeInTheDocument()
      expect(screen.getByText('Refund')).toBeInTheDocument()
    })

    it('Clear filters resets type, account, category, payment method and search but keeps the month', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger('/ledger?month=2026-01&type=expense&account=acct-1&q=groc')
      await screen.findByText(seedGroceriesTransaction.description)

      await user.click(screen.getByRole('button', { name: 'Clear filters' }))

      expect(url()).toBe('/ledger?month=2026-01')
      expect(await screen.findByText('Monthly salary deposit')).toBeInTheDocument()
      expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('')
    })
  })

  describe('search', () => {
    it('debounces typing into the URL (about 300 ms) and narrows the list', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)

      await user.type(screen.getByRole('textbox', { name: 'Search' }), 'salary')
      // Nothing is written while typing; the URL follows once the typing pauses.
      expect(url()).toBe('/ledger?month=2026-01')
      await waitFor(() => expect(url()).toBe('/ledger?month=2026-01&q=salary'))

      await waitFor(() =>
        expect(screen.queryByText(seedGroceriesTransaction.description)).toBeNull(),
      )
      expect(screen.getByText('Monthly salary deposit')).toBeInTheDocument()
    })

    it('ignores case and accents', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedCreditCardPaymentTransfer.description)

      await user.type(screen.getByRole('textbox', { name: 'Search' }), 'CRÉDIT')

      await waitFor(() =>
        expect(screen.queryByText(seedGroceriesTransaction.description)).toBeNull(),
      )
      expect(screen.getByText(seedCreditCardPaymentTransfer.description)).toBeInTheDocument()
    })

    it('replaces the history entry instead of pushing one per search', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)
      const pushed = vi.spyOn(window.history, 'pushState')

      await user.type(screen.getByRole('textbox', { name: 'Search' }), 'sal')
      await waitFor(() => expect(url()).toContain('q=sal'))

      expect(pushed).not.toHaveBeenCalled()
      pushed.mockRestore()
    })

    it('limits the search box to 100 characters', async () => {
      renderLedger()

      expect(await screen.findByRole('textbox', { name: 'Search' })).toHaveAttribute(
        'maxlength',
        '100',
      )
    })

    it('clears with the clear button', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger('/ledger?month=2026-01&q=salary')
      await screen.findByText('Monthly salary deposit')

      await user.click(screen.getByRole('button', { name: 'Clear search' }))

      await waitFor(() => expect(url()).toBe('/ledger?month=2026-01'))
      expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('')
      expect(await screen.findByText(seedGroceriesTransaction.description)).toBeInTheDocument()
    })

    it('offers "Search all time" when a search finds nothing in the period, and it finds the row', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger('/ledger?month=2026-01&q=bitcoin')

      expect(await screen.findByText('No matches in this period.')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Search all time' }))

      expect(url()).toBe('/ledger?scope=range&q=bitcoin')
      expect(await screen.findAllByText(seedBitcoinBuyTransfer.description)).not.toHaveLength(0)
      expect(screen.queryByRole('button', { name: 'Search all time' })).not.toBeInTheDocument()
    })

    it('does not offer "Search all time" when already searching all time', async () => {
      renderLedger('/ledger?scope=range&q=zzzz')

      expect(await screen.findByText('No matches in this period.')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Search all time' })).not.toBeInTheDocument()
    })

    it('does not offer "Search all time" for an empty period without a search', async () => {
      renderLedger('/ledger?month=2020-01')

      expect(await screen.findByText('No transactions or transfers found.')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Search all time' })).not.toBeInTheDocument()
    })
  })

  it('shows the truncation notice while the summary still covers every row', async () => {
    server.use(
      http.get('/api/ledger', () =>
        HttpResponse.json({
          entries: [
            {
              id: 'tx-1',
              type: 'EXPENSE',
              date: '2026-01-05',
              amount: 1,
              description: 'Newest of many',
              additionalNotes: null,
              categoryId: 'cat-1',
              accountId: 'acct-1',
              paymentMethodId: 'pm-1',
              fromAccountId: null,
              toAccountId: null,
              tradeLines: [],
            },
          ],
          summary: { income: 0, expense: 5000, net: -5000 },
          truncated: true,
        }),
      ),
    )
    renderLedger()

    expect(await screen.findByText(/Showing the newest 1 entries/)).toBeInTheDocument()
    const summary = screen.getByRole('group', { name: 'Summary of this period' })
    expect(within(summary).getByText('5,000.00')).toBeInTheDocument()
  })

  describe('adding', () => {
    async function fillAndSubmitTransaction(user: ReturnType<typeof userEvent.setup>) {
      await user.type(await screen.findByRole('spinbutton', { name: 'Amount' }), '15')
      await selectOption(user, 'Category', seedGroceriesCategory.name)
      await selectOption(user, 'Account', seedCheckingAccount.name)
      await selectOption(user, 'Payment Method', seedDebitCardPaymentMethod.name)
      await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Coffee run')
      await user.click(screen.getByRole('button', { name: 'Add' }))
    }

    it('offers a split Add menu with New transaction and New transfer', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)

      await user.click(screen.getByRole('button', { name: 'Add' }))

      expect(await screen.findByRole('menuitem', { name: 'New transaction' })).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'New transfer' })).toBeInTheDocument()
    })

    it('creates a transaction through the New transaction dialog', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger('/ledger?month=2026-01')
      await screen.findByText(seedGroceriesTransaction.description)

      await user.click(screen.getByRole('button', { name: 'Add' }))
      await user.click(await screen.findByRole('menuitem', { name: 'New transaction' }))
      expect(await screen.findByText('Add transaction')).toBeInTheDocument()
      await fillAndSubmitTransaction(user)

      // The form date defaults to today (not January), so the row is not in this month's list:
      // the dialog closing without an error is the visible result here.
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('creates a transfer through the New transfer dialog', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger('/ledger?month=2026-01')
      await screen.findByText(seedGroceriesTransaction.description)

      await user.click(screen.getByRole('button', { name: 'Add' }))
      await user.click(await screen.findByRole('menuitem', { name: 'New transfer' }))
      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByText('Add transfer')).toBeInTheDocument()
      const openAccounts = seedAccounts.filter((a) => !a.closed)
      await user.type(await screen.findByRole('spinbutton', { name: 'Amount' }), '15')
      await selectOption(user, 'From Account', openAccounts[0].name)
      await selectOption(user, 'To Account', openAccounts[1].name)
      await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Move to savings')
      fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2026-01-15' } })
      await user.click(screen.getByRole('button', { name: 'Add' }))

      expect(await screen.findByText('Move to savings')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('shows a save error inside the open transaction dialog', async () => {
      server.use(transactionClosedAccountConflictHandler)
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)

      await user.click(screen.getByRole('button', { name: 'Add' }))
      await user.click(await screen.findByRole('menuitem', { name: 'New transaction' }))
      await fillAndSubmitTransaction(user)

      const dialog = screen.getByRole('dialog')
      expect(
        await within(dialog).findByText(
          resolveErrorMessage('urn:my-finances:error:account-closed'),
        ),
      ).toBeInTheDocument()
    })

    it('opens the new-transaction dialog with the category from the Fuel page preset', async () => {
      server.use(
        http.get('/api/categories', () => HttpResponse.json([...seedCategories, seedFuelCategory])),
      )
      renderLedger({ pathname: '/ledger', state: { presetCategoryId: seedFuelCategory.id } })

      const dialog = await screen.findByRole('dialog')
      expect(await within(dialog).findByRole('combobox', { name: 'Vehicle' })).toBeInTheDocument()
      expect(within(dialog).getByRole('combobox', { name: 'Category' })).toHaveTextContent(
        seedFuelCategory.name,
      )
    })

    it('reveals the fuel fields only for the fuel category (fuel details stay in the dialog)', async () => {
      server.use(
        http.get('/api/categories', () => HttpResponse.json([...seedCategories, seedFuelCategory])),
      )
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await screen.findByText(seedGroceriesTransaction.description)

      await user.click(screen.getByRole('button', { name: 'Add' }))
      await user.click(await screen.findByRole('menuitem', { name: 'New transaction' }))
      expect(screen.queryByRole('combobox', { name: 'Vehicle' })).not.toBeInTheDocument()
      await selectOption(user, 'Category', seedFuelCategory.name)

      expect(await screen.findByRole('combobox', { name: 'Vehicle' })).toBeInTheDocument()
    })
  })

  describe('editing', () => {
    it('fetches a transaction by id and opens its dialog prefilled, then saves', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      const row = await findRow(seedGroceriesTransaction.description)

      await user.click(row.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByText('Edit transaction')).toBeInTheDocument()
      const description = within(dialog).getByRole('textbox', { name: 'Description' })
      expect(description).toHaveValue(seedGroceriesTransaction.description)
      await user.clear(description)
      await user.type(description, 'Updated note')
      await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByText('Updated note')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('fetches a transfer by id and opens the transfer dialog prefilled, then saves', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      const row = await findRow(seedCreditCardPaymentTransfer.description)

      await user.click(row.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByText('Edit transfer')).toBeInTheDocument()
      const description = within(dialog).getByRole('textbox', { name: 'Description' })
      expect(description).toHaveValue(seedCreditCardPaymentTransfer.description)
      await user.clear(description)
      await user.type(description, 'Updated transfer')
      await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByText('Updated transfer')).toBeInTheDocument()
    })

    it('Cancel closes the dialog without saving', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      const row = await findRow(seedGroceriesTransaction.description)
      await user.click(row.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(screen.getByText(seedGroceriesTransaction.description)).toBeInTheDocument()
    })

    it('reports an entry that vanished before its edit dialog could load', async () => {
      server.use(http.get('/api/transactions/:id', () => new HttpResponse(null, { status: 404 })))
      const user = userEvent.setup({ delay: null })
      renderLedger()
      const row = await findRow(seedGroceriesTransaction.description)

      await user.click(row.getByRole('button', { name: 'Edit' }))

      expect(await screen.findByText(/could not be found/i)).toBeInTheDocument()
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  describe('deleting', () => {
    it('deletes a transaction after confirming', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      const row = await findRow(seedGroceriesTransaction.description)

      await user.click(row.getByRole('button', { name: 'Delete' }))
      await screen.findByText('Delete this transaction?')
      await user.click(screen.getByRole('button', { name: 'Delete transaction' }))

      await waitFor(() =>
        expect(screen.queryByText(seedGroceriesTransaction.description)).not.toBeInTheDocument(),
      )
      // The transfer next to it is untouched.
      expect(screen.getByText(seedCreditCardPaymentTransfer.description)).toBeInTheDocument()
    })

    it('deletes a transfer after confirming, through the transfer endpoint', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      const row = await findRow(seedCreditCardPaymentTransfer.description)

      await user.click(row.getByRole('button', { name: 'Delete' }))
      await screen.findByText('Delete this transfer?')
      await user.click(screen.getByRole('button', { name: 'Delete transfer' }))

      await waitFor(() =>
        expect(
          screen.queryByText(seedCreditCardPaymentTransfer.description),
        ).not.toBeInTheDocument(),
      )
      expect(screen.getByText(seedGroceriesTransaction.description)).toBeInTheDocument()
    })
  })

  describe('?focus=', () => {
    it('opens the transfer named by focus for editing and removes only the focus key', async () => {
      server.use(accountsWithInvestmentHandler)
      renderLedger(`/ledger?type=transfer&focus=${seedBitcoinBuyTransfer.id}`)

      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByText('Edit transfer')).toBeInTheDocument()
      expect(
        within(dialog).getByDisplayValue(seedBitcoinBuyTransfer.description),
      ).toBeInTheDocument()
      // Independent of the current period (the trade is in August, the view in this month).
      await waitFor(() => expect(url()).not.toContain('focus='))
      expect(url()).toContain('type=transfer')
    })

    it('shows an error for an unknown transfer id and clears focus', async () => {
      renderLedger('/ledger?focus=trf-missing')

      expect(await screen.findByText(/could not be found/i)).toBeInTheDocument()
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      await waitFor(() => expect(url()).not.toContain('focus='))
    })
  })

  expectLoadStates({
    render: () => renderLedger(),
    url: '/api/accounts',
    successBody: seedAccounts,
    loadedText: seedGroceriesTransaction.description,
  })
})

describe('LedgerPage responsive layout', () => {
  afterEach(restoreViewport)

  async function findCard(description: string) {
    const list = await screen.findByRole('list', { name: 'Ledger' })
    await within(list).findByText(description)
    const items = within(list).getAllByRole('listitem')
    return within(items.find((item) => within(item).queryByText(description))!)
  }

  describe('mobile', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.mobile))

    it('renders cards instead of a table, with the month navigator and summary visible', async () => {
      renderLedger()

      const card = await findCard(seedGroceriesTransaction.description)
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(card.getByText('-42.50')).toBeInTheDocument()
      expect(card.getByRole('img', { name: 'Expense' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Next month' })).toBeInTheDocument()
      expect(screen.getByRole('group', { name: 'Summary of this period' })).toBeInTheDocument()
    })

    it('collapses the filters behind a button with an active-count badge', async () => {
      const user = userEvent.setup({ delay: null })
      renderLedger()
      await findCard(seedGroceriesTransaction.description)
      expect(screen.queryByRole('combobox', { name: 'Type filter' })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Filters' }))
      await selectOption(user, 'Type filter', 'Income')
      await user.click(screen.getByRole('button', { name: 'Done' }))

      expect(await screen.findByRole('button', { name: 'Filters, 1 active' })).toBeInTheDocument()
      expect(await screen.findByText('Monthly salary deposit')).toBeInTheDocument()
      await waitFor(() =>
        expect(screen.queryByText(seedGroceriesTransaction.description)).not.toBeInTheDocument(),
      )
    })
  })

  describe('tablet', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.tablet))

    it('keeps the table without the payment method column, with inline filters', async () => {
      renderLedger()

      await screen.findByText(seedGroceriesTransaction.description)
      expect(screen.getByRole('table')).toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'Payment Method' })).not.toBeInTheDocument()
      expect(screen.getByRole('combobox', { name: 'Type filter' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Filters' })).not.toBeInTheDocument()
    })
  })
})
