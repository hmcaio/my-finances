import {
  paymentMethodCreateConflictHandler,
  paymentMethodDeleteConflictHandler,
  seedCashPaymentMethod,
  seedDebitCardPaymentMethod,
  seedPaymentMethods,
} from '../../mocks/handlers/paymentMethods'
import { PAYMENT_METHOD_NAME_MAX_LENGTH } from '../../api/paymentMethods/paymentMethods'
import { PROBLEM_TYPE_MESSAGES } from '../../api/core/apiError'
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
    message: PROBLEM_TYPE_MESSAGES['urn:my-finances:error:payment-method-in-use'],
    handler: paymentMethodDeleteConflictHandler,
  },
  duplicateName: {
    message: PROBLEM_TYPE_MESSAGES['urn:my-finances:error:payment-method-name-already-exists'],
    handler: paymentMethodCreateConflictHandler,
  },
  maxLength: PAYMENT_METHOD_NAME_MAX_LENGTH,
  loadStates: { url: '/api/payment-methods', successBody: seedPaymentMethods },
})
