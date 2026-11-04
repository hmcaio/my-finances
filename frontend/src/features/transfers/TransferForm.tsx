import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, DialogActions, ToggleButton, ToggleButtonGroup } from '@mui/material'
import type { Account } from '../../api/accounts/accounts'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInvestmentHoldingsByAccount } from '../../api/investments/investmentHoldingsQueries'
import { useInvestmentProducts } from '../../api/investments/investmentProductsQueries'
import type { Transfer } from '../../api/transfers/transfers'
import { useCreateTransfer, useEditTransfer } from '../../api/transfers/transfersQueries'
import { FormGrid } from '../../components/feedback/ResponsiveDialog'
import { useQueryState } from '../../hooks/queryState'
import {
  buildTransferRequest,
  pickerAccounts,
  type FormMode,
  type TransferFormPreset,
} from './transferFormState'
import { isTransferFormValid } from './transferValidation'
import { TradeLinesFields } from './TradeLinesFields'
import { PlainTransferFields } from './PlainTransferFields'
import { TradeAccountFields } from './TradeAccountFields'
import { TransferCommonFields } from './TransferCommonFields'
import { useTransferForm } from './useTransferForm'
import { FormDialogContent } from '../../components/feedback/FormDialogContent'

export type { TransferFormPreset }

interface TransferFormProps {
  /** Every account; closed ones are left out of the pickers. */
  accounts: Account[]
  /** A transfer to edit; omit to create one. */
  editing?: Transfer | null
  /** Starts a create with a buy/sell of one product (ignored when editing). */
  preset?: TransferFormPreset | null
  /** Called with the saved transfer once the create/edit succeeded. */
  onSaved: (transfer: Transfer) => void
  /** Called with a message when the save failed, or `null` when a new attempt starts. */
  onError: (message: string | null) => void
  /** Renders a Cancel button when given. */
  onCancel?: () => void
  /**
   * Lays the form out for a `ResponsiveDialog` (F021): fields in one-column-on-phone `FormGrid`s
   * inside `DialogContent`, buttons in `DialogActions`; the caller supplies the dialog and its
   * title. Off (default), the fields wrap in flex rows with the buttons below (the trade dialog of
   * the investment product page).
   */
  dialog?: boolean
  /** Dialog mode: shown above the fields (the caller's error banner), so a save error is visible. */
  banner?: ReactNode
}

/**
 * The transfer create/edit form (F005 spec), reworked for multi-line trade confirmations by F027
 * (ADR 0024, superseding F009's one-product-per-transfer shape). A toggle switches between the
 * plain `fromAccount`/`toAccount`/`amount` shape and a trade confirmation: pick the cash account
 * and the INVESTMENT account, then add/remove product lines (side, quantity, unit price, optional
 * resulting balance, "close this holding" shown only for SELL), one shared taxes field, and a
 * read-only net-settlement preview the backend will derive `amount`/direction from on submit (n=1
 * is the default/trivial case, not a separate mode).
 */
export function TransferForm({
  accounts,
  editing = null,
  preset = null,
  onSaved,
  onError,
  onCancel,
  dialog = false,
  banner,
}: TransferFormProps) {
  const { t } = useTranslation(['transfers', 'common'])
  const { form, patch, setMode, setInvestmentAccountId, lineActions } = useTransferForm(
    accounts,
    editing,
    preset,
  )
  const [saving, setSaving] = useState(false)

  const { cashOptions, investmentAccounts } = pickerAccounts(accounts)

  const holdingsQuery = useInvestmentHoldingsByAccount(form.investmentAccountId || undefined, {
    enabled: form.mode === 'trade' && form.investmentAccountId !== '',
  })
  useQueryState(holdingsQuery, onError)
  const holdings = form.mode === 'trade' ? holdingsQuery.data : undefined
  const productsQuery = useInvestmentProducts()
  useQueryState(productsQuery, onError)
  const products = productsQuery.data
  const createMutation = useCreateTransfer()
  const editMutation = useEditTransfer()

  const formValid = isTransferFormValid(form)

  async function handleSubmit() {
    if (!formValid) return
    onError(null)
    setSaving(true)
    try {
      const request = buildTransferRequest(form)
      const saved = editing
        ? await editMutation.mutateAsync({ id: editing.id, ...request })
        : await createMutation.mutateAsync(request)
      onSaved(saved)
    } catch (err) {
      onError(defaultErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  // Inline: wrapping flex rows, as before. Dialog: a one-column-on-phone grid, where the long
  // fields carry `gridColumn: '1 / -1'` (`wide`).
  const wide = dialog ? { gridColumn: '1 / -1' } : undefined
  function fieldRow(children: ReactNode, columns = 2) {
    return dialog ? (
      <FormGrid columns={columns}>{children}</FormGrid>
    ) : (
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        {children}
      </Box>
    )
  }

  const body = (
    <>
      <ToggleButtonGroup
        value={form.mode}
        exclusive
        size="small"
        onChange={(_, value: FormMode | null) => value && setMode(value)}
        sx={{ mb: 2 }}
        aria-label={t('transfers:form.transferTypeAria')}
      >
        <ToggleButton value="plain">{t('transfers:form.plainTransferOption')}</ToggleButton>
        <ToggleButton value="trade">{t('transfers:form.tradeConfirmationOption')}</ToggleButton>
      </ToggleButtonGroup>

      {fieldRow(
        <>
          <TransferCommonFields form={form} patch={patch} wide={wide} />
          {form.mode === 'plain' ? (
            <PlainTransferFields form={form} patch={patch} cashOptions={cashOptions} />
          ) : (
            <TradeAccountFields
              form={form}
              patch={patch}
              cashOptions={cashOptions}
              investmentAccounts={investmentAccounts}
              onInvestmentAccountChange={setInvestmentAccountId}
            />
          )}
        </>,
      )}

      {form.mode === 'trade' && (
        <TradeLinesFields
          form={form}
          accounts={accounts}
          holdings={holdings}
          products={products}
          lineActions={lineActions}
        />
      )}
    </>
  )

  const cancelButton =
    editing || onCancel ? (
      <Button onClick={onCancel} disabled={saving}>
        {t('common:actions.cancel')}
      </Button>
    ) : null
  const submitButton = (
    <Button variant="contained" disabled={saving || !formValid} onClick={() => void handleSubmit()}>
      {editing ? t('transfers:form.saveChanges') : t('common:actions.add')}
    </Button>
  )

  if (dialog) {
    return (
      <>
        <FormDialogContent>
          {banner}
          {body}
        </FormDialogContent>
        <DialogActions>
          {cancelButton}
          {submitButton}
        </DialogActions>
      </>
    )
  }

  return (
    <Box>
      {body}

      <Box sx={{ mt: 2, display: 'flex', gap: 2 }}>
        {submitButton}
        {cancelButton}
      </Box>
    </Box>
  )
}
