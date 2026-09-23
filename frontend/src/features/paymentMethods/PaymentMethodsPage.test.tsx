import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { server } from '../../mocks/server'
import {
  paymentMethodDeleteConflictHandler,
  seedCashPaymentMethod,
  seedDebitCardPaymentMethod,
  seedPaymentMethods,
} from '../../mocks/handlers/paymentMethods'
import { CONFLICT_MESSAGE } from '../../api/paymentMethods'
import { findRow } from '../../test/testUtils'
import { PaymentMethodsPage } from './PaymentMethodsPage'

describe('PaymentMethodsPage', () => {
  it('renders the seeded payment methods', async () => {
    render(<PaymentMethodsPage />)

    for (const paymentMethod of seedPaymentMethods) {
      expect(await screen.findByText(paymentMethod.name)).toBeInTheDocument()
    }
  })

  it('adds a new payment method', async () => {
    const user = userEvent.setup()
    render(<PaymentMethodsPage />)
    await screen.findByText(seedDebitCardPaymentMethod.name)

    await user.type(screen.getByLabelText('Name'), 'Credit Card')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Credit Card')).toBeInTheDocument()
  })

  it('renames a payment method inline', async () => {
    const user = userEvent.setup()
    render(<PaymentMethodsPage />)
    await screen.findByText(seedDebitCardPaymentMethod.name)

    const row = await findRow(seedDebitCardPaymentMethod.name)
    await user.click(row.getByRole('button', { name: 'Rename' }))
    const input = row.getByRole('textbox')
    await user.clear(input)
    await user.type(input, 'Debit Card (Checking)')
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Debit Card (Checking)')).toBeInTheDocument()
    expect(screen.queryByText(seedDebitCardPaymentMethod.name)).not.toBeInTheDocument()
  })

  it('deletes a payment method', async () => {
    const user = userEvent.setup()
    render(<PaymentMethodsPage />)
    const name = seedCashPaymentMethod.name
    await screen.findByText(name)

    const row = await findRow(name)
    await user.click(row.getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByText(name)).not.toBeInTheDocument())
  })

  it('surfaces the 409 conflict message when delete fails', async () => {
    server.use(paymentMethodDeleteConflictHandler)
    const user = userEvent.setup()
    render(<PaymentMethodsPage />)
    const name = seedDebitCardPaymentMethod.name
    await screen.findByText(name)

    const row = await findRow(name)
    await user.click(row.getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(CONFLICT_MESSAGE)).toBeInTheDocument()
    // The row is still there - a 409 must not optimistically remove it.
    expect(screen.getByText(name)).toBeInTheDocument()
  })
})
