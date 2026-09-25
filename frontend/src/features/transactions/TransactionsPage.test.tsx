import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
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
import { CLOSED_ACCOUNT_MESSAGE } from '../../api/transactions'
import { findRow, selectOption } from '../../test/testUtils'
import { TransactionsPage } from './TransactionsPage'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('TransactionsPage', () => {
  it('renders the seeded transactions', async () => {
    renderWithQueryClient(<TransactionsPage />)

    for (const transaction of seedTransactions) {
      expect(await screen.findByText(transaction.description)).toBeInTheDocument()
    }
  })

  it('filters by category', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    const incomeCategory = seedCategories.find((c) => c.type === 'INCOME')!
    await selectOption(user, 'Category filter', incomeCategory.name)

    const incomeTransaction = seedTransactions.find((t) => t.categoryId === incomeCategory.id)!
    const expenseTransaction = seedTransactions.find((t) => t.categoryId !== incomeCategory.id)!
    expect(await screen.findByText(incomeTransaction.description)).toBeInTheDocument()
    expect(screen.queryByText(expenseTransaction.description)).not.toBeInTheDocument()
  })

  it('adds a new transaction with the create form', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'Category', seedGroceriesCategory.name)
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await selectOption(user, 'Account', openAccount.name)
    await selectOption(user, 'Payment Method', seedDebitCardPaymentMethod.name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Coffee run')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Coffee run')).toBeInTheDocument()
  })

  it('excludes closed accounts from the create/edit form account dropdown', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    const closedAccount = seedAccounts.find((a) => a.closed)!
    await user.click(screen.getByRole('combobox', { name: 'Account' }))

    expect(screen.queryByRole('option', { name: closedAccount.name })).not.toBeInTheDocument()
  })

  it('never offers an investment account in the form account dropdown', async () => {
    server.use(accountsWithInvestmentHandler)
    const user = userEvent.setup()
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    await user.click(screen.getByRole('combobox', { name: 'Account' }))

    const openAccount = seedAccounts.find((a) => !a.closed)!
    expect(await screen.findByRole('option', { name: openAccount.name })).toBeInTheDocument()
    expect(
      screen.queryByRole('option', { name: seedInvestmentAccount.name }),
    ).not.toBeInTheDocument()
  })

  it('edits a transaction', async () => {
    const user = userEvent.setup()
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
    const user = userEvent.setup()
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
    const user = userEvent.setup()
    renderWithQueryClient(<TransactionsPage />)
    await screen.findByText(seedGroceriesTransaction.description)

    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '15')
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
    const user = userEvent.setup()
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
    renderWithQueryClient(<TransactionsPage />)

    expect(await screen.findByLabelText('Date')).toHaveValue('2026-03-31')
  })
})
