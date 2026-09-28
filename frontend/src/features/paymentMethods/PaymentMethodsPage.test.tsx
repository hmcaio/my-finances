import { afterEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  paymentMethodCreateConflictHandler,
  paymentMethodDeleteConflictHandler,
  seedCashPaymentMethod,
  seedDebitCardPaymentMethod,
  seedPaymentMethods,
} from '../../mocks/handlers/paymentMethods'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  PAYMENT_METHOD_NAME_MAX_LENGTH,
} from '../../api/paymentMethods/paymentMethods'
import { describeSettingsPageOnly } from '../../test/settingsPageContract'
import { restoreViewport, setViewportWidth, VIEWPORT } from '../../test/viewport'
import { renderWithQueryClient } from '../../test/renderWithQueryClient'
import { PaymentMethodsPage } from './PaymentMethodsPage'

describeSettingsPageOnly('PaymentMethodsPage', {
  page: <PaymentMethodsPage />,
  seedRows: seedPaymentMethods,
  renameTarget: seedDebitCardPaymentMethod,
  deleteTarget: seedCashPaymentMethod,
  newName: 'Credit Card',
  addButtonLabel: 'Add payment method',
  conflict: { message: CONFLICT_MESSAGE, handler: paymentMethodDeleteConflictHandler },
  duplicateName: { message: DUPLICATE_NAME_MESSAGE, handler: paymentMethodCreateConflictHandler },
  maxLength: PAYMENT_METHOD_NAME_MAX_LENGTH,
  loadStates: { url: '/api/payment-methods', successBody: seedPaymentMethods },
})

describe('PaymentMethodsPage responsive layout (F021)', () => {
  afterEach(restoreViewport)

  it('opens the add dialog full-screen on mobile', async () => {
    setViewportWidth(VIEWPORT.mobile)
    const user = userEvent.setup()
    renderWithQueryClient(<PaymentMethodsPage />)
    await screen.findByText(seedDebitCardPaymentMethod.name)

    await user.click(screen.getByRole('button', { name: 'Add payment method' }))
    const dialog = await screen.findByRole('dialog')

    expect(dialog).toHaveClass('MuiDialog-paperFullScreen')
  })

  it('opens the add dialog as a regular (not full-screen) dialog on tablet', async () => {
    setViewportWidth(VIEWPORT.tablet)
    const user = userEvent.setup()
    renderWithQueryClient(<PaymentMethodsPage />)
    await screen.findByText(seedDebitCardPaymentMethod.name)

    await user.click(screen.getByRole('button', { name: 'Add payment method' }))
    const dialog = await screen.findByRole('dialog')

    expect(dialog).not.toHaveClass('MuiDialog-paperFullScreen')
  })
})
