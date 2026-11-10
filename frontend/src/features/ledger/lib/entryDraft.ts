import type { Category, CategoryType } from '../../../api/categories/categories'
import type { Transaction } from '../../../api/transactions/transactions'
import {
  EMPTY_FUEL_FIELDS,
  emptyTransactionForm,
  formFromTransaction,
  type TransactionFormValues,
} from '../../transactions/transactionForm'
import type { TransferCarry } from '../../transfers/lib/transferFormState'

/**
 * Pure state of the New entry dialog (F040, PRD S6.16): which kind of entry is being typed, the
 * transaction draft Expense and Income share, the seed of the transfer form, and the date and
 * account that travel between them. No React in here, so every carry rule is unit-tested without
 * rendering (`useEntryDraft` binds it to state).
 */

export type EntryType = 'EXPENSE' | 'INCOME' | 'TRANSFER'

/** The two fields every kind of entry has and a switch of type keeps. */
export interface EntryCarry {
  date: string
  accountId: string
}

export interface EntryDraft {
  type: EntryType
  /** One draft for Expense and Income: they differ only by the categories offered. */
  transaction: TransactionFormValues
  /** The date and account last seen in whichever form is on screen. */
  carry: EntryCarry
  /** What the transfer form starts from; `key` changes whenever it must be remounted from it. */
  transfer: { seed: TransferCarry; key: number }
}

/** The `Category.type` an entry type offers, or `null` for a transfer (no category). */
export function categoryTypeOf(type: EntryType): CategoryType | null {
  return type === 'EXPENSE' ? 'EXPENSE' : type === 'INCOME' ? 'INCOME' : null
}

export function initialEntryDraft(
  type: EntryType,
  presetCategoryId?: string,
  editing?: Transaction,
): EntryDraft {
  const transaction = editing
    ? formFromTransaction(editing)
    : emptyTransactionForm(presetCategoryId ?? '')
  return {
    type,
    transaction,
    carry: { date: transaction.date, accountId: transaction.accountId },
    transfer: {
      seed: { date: transaction.date, fromAccountId: transaction.accountId, toAccountId: '' },
      key: 0,
    },
  }
}

/** Drops the category (and the fuel details that only make sense under it) when it is of the other
 * type than `type` offers. A category not found in `categories` (not loaded yet) is left alone. */
function clearMismatchedCategory(
  form: TransactionFormValues,
  type: EntryType,
  categories: Category[] | undefined,
): TransactionFormValues {
  const wanted = categoryTypeOf(type)
  const category = categories?.find((c) => c.id === form.categoryId)
  if (!wanted || !category || category.type === wanted) return form
  return { ...form, categoryId: '', ...EMPTY_FUEL_FIELDS }
}

/**
 * Switches the kind of entry. Date and account always travel: a transaction's account becomes the
 * transfer's From (the transfer form is reseeded and remounted), and the transfer's From becomes
 * the transaction's account. Expense and Income share the rest of the draft, minus a category of
 * the other type.
 */
export function switchEntryType(
  draft: EntryDraft,
  type: EntryType,
  categories: Category[] | undefined,
): EntryDraft {
  if (type === draft.type) return draft
  const { carry } = draft
  if (type === 'TRANSFER') {
    return {
      ...draft,
      type,
      transfer: {
        seed: { date: carry.date, fromAccountId: carry.accountId, toAccountId: '' },
        key: draft.transfer.key + 1,
      },
    }
  }
  const transaction = clearMismatchedCategory(
    { ...draft.transaction, date: carry.date, accountId: carry.accountId },
    type,
    categories,
  )
  return { ...draft, type, transaction }
}

export function patchTransaction(
  draft: EntryDraft,
  patch: Partial<TransactionFormValues>,
): EntryDraft {
  const transaction = { ...draft.transaction, ...patch }
  return {
    ...draft,
    transaction,
    carry: { date: transaction.date, accountId: transaction.accountId },
  }
}

/** The transfer form reports its current date and From account, so a later switch carries them. */
export function reportCarry(draft: EntryDraft, carry: EntryCarry): EntryDraft {
  if (draft.carry.date === carry.date && draft.carry.accountId === carry.accountId) return draft
  return { ...draft, carry }
}

/**
 * After "Add and new" on a transaction: date, account and payment method stay; amount,
 * description, notes and category are cleared. A fuel fill-up keeps its category, vehicle and fuel
 * type (the vehicle fields only make sense under it) and clears liters, price, km and odometer.
 */
export function transactionAdded(
  draft: EntryDraft,
  categories: Category[] | undefined,
): EntryDraft {
  const saved = draft.transaction
  const isFuel = categories?.find((c) => c.id === saved.categoryId)?.fuelCategory ?? false
  const next: TransactionFormValues = {
    ...emptyTransactionForm(),
    date: saved.date,
    accountId: saved.accountId,
    paymentMethodId: saved.paymentMethodId,
    ...(isFuel
      ? { categoryId: saved.categoryId, vehicleId: saved.vehicleId, fuelType: saved.fuelType }
      : {}),
  }
  return { ...draft, transaction: next }
}

/** After "Add and new" on a plain transfer: the form restarts from its date, From and To. */
export function transferAdded(draft: EntryDraft, saved: TransferCarry): EntryDraft {
  return {
    ...draft,
    carry: { date: saved.date, accountId: saved.fromAccountId },
    transfer: { seed: saved, key: draft.transfer.key + 1 },
  }
}
