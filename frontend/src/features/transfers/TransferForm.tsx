import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  Checkbox,
  DialogActions,
  DialogContent,
  FormControlLabel,
  IconButton,
  MenuItem,
  Select,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import type { Account } from '../../api/accounts/accounts'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInvestmentHoldingsByAccount } from '../../api/investments/investmentHoldingsQueries'
import { useInvestmentProducts } from '../../api/investments/investmentProductsQueries'
import type { Transfer } from '../../api/transfers/transfers'
import { useCreateTransfer, useEditTransfer } from '../../api/transfers/transfersQueries'
import { FormGrid } from '../../components/feedback/ResponsiveDialog'
import { useQueryState } from '../../hooks/queryState'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { netSettlementDirection, type TradeSide } from './tradeMath'
import {
  buildTransferRequest,
  pickerAccounts,
  type FormMode,
  type LineState,
  type TransferFormPreset,
} from './transferFormState'
import {
  duplicateResultingBalanceProductIds,
  isTransferFormValid,
  tradeNetPreview,
} from './transferValidation'
import { useTransferForm } from './useTransferForm'

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
  const { locale } = useLocale()
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
  const productName = (id: string) => products?.find((p) => p.id === id)?.name ?? ''
  const createMutation = useCreateTransfer()
  const editMutation = useEditTransfer()

  function holdingOptionsFor(line: LineState) {
    return (holdings ?? []).filter((h) => !h.closed || h.productId === line.productId)
  }

  const netPreview = tradeNetPreview(form)
  const direction = netSettlementDirection(netPreview)
  const cashAccountName =
    accounts.find((a) => a.id === form.cashAccountId)?.name ??
    t('transfers:form.cashAccountFallback')
  const investmentAccountName =
    accounts.find((a) => a.id === form.investmentAccountId)?.name ??
    t('transfers:form.investmentAccountFallback')

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

  const commonFields = (
    <>
      <TextField
        label={t('transfers:form.dateLabel')}
        type="date"
        size="small"
        value={form.date}
        onChange={(e) => patch({ date: e.target.value })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        label={t('transfers:form.descriptionLabel')}
        size="small"
        required
        value={form.description}
        onChange={(e) => patch({ description: e.target.value })}
        slotProps={{ htmlInput: { maxLength: 150 } }}
        sx={wide}
      />
      <TextField
        label={t('transfers:form.additionalNotesLabel')}
        size="small"
        value={form.additionalNotes}
        onChange={(e) => patch({ additionalNotes: e.target.value })}
        slotProps={{ htmlInput: { maxLength: 500 } }}
        sx={wide}
      />
    </>
  )

  const plainFields = (
    <>
      <Select
        size="small"
        displayEmpty
        value={form.fromAccountId}
        onChange={(e) => patch({ fromAccountId: e.target.value })}
        aria-label={t('transfers:form.fromAccountLabel')}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          {t('transfers:form.fromAccountLabel')}
        </MenuItem>
        {cashOptions.map((a) => (
          <MenuItem key={a.id} value={a.id}>
            {a.name}
          </MenuItem>
        ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={form.toAccountId}
        onChange={(e) => patch({ toAccountId: e.target.value })}
        aria-label={t('transfers:form.toAccountLabel')}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          {t('transfers:form.toAccountLabel')}
        </MenuItem>
        {cashOptions
          .filter((a) => a.id !== form.fromAccountId)
          .map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
      </Select>
      <TextField
        label={t('transfers:form.amountLabel')}
        type="number"
        size="small"
        value={form.amount}
        onChange={(e) => patch({ amount: e.target.value })}
        slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
      />
    </>
  )

  const tradeAccountFields = (
    <>
      <Select
        size="small"
        displayEmpty
        value={form.cashAccountId}
        onChange={(e) => patch({ cashAccountId: e.target.value })}
        aria-label={t('transfers:form.cashAccountLabel')}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          {t('transfers:form.cashAccountLabel')}
        </MenuItem>
        {cashOptions
          .filter((a) => a.id !== form.investmentAccountId)
          .map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={form.investmentAccountId}
        onChange={(e) => setInvestmentAccountId(e.target.value)}
        aria-label={t('transfers:form.investmentAccountLabel')}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          {t('transfers:form.investmentAccountLabel')}
        </MenuItem>
        {investmentAccounts
          .filter((a) => a.id !== form.cashAccountId)
          .map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
      </Select>
      <TextField
        label={t('transfers:form.taxesLabel')}
        type="number"
        size="small"
        value={form.taxes}
        onChange={(e) => patch({ taxes: e.target.value })}
        slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
      />
    </>
  )

  const duplicateResultingBalanceIds = duplicateResultingBalanceProductIds(form.lines)
  const lineRows = form.lines.map((line) => {
    const holdingOptions = holdingOptionsFor(line)
    const hasDuplicateResultingBalance =
      line.resultingBalance.trim() !== '' && duplicateResultingBalanceIds.has(line.productId)
    return (
      <Box
        key={line.key}
        sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap', mb: 1 }}
      >
        <Select
          size="small"
          displayEmpty
          value={holdingOptions.some((h) => h.productId === line.productId) ? line.productId : ''}
          onChange={(e) => lineActions.update(line.key, { productId: e.target.value })}
          aria-label={t('transfers:form.productLabel')}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="" disabled>
            {t('transfers:form.productLabel')}
          </MenuItem>
          {holdingOptions.map((h) => (
            <MenuItem key={h.id} value={h.productId}>
              {productName(h.productId)}
            </MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          value={line.side}
          onChange={(e) => lineActions.changeSide(line, e.target.value as TradeSide)}
          aria-label={t('transfers:form.sideAria')}
        >
          <MenuItem value="BUY">{t('transfers:form.buyOption')}</MenuItem>
          <MenuItem value="SELL">{t('transfers:form.sellOption')}</MenuItem>
        </Select>
        <TextField
          label={t('transfers:form.quantityLabel')}
          type="number"
          size="small"
          value={line.quantity}
          onChange={(e) => lineActions.update(line.key, { quantity: e.target.value })}
          slotProps={{ htmlInput: { step: 'any', min: '0' } }}
        />
        <TextField
          label={t('transfers:form.unitPriceLabel')}
          type="number"
          size="small"
          value={line.unitPrice}
          onChange={(e) => lineActions.update(line.key, { unitPrice: e.target.value })}
          slotProps={{ htmlInput: { step: 'any', min: '0' } }}
        />
        <TextField
          label={t('transfers:form.resultingBalanceLabel')}
          type="number"
          size="small"
          value={line.resultingBalance}
          onChange={(e) => lineActions.update(line.key, { resultingBalance: e.target.value })}
          error={hasDuplicateResultingBalance}
          helperText={
            hasDuplicateResultingBalance
              ? t('transfers:form.resultingBalanceDuplicateHelper')
              : t('transfers:form.resultingBalanceHelper')
          }
          slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
        />
        {line.side === 'SELL' && (
          <FormControlLabel
            control={
              <Checkbox
                checked={line.closeHolding}
                onChange={(e) => lineActions.update(line.key, { closeHolding: e.target.checked })}
              />
            }
            label={t('transfers:form.closeHoldingLabel')}
          />
        )}
        {form.lines.length > 1 && (
          <IconButton
            size="small"
            aria-label={t('transfers:form.removeLineAria')}
            onClick={() => lineActions.remove(line.key)}
            sx={{ alignSelf: 'center' }}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        )}
      </Box>
    )
  })

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
        form.mode === 'plain' ? (
          <>
            {commonFields}
            {plainFields}
          </>
        ) : (
          <>
            {commonFields}
            {tradeAccountFields}
          </>
        ),
      )}

      {form.mode === 'trade' && (
        <Box sx={{ mt: 2 }} role="group" aria-label={t('transfers:form.tradeLinesGroupAria')}>
          <Typography variant="subtitle2" gutterBottom>
            {t('transfers:form.productLinesTitle')}
          </Typography>
          {lineRows}
          <Button size="small" startIcon={<AddIcon />} onClick={lineActions.add} sx={{ mt: 1 }}>
            {t('transfers:form.addLineButton')}
          </Button>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }} aria-live="polite">
            {netPreview === null
              ? t('transfers:form.previewEnterValues')
              : netPreview === 0
                ? t('transfers:form.previewZero')
                : direction === 'cost'
                  ? t('transfers:form.previewSettlement', {
                      amount: formatMoney(Math.abs(netPreview), locale),
                      from: cashAccountName,
                      to: investmentAccountName,
                    })
                  : t('transfers:form.previewSettlement', {
                      amount: formatMoney(Math.abs(netPreview), locale),
                      from: investmentAccountName,
                      to: cashAccountName,
                    })}
          </Typography>
        </Box>
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
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            {banner}
            {body}
          </Box>
        </DialogContent>
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
