import { useCallback, useState } from 'react'
import type { Category } from '../../../api/categories/categories'
import type { Transaction } from '../../../api/transactions/transactions'
import type { TransactionFormValues } from '../../transactions/transactionForm'
import type { TransferCarry } from '../../transfers/lib/transferFormState'
import {
  initialEntryDraft,
  patchTransaction,
  reportCarry,
  switchEntryType,
  transactionAdded,
  transferAdded,
  type EntryCarry,
  type EntryType,
} from '../lib/entryDraft'

interface UseEntryDraftOptions {
  /** The kind of entry a create opens on. */
  initialType: EntryType
  /** Pre-selects a category on Expense: the Fuel page's "add fill-up". */
  presetCategoryId?: string
  /** The transaction being edited, to start the draft from. */
  editingTransaction?: Transaction
  /** Edit mode: the type is fixed and `switchType` does nothing. */
  lockedType?: EntryType
  /** Resolves a category's type (for the mismatch rule) and whether it is the fuel category. */
  categories: Category[] | undefined
}

/**
 * The state of the New entry dialog (F040): the type toggle, the transaction draft, the transfer
 * form's seed and the date and account that travel when the type changes. The rules are the pure
 * functions of `lib/entryDraft`; this binds them to state. Edit mode passes `lockedType`.
 */
export function useEntryDraft({
  initialType,
  presetCategoryId,
  editingTransaction,
  lockedType,
  categories,
}: UseEntryDraftOptions) {
  const [draft, setDraft] = useState(() =>
    initialEntryDraft(lockedType ?? initialType, presetCategoryId, editingTransaction),
  )

  const switchType = useCallback(
    (type: EntryType) => {
      if (lockedType) return
      setDraft((prev) => switchEntryType(prev, type, categories))
    },
    [lockedType, categories],
  )
  const patch = useCallback((changes: Partial<TransactionFormValues>) => {
    setDraft((prev) => patchTransaction(prev, changes))
  }, [])
  const report = useCallback((carry: EntryCarry) => {
    setDraft((prev) => reportCarry(prev, carry))
  }, [])
  const afterTransactionAdded = useCallback(() => {
    setDraft((prev) => transactionAdded(prev, categories))
  }, [categories])
  const afterTransferAdded = useCallback((saved: TransferCarry) => {
    setDraft((prev) => transferAdded(prev, saved))
  }, [])

  return {
    type: lockedType ?? draft.type,
    transaction: draft.transaction,
    transferSeed: draft.transfer.seed,
    transferKey: draft.transfer.key,
    switchType,
    patchTransaction: patch,
    reportCarry: report,
    transactionAdded: afterTransactionAdded,
    transferAdded: afterTransferAdded,
  }
}
