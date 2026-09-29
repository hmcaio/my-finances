import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { seedGroceriesCategory } from '../../mocks/handlers/categories'
import { seedCheckingAccount } from '../../mocks/handlers/accounts'
import { seedDebitCardPaymentMethod, seedPaymentMethods } from '../../mocks/handlers/paymentMethods'
import { seedRentPendingOccurrence } from '../../mocks/handlers/recurringTemplates'
import { findRow } from '../../test/testUtils'
import { expectLoadStates } from '../../test/loadStates'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { PendingOccurrencesWidget } from './PendingOccurrencesWidget'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'

describe('PendingOccurrencesWidget', () => {
  // jsdom has no viewport, which MUI treats as the tablet band (Category column hidden): these
  // tests assert the full desktop table.
  beforeEach(() => setViewportWidth(VIEWPORT.desktop))
  afterEach(restoreViewport)

  it('renders the seeded pending occurrence with its template description and amount', async () => {
    renderWithQueryClient(<PendingOccurrencesWidget />)

    const row = await findRow('Rent')
    expect(row.getByText(seedRentPendingOccurrence.amount.toFixed(2))).toBeInTheDocument()
    expect(row.getByText(seedRentPendingOccurrence.dueDate)).toBeInTheDocument()
  })

  it('confirms an occurrence with a payment method and removes it from the list', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<PendingOccurrencesWidget />)
    const row = await findRow('Rent')

    await user.click(row.getByRole('button', { name: 'Confirm occurrence' }))
    await user.click(screen.getByLabelText('Payment Method'))
    await user.click(await screen.findByRole('option', { name: seedDebitCardPaymentMethod.name }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(screen.queryByText('Rent')).not.toBeInTheDocument()
  })

  it('confirms an occurrence with an overridden amount', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<PendingOccurrencesWidget />)
    const row = await findRow('Rent')

    await user.click(row.getByRole('button', { name: 'Confirm occurrence' }))
    const amountInput = screen.getByLabelText('Amount')
    await user.clear(amountInput)
    await user.type(amountInput, '1650')
    await user.click(screen.getByLabelText('Payment Method'))
    await user.click(await screen.findByRole('option', { name: seedDebitCardPaymentMethod.name }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByText('Nothing pending right now.')).toBeInTheDocument()
  })

  it('dismisses an occurrence without creating a transaction', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithQueryClient(<PendingOccurrencesWidget />)
    const row = await findRow('Rent')

    await user.click(row.getByRole('button', { name: 'Dismiss occurrence' }))
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))

    expect(await screen.findByText('Nothing pending right now.')).toBeInTheDocument()
  })

  // Payment methods are a source the widget's own `tableState` composes but nothing else in this
  // component depends on, so delaying/failing that one endpoint alone exercises this widget's load
  // states without touching the categories/accounts/templates it shares with the page that embeds
  // it (`RecurringTemplatesPage`, tested separately).
  expectLoadStates({
    render: () => renderWithQueryClient(<PendingOccurrencesWidget />),
    url: '/api/payment-methods',
    successBody: seedPaymentMethods,
    loadedText: 'Rent',
  })
})

describe('PendingOccurrencesWidget responsive layout (F021)', () => {
  afterEach(restoreViewport)

  async function findCard(description: string) {
    const list = await screen.findByRole('list', { name: 'Upcoming recurring bills' })
    await within(list).findByText(description)
    const items = within(list).getAllByRole('listitem')
    return within(items.find((item) => within(item).queryByText(description))!)
  }

  describe('mobile', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.mobile))

    it('renders cards instead of a table, with due date, category, account and amount', async () => {
      renderWithQueryClient(<PendingOccurrencesWidget />)

      const card = await findCard('Rent')
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(
        card.getByText(seedRentPendingOccurrence.dueDate, { exact: false }),
      ).toBeInTheDocument()
      expect(card.getByText(seedGroceriesCategory.name, { exact: false })).toBeInTheDocument()
      expect(card.getByText(seedCheckingAccount.name)).toBeInTheDocument()
      expect(card.getByText(seedRentPendingOccurrence.amount.toFixed(2))).toBeInTheDocument()
      expect(card.getByRole('button', { name: 'Confirm occurrence' })).toBeInTheDocument()
      expect(card.getByRole('button', { name: 'Dismiss occurrence' })).toBeInTheDocument()
    })

    it('Confirm opens a full-screen dialog prefilled from the occurrence', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<PendingOccurrencesWidget />)
      const card = await findCard('Rent')

      await user.click(card.getByRole('button', { name: 'Confirm occurrence' }))

      const dialog = await screen.findByRole('dialog')
      expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
      expect(within(dialog).getByLabelText('Amount')).toHaveValue(seedRentPendingOccurrence.amount)
      expect(within(dialog).getByLabelText('Date')).toHaveValue(seedRentPendingOccurrence.dueDate)
    })
  })

  describe('tablet', () => {
    beforeEach(() => setViewportWidth(VIEWPORT.tablet))

    it('keeps the table without the Category column, reachable through the row expander', async () => {
      const user = userEvent.setup({ delay: null })
      renderWithQueryClient(<PendingOccurrencesWidget />)

      const row = await findRow('Rent')
      expect(screen.getByRole('columnheader', { name: 'Amount' })).toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'Category' })).not.toBeInTheDocument()

      await user.click(row.getByRole('button', { name: 'Show details' }))
      expect(await screen.findByText(seedGroceriesCategory.name)).toBeInTheDocument()
    })
  })
})
