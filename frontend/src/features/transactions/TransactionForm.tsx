import { useMemo, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@mui/material'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useCategories } from '../../api/categories/categoriesQueries'
import type { CategoryType } from '../../api/categories/categories'
import { defaultErrorMessage } from '../../api/core/apiError'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import type { Transaction } from '../../api/transactions/transactions'
import {
  useCreateTransaction,
  useEditTransaction,
} from '../../api/transactions/transactionsQueries'
import { useVehicles } from '../../api/vehicles/vehiclesQueries'
import { FormDialogPane } from '../../components/feedback/FormDialogPane'
import { FormGrid } from '../../components/feedback/ResponsiveDialog'
import { TransactionFormFields } from './TransactionFormFields'
import { isTransactionFormValid, type TransactionFormValues } from './transactionForm'

interface TransactionFormProps {
  /** The draft; the caller owns it so a switch of type in the New entry dialog keeps it (F040). */
  form: TransactionFormValues
  onChange: (patch: Partial<TransactionFormValues>) => void
  /** Offers only the categories of this type. */
  type: CategoryType
  /** The full transaction to edit, or `null` to add a new one. */
  editing: Transaction | null
  /** The caller's error banner, shown above the fields (a save error shows inside the dialog). */
  banner: ReactNode
  /** Called with a message when the save failed, or `null` when a new attempt starts. */
  onError: (message: string | null) => void
  /** Called after "Add" or "Save changes" succeeded. */
  onSaved: () => void
  /**
   * Create only: renders "Add and new", which saves and calls this instead of `onSaved`, then
   * focuses Amount. The caller restarts the draft (`useEntryDraft`); the form stays mounted.
   */
  onAddAnother?: () => void
  onCancel: () => void
}

/**
 * The transaction create/edit form inside the New entry dialog (F004, fuel details F024, moved
 * out of `TransactionFormDialog` by F040): `TransactionFormFields` in a `FormGrid` with Cancel,
 * "Add and new" (create only) and the primary button, in a `FormDialogPane`. The mutation and the
 * validity rule are the same as before; the saved row appears through the refetch every
 * successful write triggers, and the success toast comes from the mutation's `meta`.
 */
export function TransactionForm({
  form,
  onChange,
  type,
  editing,
  banner,
  onError,
  onSaved,
  onAddAnother,
  onCancel,
}: TransactionFormProps) {
  const { t } = useTranslation(['transactions', 'common'])
  const categories = useCategories().data
  const accounts = useAccounts(true).data
  const paymentMethods = usePaymentMethods().data
  const vehicles = useVehicles().data
  const createMutation = useCreateTransaction()
  const editMutation = useEditTransaction()
  const [saving, setSaving] = useState(false)
  const amountInputRef = useRef<HTMLInputElement>(null)

  // An investment account takes no transactions (money moves through transfers, F008), and a
  // closed one takes no new ones, so the form never offers either.
  const openAccounts = useMemo(
    () => (accounts ?? []).filter((a) => !a.closed && a.type !== 'INVESTMENT'),
    [accounts],
  )

  const isFuelCategorySelected = categories?.find((c) => c.id === form.categoryId)?.fuelCategory
  const valid = isTransactionFormValid(form, categories)

  async function handleSubmit(another = false) {
    if (!valid) return
    onError(null)
    setSaving(true)
    const request = {
      date: form.date,
      amount: Number(form.amount),
      categoryId: form.categoryId,
      accountId: form.accountId,
      paymentMethodId: form.paymentMethodId,
      description: form.description.trim(),
      additionalNotes: form.additionalNotes.trim() || undefined,
      ...(isFuelCategorySelected
        ? {
            vehicleId: form.vehicleId,
            fuelType: form.fuelType as
              'ETANOL' | 'ETANOL_ADITIVADO' | 'GASOLINA' | 'GASOLINA_ADITIVADA',
            liters: Number(form.liters),
            pricePerLiter: Number(form.pricePerLiter),
            kmSinceLastFill: form.kmSinceLastFill ? Number(form.kmSinceLastFill) : undefined,
            odometer: form.odometer ? Number(form.odometer) : undefined,
          }
        : {}),
    }
    try {
      if (editing) {
        await editMutation.mutateAsync({ id: editing.id, ...request })
      } else {
        await createMutation.mutateAsync(request)
      }
    } catch (err) {
      onError(defaultErrorMessage(err))
      setSaving(false)
      return
    }
    if (another) {
      setSaving(false)
      onAddAnother?.()
      // An explicit focus call, not `autoFocus`: the form stays mounted, and the field keeps
      // its place under the Vite dev server too.
      amountInputRef.current?.focus()
    } else {
      onSaved()
    }
  }

  return (
    <FormDialogPane
      onSubmit={() => void handleSubmit()}
      canSubmit={valid}
      busy={saving}
      submitLabel={editing ? t('transactions:saveChanges') : t('common:actions.add')}
      onCancel={onCancel}
      extraActions={
        onAddAnother &&
        !editing && (
          <Button disabled={saving || !valid} onClick={() => void handleSubmit(true)}>
            {t('common:actions.addAndNew')}
          </Button>
        )
      }
    >
      {banner}
      <FormGrid>
        <TransactionFormFields
          form={form}
          onChange={onChange}
          categories={categories}
          accounts={openAccounts}
          paymentMethods={paymentMethods}
          vehicles={vehicles}
          type={type}
          amountInputRef={amountInputRef}
        />
      </FormGrid>
    </FormDialogPane>
  )
}
