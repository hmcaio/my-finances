import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { seedDebitCardPaymentMethod, seedPaymentMethods } from '../../mocks/handlers/paymentMethods'
import { seedRentPendingOccurrence } from '../../mocks/handlers/recurringTemplates'
import { findRow } from '../../test/testUtils'
import { expectLoadStates } from '../../test/loadStates'
import { PendingOccurrencesWidget } from './PendingOccurrencesWidget'

describe('PendingOccurrencesWidget', () => {
  it('renders the seeded pending occurrence with its template description and amount', async () => {
    render(<PendingOccurrencesWidget />)

    const row = await findRow('Rent')
    expect(row.getByText(seedRentPendingOccurrence.amount.toFixed(2))).toBeInTheDocument()
    expect(row.getByText(seedRentPendingOccurrence.dueDate)).toBeInTheDocument()
  })

  it('confirms an occurrence with a payment method and removes it from the list', async () => {
    const user = userEvent.setup()
    render(<PendingOccurrencesWidget />)
    const row = await findRow('Rent')

    await user.click(row.getByRole('button', { name: 'Confirm occurrence' }))
    await user.click(screen.getByLabelText('Payment Method'))
    await user.click(await screen.findByRole('option', { name: seedDebitCardPaymentMethod.name }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(screen.queryByText('Rent')).not.toBeInTheDocument()
  })

  it('notifies the embedding page after a confirm, but not after a dismiss', async () => {
    const user = userEvent.setup()
    const onConfirmed = vi.fn()
    render(<PendingOccurrencesWidget onConfirmed={onConfirmed} />)
    const row = await findRow('Rent')

    await user.click(row.getByRole('button', { name: 'Dismiss occurrence' }))
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(await screen.findByText('Nothing pending right now.')).toBeInTheDocument()
    expect(onConfirmed).not.toHaveBeenCalled()
  })

  it('calls onConfirmed once an occurrence is confirmed', async () => {
    const user = userEvent.setup()
    const onConfirmed = vi.fn()
    render(<PendingOccurrencesWidget onConfirmed={onConfirmed} />)
    const row = await findRow('Rent')

    await user.click(row.getByRole('button', { name: 'Confirm occurrence' }))
    await user.click(screen.getByLabelText('Payment Method'))
    await user.click(await screen.findByRole('option', { name: seedDebitCardPaymentMethod.name }))
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByText('Nothing pending right now.')).toBeInTheDocument()
    expect(onConfirmed).toHaveBeenCalledTimes(1)
  })

  it('confirms an occurrence with an overridden amount', async () => {
    const user = userEvent.setup()
    render(<PendingOccurrencesWidget />)
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
    const user = userEvent.setup()
    render(<PendingOccurrencesWidget />)
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
    render: () => render(<PendingOccurrencesWidget />),
    url: '/api/payment-methods',
    successBody: seedPaymentMethods,
    loadedText: 'Rent',
  })
})
