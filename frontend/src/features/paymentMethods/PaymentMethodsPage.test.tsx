import {
  paymentMethodCreateConflictHandler,
  paymentMethodDeleteConflictHandler,
  seedCashPaymentMethod,
  seedDebitCardPaymentMethod,
  seedPaymentMethods,
} from '../../mocks/handlers/paymentMethods'
import { PAYMENT_METHOD_NAME_MAX_LENGTH } from '../../api/paymentMethods/paymentMethods'
import { resolveErrorMessage } from '../../test/errorMessages'
import { describeSettingsPageOnly } from '../../test/settingsPageContract'
import { PaymentMethodsPage } from './PaymentMethodsPage'

describeSettingsPageOnly('PaymentMethodsPage', {
  page: <PaymentMethodsPage />,
  seedRows: seedPaymentMethods,
  renameTarget: seedDebitCardPaymentMethod,
  deleteTarget: seedCashPaymentMethod,
  newName: 'Credit Card',
  addButtonLabel: 'Add payment method',
  conflict: {
    message: resolveErrorMessage('urn:my-finances:error:payment-method-in-use'),
    handler: paymentMethodDeleteConflictHandler,
  },
  duplicateName: {
    message: resolveErrorMessage('urn:my-finances:error:payment-method-name-already-exists'),
    handler: paymentMethodCreateConflictHandler,
  },
  maxLength: PAYMENT_METHOD_NAME_MAX_LENGTH,
  loadStates: { url: '/api/payment-methods', successBody: seedPaymentMethods },
})
