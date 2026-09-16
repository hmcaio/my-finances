import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { server } from '../../mocks/server'
import { seedAccounts } from '../../mocks/handlers/accounts'
import { seedCategories } from '../../mocks/handlers/categories'
import { seedPaymentMethods } from '../../mocks/handlers/paymentMethods'
import {
  seedTransactions,
  transactionClosedAccountConflictHandler,
} from '../../mocks/handlers/transactions'
import { TransactionsPage } from './TransactionsPage'

async function selectOption(
  user: ReturnType<typeof userEvent.setup>,
  comboboxName: string,
  optionName: string,
) {
  await user.click(screen.getByRole('combobox', { name: comboboxName }))
  await user.click(await screen.findByRole('option', { name: optionName }))
}

describe('TransactionsPage', () => {
  it('renders the seeded transactions', async () => {
    render(<TransactionsPage />)

    for (const transaction of seedTransactions) {
      expect(await screen.findByText(transaction.description)).toBeInTheDocument()
    }
  })

  it('filters by category', async () => {
    const user = userEvent.setup()
    render(<TransactionsPage />)
    await screen.findByText(seedTransactions[0].description)

    const incomeCategory = seedCategories.find((c) => c.type === 'INCOME')!
    await selectOption(user, 'Category filter', incomeCategory.name)

    const incomeTransaction = seedTransactions.find((t) => t.categoryId === incomeCategory.id)!
    const expenseTransaction = seedTransactions.find((t) => t.categoryId !== incomeCategory.id)!
    expect(await screen.findByText(incomeTransaction.description)).toBeInTheDocument()
    expect(screen.queryByText(expenseTransaction.description)).not.toBeInTheDocument()
  })

  it('adds a new transaction with the create form', async () => {
    const user = userEvent.setup()
    render(<TransactionsPage />)
    await screen.findByText(seedTransactions[0].description)

    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'Category', seedCategories[0].name)
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await selectOption(user, 'Account', openAccount.name)
    await selectOption(user, 'Payment Method', seedPaymentMethods[0].name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Coffee run')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Coffee run')).toBeInTheDocument()
  })

  it('excludes closed accounts from the create/edit form account dropdown', async () => {
    const user = userEvent.setup()
    render(<TransactionsPage />)
    await screen.findByText(seedTransactions[0].description)

    const closedAccount = seedAccounts.find((a) => a.closed)!
    await user.click(screen.getByRole('combobox', { name: 'Account' }))

    expect(screen.queryByRole('option', { name: closedAccount.name })).not.toBeInTheDocument()
  })

  it('edits a transaction', async () => {
    const user = userEvent.setup()
    render(<TransactionsPage />)
    const target = seedTransactions[0]
    await screen.findByText(target.description)

    const row = screen.getByText(target.description).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Edit' }))

    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    const descriptionInput = screen.getByRole('textbox', { name: 'Description' })
    await user.clear(descriptionInput)
    await user.type(descriptionInput, 'Updated note')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Updated note')).toBeInTheDocument()
  })

  it('deletes a transaction after confirming the dialog', async () => {
    const user = userEvent.setup()
    render(<TransactionsPage />)
    const target = seedTransactions[0]
    await screen.findByText(target.description)

    const row = screen.getByText(target.description).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Delete' }))
    await screen.findByText('Delete this transaction?')
    await user.click(screen.getByRole('button', { name: 'Delete transaction' }))

    await waitFor(() => expect(screen.queryByText(target.description)).not.toBeInTheDocument())
  })

  it('surfaces the closed-account conflict message on create', async () => {
    server.use(transactionClosedAccountConflictHandler)
    const user = userEvent.setup()
    render(<TransactionsPage />)
    await screen.findByText(seedTransactions[0].description)

    await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '15')
    await selectOption(user, 'Category', seedCategories[0].name)
    const openAccount = seedAccounts.find((a) => !a.closed)!
    await selectOption(user, 'Account', openAccount.name)
    await selectOption(user, 'Payment Method', seedPaymentMethods[0].name)
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Coffee run')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(/cannot accept new transactions/)).toBeInTheDocument()
  })
})
