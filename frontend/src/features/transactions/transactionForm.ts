import { today } from '../../utils/localDate'

export interface TransactionFormValues {
  date: string
  amount: string
  categoryId: string
  accountId: string
  paymentMethodId: string
  description: string
  additionalNotes: string
}

/** A fresh form, built per use so its date is today's local date rather than the date the page
 * module was first loaded. */
export function emptyTransactionForm(): TransactionFormValues {
  return {
    date: today(),
    amount: '',
    categoryId: '',
    accountId: '',
    paymentMethodId: '',
    description: '',
    additionalNotes: '',
  }
}

export function isTransactionFormValid(form: TransactionFormValues): boolean {
  return (
    form.date !== '' &&
    Number(form.amount) > 0 &&
    form.categoryId !== '' &&
    form.accountId !== '' &&
    form.paymentMethodId !== '' &&
    form.description.trim() !== ''
  )
}
