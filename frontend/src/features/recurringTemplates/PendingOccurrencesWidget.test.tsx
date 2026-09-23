import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { seedDebitCardPaymentMethod } from '../../mocks/handlers/paymentMethods'
import { seedRentPendingOccurrence } from '../../mocks/handlers/recurringTemplates'
import { findRow } from '../../test/testUtils'
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
})
