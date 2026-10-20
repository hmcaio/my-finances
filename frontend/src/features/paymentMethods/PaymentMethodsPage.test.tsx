import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { seedPaymentMethods } from '../../mocks/handlers/paymentMethods'
import { expectLoadStates } from '../../test/loadStates'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { PaymentMethodsPage } from './PaymentMethodsPage'

/**
 * F034/ADR 0028: PaymentMethodsPage lost every mutation affordance - every row is now fixed,
 * migration-seeded content. This replaces the old `describeSettingsPage` CRUD contract with a
 * plain read-only check plus the shared load-state cases.
 */
describe('PaymentMethodsPage', () => {
  it('renders the seeded payment methods with no add/rename/delete affordance', async () => {
    renderWithQueryClient(<PaymentMethodsPage />)

    for (const paymentMethod of seedPaymentMethods) {
      expect(await screen.findByText(paymentMethod.name)).toBeInTheDocument()
    }
    expect(screen.queryByRole('button', { name: /add/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Rename' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  expectLoadStates({
    render: () => renderWithQueryClient(<PaymentMethodsPage />),
    url: '/api/payment-methods',
    successBody: seedPaymentMethods,
    loadedText: seedPaymentMethods[0].name,
  })
})
