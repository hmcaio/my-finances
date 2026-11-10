import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../../mocks/server'
import { seedAccounts, seedCheckingAccount } from '../../../mocks/handlers/accounts'
import {
  seedCategories,
  seedFuelCategory,
  seedGroceriesCategory,
  seedSalaryCategory,
} from '../../../mocks/handlers/categories'
import { seedDebitCardPaymentMethod } from '../../../mocks/handlers/paymentMethods'
import {
  seedGroceriesTransaction,
  transactionClosedAccountConflictHandler,
} from '../../../mocks/handlers/transactions'
import {
  seedBitcoinBuyTransfer,
  seedCreditCardPaymentTransfer,
} from '../../../mocks/handlers/transfers'
import { seedCivicVehicle } from '../../../mocks/handlers/vehicles'
import { renderWithQueryClient } from '../../../test/utils/renderWithQueryClient'
import { resolveErrorMessage } from '../../../test/utils/errorMessages'
import { selectOption } from '../../../test/utils/testUtils'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../../test/utils/viewport'
import { EntryDialog } from './EntryDialog'

type Props = Partial<Parameters<typeof EntryDialog>[0]>

const SAVINGS = seedAccounts[2].name

function renderDialog(props: Props = {}) {
  const onClose = vi.fn()
  renderWithQueryClient(<EntryDialog initialType="EXPENSE" onClose={onClose} {...props} />)
  return { user: userEvent.setup({ delay: null }), onClose }
}

/** Records the JSON body of every create, while the default handlers keep answering. */
function recordCreates() {
  const sent: { kind: 'transaction' | 'transfer'; body: Record<string, unknown> }[] = []
  server.use(
    http.post('/api/transactions', async ({ request }) => {
      sent.push({ kind: 'transaction', body: (await request.clone().json()) as never })
    }),
    http.post('/api/transfers', async ({ request }) => {
      sent.push({ kind: 'transfer', body: (await request.clone().json()) as never })
    }),
  )
  return sent
}

type User = ReturnType<typeof userEvent.setup>

async function fillExpense(
  user: User,
  description = 'Coffee run',
  category = seedGroceriesCategory.name,
) {
  await user.type(await screen.findByRole('spinbutton', { name: 'Amount' }), '15')
  await selectOption(user, 'Category', category)
  await selectOption(user, 'Account', seedCheckingAccount.name)
  await selectOption(user, 'Payment Method', seedDebitCardPaymentMethod.name)
  await user.type(screen.getByRole('textbox', { name: 'Description' }), description)
}

const toggle = (name: string) =>
  within(screen.getByRole('group', { name: 'Entry type' })).getByRole('button', { name })

