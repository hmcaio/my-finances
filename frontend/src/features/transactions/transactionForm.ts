import type { Category } from '../../api/categories/categories'
import { today } from '../../utils/localDate'

export interface TransactionFormValues {
  date: string
  amount: string
  categoryId: string
  accountId: string
  paymentMethodId: string
  description: string
  additionalNotes: string
  // F024 (ADR 0021): shown/required only while `categoryId` is the fuel category; cleared
  // whenever the user picks a different category (`TransactionFormFields`'s category select).
  vehicleId: string
  fuelType: string
  liters: string
  pricePerLiter: string
  kmSinceLastFill: string
  odometer: string
}

/** A fresh form, built per use so its date is today's local date rather than the date the page
 * module was first loaded. `presetCategoryId` pre-selects a category - the Fuel page's "add fuel
 * transaction" action uses it to arrive with the fuel category already chosen (F024 spec). */
export function emptyTransactionForm(presetCategoryId = ''): TransactionFormValues {
  return {
    date: today(),
    amount: '',
    categoryId: presetCategoryId,
    accountId: '',
    paymentMethodId: '',
    description: '',
    additionalNotes: '',
    vehicleId: '',
    fuelType: '',
    liters: '',
    pricePerLiter: '',
    kmSinceLastFill: '',
    odometer: '',
  }
}

/**
 * `categories` resolves whether `form.categoryId` is the fuel category - when it is, `vehicleId`/
 * `fuelType`/`liters`/`pricePerLiter` become mandatory too (F024's invariant, checked client-side
 * before ever submitting a request the backend would reject with 400).
 */
export function isTransactionFormValid(
  form: TransactionFormValues,
  categories: Category[] | undefined,
): boolean {
  const baseValid =
    form.date !== '' &&
    Number(form.amount) > 0 &&
    form.categoryId !== '' &&
    form.accountId !== '' &&
    form.paymentMethodId !== '' &&
    form.description.trim() !== ''
  if (!baseValid) return false

  const isFuelCategory = categories?.find((c) => c.id === form.categoryId)?.fuelCategory ?? false
  if (!isFuelCategory) return true
  return (
    form.vehicleId !== '' &&
    form.fuelType !== '' &&
    Number(form.liters) > 0 &&
    Number(form.pricePerLiter) > 0
  )
}
