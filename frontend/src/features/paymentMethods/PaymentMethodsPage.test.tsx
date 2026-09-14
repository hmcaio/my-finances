import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import { seedPaymentMethods } from '../../mocks/handlers/paymentMethods'
import { PaymentMethodsPage } from './PaymentMethodsPage'

function findRow(name: string) {
  const cell = screen.getByText(name)
  return within(cell.closest('tr') as HTMLElement)
}

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
    await screen.findByText(seedPaymentMethods[0].name)

    await user.type(screen.getByLabelText('Name'), 'Credit Card')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Credit Card')).toBeInTheDocument()
  })

  it('renames a payment method inline', async () => {
    const user = userEvent.setup()
    render(<PaymentMethodsPage />)
    await screen.findByText(seedPaymentMethods[0].name)

    const row = findRow(seedPaymentMethods[0].name)
    await user.click(row.getByRole('button', { name: 'Rename' }))
    const input = row.getByRole('textbox')
    await user.clear(input)
    await user.type(input, 'Debit Card (Checking)')
    await user.click(row.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Debit Card (Checking)')).toBeInTheDocument()
    expect(screen.queryByText(seedPaymentMethods[0].name)).not.toBeInTheDocument()
  })

  it('deletes a payment method', async () => {
    const user = userEvent.setup()
    render(<PaymentMethodsPage />)
    const name = seedPaymentMethods[1].name
    await screen.findByText(name)

    const row = findRow(name)
    await user.click(row.getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByText(name)).not.toBeInTheDocument())
  })

  it('surfaces the 409 conflict message when delete fails', async () => {
    server.use(
      http.delete('/api/payment-methods/:id', () =>
        HttpResponse.json({ message: 'Payment method is in use' }, { status: 409 }),
      ),
    )
    const user = userEvent.setup()
    render(<PaymentMethodsPage />)
    const name = seedPaymentMethods[0].name
    await screen.findByText(name)

    const row = findRow(name)
    await user.click(row.getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(/reassign them/)).toBeInTheDocument()
    // The row is still there - a 409 must not optimistically remove it.
    expect(screen.getByText(name)).toBeInTheDocument()
  })
})
