import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import {
  accountsWithInvestmentHandler,
  seedAccounts,
  seedInvestmentAccount,
} from '../../mocks/handlers/accounts'
import { seedCategories, seedGroceriesCategory } from '../../mocks/handlers/categories'
import { seedDebitCardPaymentMethod, seedPaymentMethods } from '../../mocks/handlers/paymentMethods'
import {
  seedGroceriesTransaction,
  seedTransactions,
  transactionClosedAccountConflictHandler,
} from '../../mocks/handlers/transactions'
import { CLOSED_ACCOUNT_MESSAGE } from '../../api/transactions/transactions'
import { findRow, selectOption } from '../../test/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { TransactionsPage } from './TransactionsPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('TransactionsPage', () => {
  // jsdom has no viewport, which MUI treats as the tablet band (payment method column hidden):
  // these tests assert the full desktop table.
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  it('renders the seeded transactions', async () => {
    renderWithQueryClient(<TransactionsPage />)

    for (const transaction of seedTransactions) {
      expect(await screen.findByText(transaction.description)).toBeInTheDocument()
    }
  })

  it('filters by category', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    const incomeCategory = seedCategories.find((c) => c.type === 'INCOME')!
    await selectOption(user, 'Category filter', incomeCategory.name)

    const incomeTransaction = seedTransactions.find((t) => t.categoryId === incomeCategory.id)!
    const expenseTransaction = seedTransactions.find((t) => t.categoryId !== incomeCategory.id)!
    expect(await screen.findByText(incomeTransaction.description)).toBeInTheDocument()
    expect(screen.queryByText(expenseTransaction.description)).not.toBeInTheDocument()
  })

  it('adds a new transaction through the Add dialog', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    await user.click(screen.getByRole('button', { name: 'Add transaction' }))
    await user.type(await screen.findByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'Category', seedGroceriesCategory.name)
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await selectOption(user, 'Account', openAccount.name)
    await selectOption(user, 'Payment Method', seedDebitCardPaymentMethod.name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Coffee run')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Coffee run')).toBeInTheDocument()
  })

  it('excludes closed accounts from the create/edit form account dropdown', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    const closedAccount = seedAccounts.find((a) => a.closed)!
    await user.click(screen.getByRole('button', { name: 'Add transaction' }))
    await user.click(await screen.findByRole('combobox', { name: 'Account' }))

    expect(screen.queryByRole('option', { name: closedAccount.name })).not.toBeInTheDocument()
  })

  it('never offers an investment account in the form account dropdown', async () => {
    server.use(accountsWithInvestmentHandler)
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    await user.click(screen.getByRole('button', { name: 'Add transaction' }))
    await user.click(await screen.findByRole('combobox', { name: 'Account' }))

    const openAccount = seedAccounts.find((a) => !a.closed)!
    expect(await screen.findByRole('option', { name: openAccount.name })).toBeInTheDocument()
    expect(
      screen.queryByRole('option', { name: seedInvestmentAccount.name }),
    ).not.toBeInTheDocument()
  })

  it('edits a transaction', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<TransactionsPage />)
    const target = seedGroceriesTransaction
    await screen.findByText(target.description)

    const row = await findRow(target.description)
    await user.click(row.getByRole('button', { name: 'Edit' }))

    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    const descriptionInput = screen.getByRole('textbox', { name: 'Description' })
    await user.clear(descriptionInput)
    await user.type(descriptionInput, 'Updated note')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Updated note')).toBeInTheDocument()
  })

  it('deletes a transaction after confirming the dialog', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<TransactionsPage />)
    const target = seedGroceriesTransaction
    await screen.findByText(target.description)

    const row = await findRow(target.description)
    await user.click(row.getByRole('button', { name: 'Delete' }))
    await screen.findByText('Delete this transaction?')
    await user.click(screen.getByRole('button', { name: 'Delete transaction' }))

    await waitFor(() => expect(screen.queryByText(target.description)).not.toBeInTheDocument())
  })

  it('surfaces the closed-account conflict message on create', async () => {
    server.use(transactionClosedAccountConflictHandler)
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    await user.click(screen.getByRole('button', { name: 'Add transaction' }))
    await user.type(await screen.findByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'Category', seedGroceriesCategory.name)
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await selectOption(user, 'Account', openAccount.name)
    await selectOption(user, 'Payment Method', seedDebitCardPaymentMethod.name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Coffee run')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(CLOSED_ACCOUNT_MESSAGE)).toBeInTheDocument()
  })

  it('retries the table and its name lookups together, clearing the error banner', async () => {
    const failOnce = (url: string) =>
      http.get(url, () => new HttpResponse(null, { status: 500 }), { once: true })
    server.use(
      failOnce('/api/transactions'),
      failOnce('/api/categories'),
      failOnce('/api/accounts'),
      failOnce('/api/payment-methods'),
    )
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<TransactionsPage />)
    await screen.findAllByRole('alert')

    await user.click(await screen.findByRole('button', { name: 'Retry' }))

    const transaction = seedGroceriesTransaction
    const row = await findRow(transaction.description)
    // Names, not the raw ids the row falls back to while a lookup list is missing.
    await waitFor(() => {
      expect(
        row.getByText(seedCategories.find((c) => c.id === transaction.categoryId)!.name),
      ).toBeInTheDocument()
    })
    expect(
      row.getByText(seedAccounts.find((a) => a.id === transaction.accountId)!.name),
    ).toBeInTheDocument()
    expect(
      row.getByText(seedPaymentMethods.find((p) => p.id === transaction.paymentMethodId)!.name),
    ).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('holds the rows back until the name lookups arrive, so ids are never shown as names', async () => {
    server.use(
      http.get('/api/categories', async () => {
        await delay(400)
        return HttpResponse.json(seedCategories)
      }),
    )
    renderWithQueryClient(<TransactionsPage />)
    const transaction = seedGroceriesTransaction

    // The transactions themselves arrive right away; the skeleton stays up for the slow lookup.
    expect(await screen.findByText('Loading…')).toBeInTheDocument()
    expect(screen.queryByText(transaction.description)).not.toBeInTheDocument()
    expect(screen.queryByText(transaction.categoryId)).not.toBeInTheDocument()

    expect(await screen.findByText(transaction.description)).toBeInTheDocument()
    expect(screen.queryByText(transaction.categoryId)).not.toBeInTheDocument()
  })
})

