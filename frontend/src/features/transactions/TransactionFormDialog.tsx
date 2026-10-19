import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, DialogActions, DialogContent, DialogTitle } from '@mui/material'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useCategories } from '../../api/categories/categoriesQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import type { Transaction } from '../../api/transactions/transactions'
import {
  useCreateTransaction,
  useEditTransaction,
} from '../../api/transactions/transactionsQueries'
import { useVehicles } from '../../api/vehicles/vehiclesQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { TransactionFormFields } from './TransactionFormFields'
import {
  emptyTransactionForm,
  formFromTransaction,
  isTransactionFormValid,
  type TransactionFormValues,
} from './transactionForm'

interface TransactionFormDialogProps {
  /** The full transaction to edit, or `null` to add a new one. */
  editing: Transaction | null
  /** Pre-selects a category on add: the Fuel page's "add fill-up" arrives with the fuel category. */
  presetCategoryId?: string
  /** Called after a successful save and on cancel. */
  onClose: () => void
}

/**
 * The transaction create/edit dialog (F004, fuel details F024), extracted from the old
 * `TransactionsPage` for the Ledger (F037). It is open for as long as it is mounted: the parent
 * mounts it (with a fresh `key`) to open it, so the form always starts from `editing` or an empty
 * form with today's date. Fields are `TransactionFormFields`; a save error shows inside the dialog,
 * since the page's banner sits behind it. The saved row appears through the refetch every
 * successful write triggers.
 */
export function TransactionFormDialog({
  editing,
  presetCategoryId,
  onClose,
}: TransactionFormDialogProps) {
  const { t } = useTranslation(['transactions', 'common'])
  const categories = useCategories().data
  const accounts = useAccounts(true).data
  const paymentMethods = usePaymentMethods().data
  const vehicles = useVehicles().data
  const createMutation = useCreateTransaction()
  const editMutation = useEditTransaction()

  const [form, setForm] = useState<TransactionFormValues>(() =>
    editing ? formFromTransaction(editing) : emptyTransactionForm(presetCategoryId ?? ''),
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // An investment account takes no transactions (money moves through transfers, F008), and a
  // closed one takes no new ones, so the form never offers either.
  const openAccounts = useMemo(
    () => (accounts ?? []).filter((a) => !a.closed && a.type !== 'INVESTMENT'),
    [accounts],
  )

  const isFuelCategorySelected = categories?.find((c) => c.id === form.categoryId)?.fuelCategory

  async function handleSubmit() {
    if (!isTransactionFormValid(form, categories)) return
    setError(null)
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
      onClose()
    } catch (err) {
      setError(defaultErrorMessage(err))
      setSaving(false)
    }
  }

  return (
    <ResponsiveDialog open onClose={saving ? undefined : onClose}>
      <DialogTitle>
        {editing ? t('transactions:editDialogTitle') : t('transactions:addDialogTitle')}
      </DialogTitle>
      <DialogContent>
        <Box sx={{ pt: 1 }}>
          <ErrorAlert message={error} onDismiss={() => setError(null)} />
          <FormGrid>
            <TransactionFormFields
              form={form}
              onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
              categories={categories}
              accounts={openAccounts}
              paymentMethods={paymentMethods}
              vehicles={vehicles}
            />
          </FormGrid>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          disabled={saving || !isTransactionFormValid(form, categories)}
          onClick={() => void handleSubmit()}
        >
          {editing ? t('transactions:saveChanges') : t('common:actions.add')}
        </Button>
      </DialogActions>
    </ResponsiveDialog>
  )
}