describe('EntryDialog', () => {
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  describe('the type toggle', () => {
    it('opens on the given type with Expense, Income and Transfer to pick from', async () => {
      renderDialog({ initialType: 'TRANSFER' })

      expect(await screen.findByRole('heading', { name: 'New entry' })).toBeInTheDocument()
      expect(toggle('Transfer')).toHaveAttribute('aria-pressed', 'true')
      expect(toggle('Expense')).toHaveAttribute('aria-pressed', 'false')
      expect(toggle('Income')).toHaveAttribute('aria-pressed', 'false')
      expect(await screen.findByRole('combobox', { name: 'From Account' })).toBeInTheDocument()
    })

    it('offers only expense categories on Expense and only income categories on Income', async () => {
      const { user } = renderDialog()
      await screen.findByRole('spinbutton', { name: 'Amount' })

      await user.click(screen.getByRole('combobox', { name: 'Category' }))
      const expenseOptions = (await screen.findAllByRole('option')).map((o) => o.textContent)
      await user.keyboard('{Escape}')
      expect(expenseOptions).toEqual(
        expect.arrayContaining(['Category *', seedGroceriesCategory.name, 'Other Expense']),
      )
      expect(expenseOptions).not.toContain(seedSalaryCategory.name)

      await user.click(toggle('Income'))
      await user.click(screen.getByRole('combobox', { name: 'Category' }))
      const incomeOptions = (await screen.findAllByRole('option')).map((o) => o.textContent)
      expect(incomeOptions).toEqual(
        expect.arrayContaining([seedSalaryCategory.name, 'Other Income']),
      )
      expect(incomeOptions).not.toContain(seedGroceriesCategory.name)
    })

    it('clears a category of the other type when switching from Expense to Income', async () => {
      const { user } = renderDialog()
      await selectOption(user, 'Category', seedGroceriesCategory.name)
      expect(screen.getByRole('combobox', { name: 'Category' })).toHaveTextContent(
        seedGroceriesCategory.name,
      )

      await user.click(toggle('Income'))

      expect(screen.getByRole('combobox', { name: 'Category' })).not.toHaveTextContent(
        seedGroceriesCategory.name,
      )
    })

    it('carries date and account from the transaction to the transfer and back', async () => {
      const { user } = renderDialog()
      await selectOption(user, 'Account', seedCheckingAccount.name)
      fireEvent.change(screen.getByLabelText(/^Date/), { target: { value: '2026-01-14' } })

      await user.click(toggle('Transfer'))
      expect(screen.getByLabelText(/^Date/)).toHaveValue('2026-01-14')
      expect(screen.getByRole('combobox', { name: 'From Account' })).toHaveTextContent(
        seedCheckingAccount.name,
      )
      fireEvent.change(screen.getByLabelText(/^Date/), { target: { value: '2026-01-16' } })
      await selectOption(user, 'From Account', SAVINGS)

      await user.click(toggle('Income'))
      expect(screen.getByLabelText(/^Date/)).toHaveValue('2026-01-16')
      expect(screen.getByRole('combobox', { name: 'Account' })).toHaveTextContent(SAVINGS)
    })
  })

  describe('saving', () => {
    it('saves a transaction, a transfer and an income in one open dialog, three times', async () => {
      const sent = recordCreates()
      const { user, onClose } = renderDialog()

      await fillExpense(user)
      await user.click(screen.getByRole('button', { name: 'Add and new' }))
      await waitFor(() => expect(sent).toHaveLength(1))
      // The dialog stays open, with the date, account and payment method kept and the rest cleared.
      expect(onClose).not.toHaveBeenCalled()
      expect(screen.getByRole('spinbutton', { name: 'Amount' })).toHaveValue(null)
      expect(screen.getByRole('textbox', { name: 'Description' })).toHaveValue('')
      expect(screen.getByRole('combobox', { name: 'Account' })).toHaveTextContent(
        seedCheckingAccount.name,
      )
      expect(screen.getByRole('combobox', { name: 'Payment Method' })).toHaveTextContent(
        seedDebitCardPaymentMethod.name,
      )
      expect(screen.getByRole('combobox', { name: 'Category' })).not.toHaveTextContent(
        seedGroceriesCategory.name,
      )

      await user.click(toggle('Transfer'))
      await selectOption(user, 'To Account', SAVINGS)
      await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '40')
      await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Move to savings')
      await user.click(screen.getByRole('button', { name: 'Add and new' }))
      await waitFor(() => expect(sent).toHaveLength(2))
      expect(onClose).not.toHaveBeenCalled()

      await user.click(toggle('Income'))
      await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '300')
      await selectOption(user, 'Category', seedSalaryCategory.name)
      await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Bonus')
      await user.click(screen.getByRole('button', { name: 'Add' }))

      await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
      expect(sent.map((s) => s.kind)).toEqual(['transaction', 'transfer', 'transaction'])
      expect(sent[0].body).toMatchObject({
        categoryId: seedGroceriesCategory.id,
        accountId: seedCheckingAccount.id,
        description: 'Coffee run',
      })
      expect(sent[1].body).toMatchObject({
        fromAccountId: seedCheckingAccount.id,
        toAccountId: seedAccounts[2].id,
        amount: 40,
        date: sent[0].body.date,
      })
      expect(sent[2].body).toMatchObject({
        categoryId: seedSalaryCategory.id,
        accountId: seedCheckingAccount.id,
        paymentMethodId: seedDebitCardPaymentMethod.id,
        amount: 300,
        date: sent[0].body.date,
      })
    })

    it('Add closes the dialog after one save', async () => {
      const sent = recordCreates()
      const { user, onClose } = renderDialog()
      await fillExpense(user)

      await user.click(screen.getByRole('button', { name: 'Add' }))

      await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
      expect(sent).toHaveLength(1)
    })

    it('Enter in a field submits Add (closing the dialog), not Add and new', async () => {
      const sent = recordCreates()
      const { user, onClose } = renderDialog()
      await fillExpense(user)

      await user.type(screen.getByRole('textbox', { name: 'Description' }), '{Enter}')

      await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
      expect(sent).toHaveLength(1)
    })

    it('marks every required field, hints at it, and keeps Add disabled until all are filled', async () => {
      const { user } = renderDialog()
      await screen.findByRole('spinbutton', { name: 'Amount' })

      expect(screen.getByText('Fields marked * are required')).toBeInTheDocument()
      expect(screen.getByLabelText(/^Date/)).toBeRequired()
      expect(screen.getByRole('spinbutton', { name: 'Amount' })).toBeRequired()
      expect(screen.getByRole('textbox', { name: 'Description' })).toBeRequired()
      for (const name of ['Category', 'Account', 'Payment Method']) {
        expect(screen.getByRole('combobox', { name })).toHaveTextContent(`${name} *`)
      }

      await fillExpense(user)
      await selectOption(user, 'Payment Method', seedDebitCardPaymentMethod.name)
      expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled()
      // Leaving only the payment method empty is what used to disable Add with no visible reason.
      await user.click(toggle('Transfer'))
      await user.click(toggle('Expense'))
      expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled()
    })

    it('does nothing on Enter while the form is not valid', async () => {
      const sent = recordCreates()
      const { user, onClose } = renderDialog()
      await user.type(await screen.findByRole('textbox', { name: 'Description' }), 'x{Enter}')

      expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
      expect(sent).toHaveLength(0)
      expect(onClose).not.toHaveBeenCalled()
    })

    it('shows a failed save inside the open dialog and keeps it open', async () => {
      server.use(transactionClosedAccountConflictHandler)
      const { user, onClose } = renderDialog()
      await fillExpense(user)

      await user.click(screen.getByRole('button', { name: 'Add and new' }))

      expect(
        await within(screen.getByRole('dialog')).findByText(
          resolveErrorMessage('urn:my-finances:error:account-closed'),
        ),
      ).toBeInTheDocument()
      expect(onClose).not.toHaveBeenCalled()
      expect(screen.getByRole('textbox', { name: 'Description' })).toHaveValue('Coffee run')
    })

    it('closes on Cancel and on Esc', async () => {
      const { user, onClose } = renderDialog()
      await screen.findByRole('spinbutton', { name: 'Amount' })

      await user.click(screen.getByRole('button', { name: 'Cancel' }))
      expect(onClose).toHaveBeenCalledTimes(1)
      await user.keyboard('{Escape}')
      expect(onClose).toHaveBeenCalledTimes(2)
    })
  })

  describe('focus', () => {
    it('focuses Amount when it opens on a transaction', async () => {
      renderDialog()

      expect(await screen.findByRole('spinbutton', { name: 'Amount' })).toHaveFocus()
    })

    it('puts focus back in Amount after Add and new on a transaction', async () => {
      const { user } = renderDialog()
      await fillExpense(user)

      await user.click(screen.getByRole('button', { name: 'Add and new' }))

      await waitFor(() =>
        expect(screen.getByRole('textbox', { name: 'Description' })).toHaveValue(''),
      )
      expect(screen.getByRole('spinbutton', { name: 'Amount' })).toHaveFocus()
    })

    it('puts focus in Description (the first cleared field) after Add and new on a transfer', async () => {
      const { user } = renderDialog({ initialType: 'TRANSFER' })
      await screen.findByRole('combobox', { name: 'From Account' })
      await selectOption(user, 'From Account', seedCheckingAccount.name)
      await selectOption(user, 'To Account', SAVINGS)
      await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '5')
      await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Top up')

      await user.click(screen.getByRole('button', { name: 'Add and new' }))

      await waitFor(() =>
        expect(screen.getByRole('spinbutton', { name: 'Amount' })).toHaveValue(null),
      )
      expect(screen.getByRole('textbox', { name: 'Description' })).toHaveFocus()
      // From and To stay.
      expect(screen.getByRole('combobox', { name: 'From Account' })).toHaveTextContent(
        seedCheckingAccount.name,
      )
      expect(screen.getByRole('combobox', { name: 'To Account' })).toHaveTextContent(SAVINGS)
    })
  })

  describe('a fuel fill-up', () => {
    it('keeps category, vehicle and fuel type after Add and new, and clears liters, price, km and odometer', async () => {
      server.use(
        http.get('/api/categories', () => HttpResponse.json([...seedCategories, seedFuelCategory])),
      )
      const sent = recordCreates()
      const { user } = renderDialog({ presetCategoryId: seedFuelCategory.id })
      await screen.findByRole('combobox', { name: 'Vehicle' })
      await selectOption(user, 'Vehicle', seedCivicVehicle.name)
      await selectOption(user, 'Fuel Type', 'Gasolina')
      await user.type(screen.getByRole('spinbutton', { name: 'Amount' }), '200')
      await selectOption(user, 'Account', seedCheckingAccount.name)
      await selectOption(user, 'Payment Method', seedDebitCardPaymentMethod.name)
      await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Fill-up')
      await user.type(screen.getByRole('spinbutton', { name: /^Liters/ }), '40')
      await user.type(screen.getByRole('spinbutton', { name: /^Price per Liter/ }), '5')
      await user.type(screen.getByRole('spinbutton', { name: 'Km Since Last Fill' }), '400')
      await user.type(screen.getByRole('spinbutton', { name: 'Odometer' }), '12000')

      await user.click(screen.getByRole('button', { name: 'Add and new' }))

      await waitFor(() => expect(sent).toHaveLength(1))
      await waitFor(() =>
        expect(screen.getByRole('spinbutton', { name: 'Odometer' })).toHaveValue(null),
      )
      expect(screen.getByRole('combobox', { name: 'Category' })).toHaveTextContent(
        seedFuelCategory.name,
      )
      expect(screen.getByRole('combobox', { name: 'Vehicle' })).toHaveTextContent(
        seedCivicVehicle.name,
      )
      expect(screen.getByRole('combobox', { name: 'Fuel Type' })).toHaveTextContent('Gasolina')
      for (const name of [/^Liters/, /^Price per Liter/, 'Km Since Last Fill', 'Amount']) {
        expect(screen.getByRole('spinbutton', { name })).toHaveValue(null)
      }
      expect(screen.getByRole('textbox', { name: 'Description' })).toHaveValue('')
      expect(screen.getByRole('spinbutton', { name: 'Amount' })).toHaveFocus()
    })
  })

  describe('trade confirmations and edit mode', () => {
    it('offers no Add and new for a trade confirmation', async () => {
      const { user } = renderDialog({ initialType: 'TRANSFER' })

      await user.click(await screen.findByRole('button', { name: 'Trade confirmation' }))

      expect(screen.queryByRole('button', { name: 'Add and new' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Add' })).toBeInTheDocument()
    })

    it('edits a transaction with the type fixed: no toggle, one Save changes button', async () => {
      const { user, onClose } = renderDialog({
        initialType: undefined,
        editingTransaction: seedGroceriesTransaction,
      })

      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByRole('heading', { name: 'Edit transaction' })).toBeInTheDocument()
      expect(screen.queryByRole('group', { name: 'Entry type' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Add and new' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument()
      const description = within(dialog).getByRole('textbox', { name: 'Description' })
      expect(description).toHaveValue(seedGroceriesTransaction.description)
      await user.clear(description)
      await user.type(description, 'Updated note')
      await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

      await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
    })

    it('edits an income with the income categories only', async () => {
      const { user } = renderDialog({
        initialType: undefined,
        editingTransaction: {
          ...seedGroceriesTransaction,
          categoryId: seedSalaryCategory.id,
          type: 'INCOME',
        },
      })

      await user.click(await screen.findByRole('combobox', { name: 'Category' }))

      expect(
        await screen.findByRole('option', { name: seedSalaryCategory.name }),
      ).toBeInTheDocument()
      expect(
        screen.queryByRole('option', { name: seedGroceriesCategory.name }),
      ).not.toBeInTheDocument()
    })

    it('edits a transfer with the type fixed: no toggle, one Save changes button', async () => {
      renderDialog({ initialType: undefined, editingTransfer: seedCreditCardPaymentTransfer })

      const dialog = await screen.findByRole('dialog')
      await within(dialog).findByRole('button', { name: 'Save changes' })
      expect(within(dialog).getByRole('heading', { name: 'Edit transfer' })).toBeInTheDocument()
      expect(screen.queryByRole('group', { name: 'Entry type' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Add and new' })).not.toBeInTheDocument()
      expect(within(dialog).getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    })

    it('still edits a trade confirmation', async () => {
      renderDialog({ initialType: undefined, editingTransfer: seedBitcoinBuyTransfer })

      expect(await screen.findByRole('group', { name: 'Trade lines' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Add and new' })).not.toBeInTheDocument()
    })
  })
})