describe('TransactionsPage local-time defaults', () => {
  beforeEach(() => {
    // 23:30 on 31 March in UTC-3 is already 1 April in UTC.
    vi.stubEnv('TZ', 'America/Sao_Paulo')
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 2, 31, 23, 30))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllEnvs()
  })

  it('defaults the form date to the local date, not the UTC date', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderWithQueryClient(<TransactionsPage />)

    await user.click(await screen.findByRole('button', { name: 'Add transaction' }))
    expect(await screen.findByLabelText('Date')).toHaveValue('2026-03-31')
  })
})

describe('TransactionsPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  const target = seedGroceriesTransaction

  async function findCard(description: string) {
    const list = await screen.findByRole('list', { name: 'Transactions' })
    await within(list).findByText(description)
    const items = within(list).getAllByRole('listitem')
    return within(items.find((item) => within(item).queryByText(description))!)
  }

  async function fillAndSubmitAddForm(user: ReturnType<typeof userEvent.setup>) {
    await user.type(await screen.findByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'Category', seedGroceriesCategory.name)
    await selectOption(user, 'Account', seedAccounts.find((a) => !a.closed)!.name)
    await selectOption(user, 'Payment Method', seedDebitCardPaymentMethod.name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Coffee run')
    await user.click(screen.getByRole('button', { name: 'Add' }))
  }

  describe('mobile', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.mobile))

    it('renders cards instead of a table, with the signed amount', async () => {
      renderWithQueryClient(<TransactionsPage />)

      const card = await findCard(target.description)
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(card.getByText(`-${target.amount.toFixed(2)}`)).toBeInTheDocument()
      const categoryName = seedCategories.find((c) => c.id === target.categoryId)!.name
      expect(card.getByText(`${target.date} · ${categoryName}`)).toBeInTheDocument()
      expect(card.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
      expect(card.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
    })

    it('collapses the filters behind a button with an active-count badge', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<TransactionsPage />)
      await findCard(target.description)
      expect(screen.queryByRole('combobox', { name: 'Category filter' })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Filters' }))
      const incomeCategory = seedCategories.find((c) => c.type === 'INCOME')!
      await selectOption(user, 'Category filter', incomeCategory.name)
      await user.click(screen.getByRole('button', { name: 'Done' }))

      expect(await screen.findByRole('button', { name: 'Filters, 1 active' })).toBeInTheDocument()
      const income = seedTransactions.find((t) => t.categoryId === incomeCategory.id)!
      expect(await screen.findByText(income.description)).toBeInTheDocument()
      expect(screen.queryByText(target.description)).not.toBeInTheDocument()
    })

    it('Add opens a full-screen dialog that creates a transaction', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<TransactionsPage />)
      await findCard(target.description)

      await user.click(screen.getByRole('button', { name: 'Add transaction' }))

      expect(await screen.findByRole('dialog')).toHaveClass('MuiDialog-paperFullScreen')
      await fillAndSubmitAddForm(user)

      expect(await screen.findByText('Coffee run')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('shows a save error inside the dialog, which stays open', async () => {
      server.use(transactionClosedAccountConflictHandler)
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<TransactionsPage />)
      await findCard(target.description)

      await user.click(screen.getByRole('button', { name: 'Add transaction' }))
      await fillAndSubmitAddForm(user)

      const dialog = screen.getByRole('dialog')
      expect(await within(dialog).findByText(CLOSED_ACCOUNT_MESSAGE)).toBeInTheDocument()
    })

    it('Edit opens the dialog prefilled and saves through the same mutation', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<TransactionsPage />)
      const card = await findCard(target.description)

      await user.click(card.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
      expect(within(dialog).getByText('Edit transaction')).toBeInTheDocument()
      const description = within(dialog).getByRole('textbox', { name: 'Description' })
      expect(description).toHaveValue(target.description)
      await user.clear(description)
      await user.type(description, 'Updated note')
      await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByText('Updated note')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('Cancel closes the dialog without saving', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<TransactionsPage />)
      const card = await findCard(target.description)
      await user.click(card.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(screen.getByText(target.description)).toBeInTheDocument()
    })
  })

  describe('tablet', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.tablet))

    it('keeps the table without the payment method column, with inline filters and the Add dialog', async () => {
      renderWithQueryClient(<TransactionsPage />)

      await screen.findByText(target.description)
      expect(screen.getByRole('table')).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: 'Date' })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: 'Description' })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: 'Amount' })).toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'Payment Method' })).not.toBeInTheDocument()
      expect(screen.getByRole('combobox', { name: 'Category filter' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Filters' })).not.toBeInTheDocument()
      // No inline form panel: the form only exists inside the dialog.
      expect(screen.queryByRole('spinbutton', { name: 'Amount' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Add transaction' })).toBeInTheDocument()
    })
  })

  describe('desktop', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.desktop))

    it('shows every column and inline filters, with the form only inside the Add dialog', async () => {
      renderWithQueryClient(<TransactionsPage />)

      await screen.findByText(target.description)
      for (const name of [
        'Date',
        'Category',
        'Account',
        'Payment Method',
        'Amount',
        'Description',
      ]) {
        expect(screen.getByRole('columnheader', { name })).toBeInTheDocument()
      }
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.queryByRole('spinbutton', { name: 'Amount' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Add transaction' })).toBeInTheDocument()
    })

    it('Add opens a regular (not full-screen) dialog that creates a transaction', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<TransactionsPage />)
      await screen.findByText(target.description)

      await user.click(screen.getByRole('button', { name: 'Add transaction' }))

      expect(await screen.findByRole('dialog')).not.toHaveClass('MuiDialog-paperFullScreen')
      await fillAndSubmitAddForm(user)

      expect(await screen.findByText('Coffee run')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('Edit opens the dialog prefilled', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<TransactionsPage />)
      const row = await findRow(target.description)

      await user.click(row.getByRole('button', { name: 'Edit' }))

      const dialog = await screen.findByRole('dialog')
      expect(dialog).not.toHaveClass('MuiDialog-paperFullScreen')
      expect(within(dialog).getByRole('textbox', { name: 'Description' })).toHaveValue(
        target.description,
      )
      expect(within(dialog).getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    })
  })
})
