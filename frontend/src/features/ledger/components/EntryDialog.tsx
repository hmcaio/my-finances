import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, DialogTitle, ToggleButton, ToggleButtonGroup } from '@mui/material'
import { useAccounts } from '../../../api/accounts/accountsQueries'
import { useCategories } from '../../../api/categories/categoriesQueries'
import type { Transaction } from '../../../api/transactions/transactions'
import type { Transfer } from '../../../api/transfers/transfers'
import { ErrorAlert } from '../../../components/feedback/ErrorAlert'
import { ResponsiveDialog } from '../../../components/feedback/ResponsiveDialog'
import { TransactionForm } from '../../transactions/TransactionForm'
import { TransferForm } from '../../transfers/components/TransferForm'
import { useEntryDraft } from '../hooks/useEntryDraft'
import { categoryTypeOf, type EntryType } from '../lib/entryDraft'

interface EntryDialogProps {
  /** The kind of entry a create opens on (default Expense). Ignored when editing. */
  initialType?: EntryType
  /** Pre-selects a category on Expense: the Fuel page's "add fill-up". */
  presetCategoryId?: string
  /** Edit mode: the full transaction to edit. The type is fixed and the toggle is hidden. */
  editingTransaction?: Transaction
  /** Edit mode: the full transfer to edit (a trade confirmation included). */
  editingTransfer?: Transfer
  /** Called on Cancel, Esc and after "Add" or "Save changes"; not after "Add and new". */
  onClose: () => void
}

const TYPES: {
  value: EntryType
  labelKey: 'ledger:type.expense' | 'ledger:type.income' | 'ledger:type.transfer'
}[] = [
  { value: 'EXPENSE', labelKey: 'ledger:type.expense' },
  { value: 'INCOME', labelKey: 'ledger:type.income' },
  { value: 'TRANSFER', labelKey: 'ledger:type.transfer' },
]

/**
 * The New entry dialog of the Ledger (F040, PRD S6.16): one dialog for an expense, an income or
 * a transfer, with a toggle to switch between them without closing, so a sitting of mixed entries
 * is one dialog. Switching keeps the date and the account (`useEntryDraft`) and the category list
 * follows the type. "Add and new" saves and keeps the dialog open for the next entry; "Add"
 * closes it. Edit mode (`editingTransaction` / `editingTransfer`) fixes the type and hides the
 * toggle. It is open for as long as it is mounted: the page mounts it, with a fresh `key`, to
 * open it. A save error shows inside the dialog, since the page's banner sits behind it.
 */
export function EntryDialog({
  initialType = 'EXPENSE',
  presetCategoryId,
  editingTransaction,
  editingTransfer,
  onClose,
}: EntryDialogProps) {
  const { t } = useTranslation(['ledger', 'transactions', 'transfers', 'common'])
  const categories = useCategories().data
  const accounts = useAccounts(true).data
  const [error, setError] = useState<string | null>(null)

  const lockedType: EntryType | undefined = editingTransfer
    ? 'TRANSFER'
    : editingTransaction
      ? editingTransaction.type
      : undefined
  const draft = useEntryDraft({
    initialType,
    presetCategoryId,
    editingTransaction,
    lockedType,
    categories,
  })
  const editing = lockedType !== undefined
  const categoryType = categoryTypeOf(draft.type)

  const title = editingTransfer
    ? t('transfers:editDialogTitle')
    : editingTransaction
      ? t('transactions:editDialogTitle')
      : t('ledger:entry.title')
  const banner = <ErrorAlert message={error} onDismiss={() => setError(null)} />

  function switchType(type: EntryType) {
    setError(null)
    draft.switchType(type)
  }

  return (
    <ResponsiveDialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{title}</DialogTitle>
      {!editing && (
        <Box sx={{ px: 3, pb: 1 }}>
          <ToggleButtonGroup
            value={draft.type}
            exclusive
            size="small"
            onChange={(_, value: EntryType | null) => value && switchType(value)}
            aria-label={t('ledger:entry.typeAria')}
            sx={{
              width: { xs: '100%', sm: 'auto' },
              '& .MuiToggleButton-root': { flex: { xs: 1, sm: 'none' } },
            }}
          >
            {TYPES.map(({ value, labelKey }) => (
              <ToggleButton key={value} value={value}>
                {t(labelKey)}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Box>
      )}

      {categoryType !== null ? (
        <TransactionForm
          form={draft.transaction}
          onChange={draft.patchTransaction}
          type={categoryType}
          editing={editingTransaction ?? null}
          banner={banner}
          onError={setError}
          onSaved={onClose}
          onAddAnother={editing ? undefined : draft.transactionAdded}
          onCancel={onClose}
        />
      ) : (
        accounts && (
          <TransferForm
            // Remounted whenever the draft reseeds it (a switch to Transfer, "Add and new").
            key={editingTransfer?.id ?? `new-${draft.transferKey}`}
            dialog
            accounts={accounts}
            editing={editingTransfer ?? null}
            carry={draft.transferSeed}
            banner={banner}
            onError={setError}
            onSaved={onClose}
            onAddAnother={
              editing
                ? undefined
                : (saved) =>
                    draft.transferAdded({
                      date: saved.date,
                      fromAccountId: saved.fromAccountId,
                      toAccountId: saved.toAccountId,
                    })
            }
            onCarryChange={editing ? undefined : draft.reportCarry}
            onCancel={onClose}
            autoFocusDescription
            // After "Add and new" or a switch the form remounts inside an open dialog: focus the
            // first cleared field with an explicit call (`autoFocus` is flaky under the dev server).
            focusDescriptionOnMount={draft.transferKey > 0}
          />
        )
      )}
    </ResponsiveDialog>
  )
}
