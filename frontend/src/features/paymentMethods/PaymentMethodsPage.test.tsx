import {
  paymentMethodCreateConflictHandler,
  paymentMethodDeleteConflictHandler,
  seedCashPaymentMethod,
  seedDebitCardPaymentMethod,
  seedPaymentMethods,
} from '../../mocks/handlers/paymentMethods'
import { CONFLICT_MESSAGE, DUPLICATE_NAME_MESSAGE } from '../../api/paymentMethods'
import { describeSettingsPageOnly } from '../../test/settingsPageContract'
import { PaymentMethodsPage } from './PaymentMethodsPage'

describeSettingsPageOnly('PaymentMethodsPage', {
  page: <PaymentMethodsPage />,
  seedRows: seedPaymentMethods,
  renameTarget: seedDebitCardPaymentMethod,
  deleteTarget: seedCashPaymentMethod,
  newName: 'Credit Card',
  conflict: { message: CONFLICT_MESSAGE, handler: paymentMethodDeleteConflictHandler },
  duplicateName: { message: DUPLICATE_NAME_MESSAGE, handler: paymentMethodCreateConflictHandler },
  // No maxLength: PaymentMethodsPage doesn't cap its Name input today (see this PR's report - a
  // pre-existing gap against `frontend/CLAUDE.md`'s bounded-free-text convention, out of scope
  // for this test-only PR).
  loadStates: { url: '/api/payment-methods', successBody: seedPaymentMethods },
})
