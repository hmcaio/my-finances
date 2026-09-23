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
} from '../../api/paymentMethods'
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
  maxLength: PAYMENT_METHOD_NAME_MAX_LENGTH,
  loadStates: { url: '/api/payment-methods', successBody: seedPaymentMethods },
})
