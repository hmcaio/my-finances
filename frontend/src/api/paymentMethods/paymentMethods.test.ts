import { expect } from 'vitest'
import {
  paymentMethodCreateConflictHandler,
  paymentMethodDeleteConflictHandler,
  paymentMethodRenameConflictHandler,
  seedPaymentMethods,
} from '../../mocks/handlers/paymentMethods'
import { describeNamedEntityApi } from '../../test/apiContract'
import {
  CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  createPaymentMethod,
  deletePaymentMethod,
  getPaymentMethods,
  renamePaymentMethod,
  type CreatePaymentMethodRequest,
  type PaymentMethod,
  type UpdatePaymentMethodRequest,
} from './paymentMethods'

describeNamedEntityApi<PaymentMethod, CreatePaymentMethodRequest, UpdatePaymentMethodRequest>({
  label: 'paymentMethods',
  api: {
    get: getPaymentMethods,
    create: createPaymentMethod,
    rename: renamePaymentMethod,
    remove: deletePaymentMethod,
  },
  seedList: seedPaymentMethods,
  create: {
    request: { name: 'Credit Card' },
    expect: { name: 'Credit Card', id: expect.any(String) },
  },
  rename: {
    id: 'pm-1',
    request: { name: 'Debit Card (Checking)' },
    expect: { id: 'pm-1', name: 'Debit Card (Checking)' },
  },
  remove: { id: 'pm-1' },
  conflict: {
    message: CONFLICT_MESSAGE,
    duplicateNameMessage: DUPLICATE_NAME_MESSAGE,
    createHandler: paymentMethodCreateConflictHandler,
    renameHandler: paymentMethodRenameConflictHandler,
    deleteHandler: paymentMethodDeleteConflictHandler,
    createRequest: { name: 'Debit Card' },
    renameId: 'pm-2',
    renameRequest: { name: 'Debit Card' },
  },
})
