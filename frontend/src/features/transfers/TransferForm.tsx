import { useState, type ReactNode } from 'react'
import {
  Box,
  Button,
  Checkbox,
  DialogActions,
  DialogContent,
  FormControlLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material'
import type { Account } from '../../api/accounts/accounts'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInvestmentProducts } from '../../api/investments/investmentProductsQueries'
import type { Transfer } from '../../api/transfers/transfers'
import { useCreateTransfer, useEditTransfer } from '../../api/transfers/transfersQueries'
import { FormGrid } from '../../components/feedback/ResponsiveDialog'
import { useQueryState } from '../../hooks/queryState'
import { today } from '../../utils/localDate'
import {
  grossTradedValue,
  suggestedResultingBalance,
  tradeTotal,
  type TradeDirection,
} from './tradeMath'

/**
 * A buy/sell to start the form with (F009 spec): the product's own INVESTMENT account on the
 * traded side, the product selected and a suggested description. The user still picks the cash
 * account and everything else.
 */
export interface TransferFormPreset {
  direction: TradeDirection
  investmentAccountId: string
  productId: string
  productName: string
}

interface FormState {
  date: string
  amount: string
  fromAccountId: string
  toAccountId: string
  description: string
  additionalNotes: string
  investmentProductId: string
  quantity: string
  unitPrice: string
  taxes: string
  resultingBalance: string
  /** Once the user types in Amount, the live total stops overwriting it. */
  amountOverridden: boolean
  /** Once the user types in Resulting balance, the suggestion stops replacing it. */
  resultingTouched: boolean
  soldEntirePosition: boolean
}

/** A fresh form, built per use so its date is today's local date rather than the date the module
 * was first loaded. */
function initialState(editing: Transfer | null, preset: TransferFormPreset | null): FormState {
  const blank: FormState = {
    date: today(),
    amount: '',
    fromAccountId: '',
    toAccountId: '',
    description: '',
    additionalNotes: '',
    investmentProductId: '',
    quantity: '',
    unitPrice: '',
    taxes: '',
    resultingBalance: '',
    amountOverridden: false,
    resultingTouched: false,
    soldEntirePosition: false,
  }
  if (editing) {
    return {
      ...blank,
      date: editing.date,
      amount: String(editing.amount),
      fromAccountId: editing.fromAccountId,
      toAccountId: editing.toAccountId,
      description: editing.description,
      additionalNotes: editing.additionalNotes ?? '',
      investmentProductId: editing.investmentProductId ?? '',
      quantity: editing.quantity === null ? '' : String(editing.quantity),
      unitPrice: editing.unitPrice === null ? '' : String(editing.unitPrice),
      taxes: editing.taxes === null ? '' : String(editing.taxes),
      amountOverridden: true,
    }
  }
  if (preset) {
    return {
      ...blank,
      fromAccountId: preset.direction === 'sell' ? preset.investmentAccountId : '',
      toAccountId: preset.direction === 'buy' ? preset.investmentAccountId : '',
      investmentProductId: preset.productId,
      description: `${preset.direction === 'buy' ? 'Buy' : 'Sell'} ${preset.productName}`,
    }
  }
  return blank
}

/** A form field's text as a number, or `null` when empty or not a number. */
function numberOrNull(text: string): number | null {
  if (text.trim() === '') return null
  const value = Number(text)
  return Number.isFinite(value) ? value : null
}

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
 * The transfer create/edit form (F005 spec), extended for buys and sells (F009 spec). Picking an
 * INVESTMENT account shows a product select (that account's open products), then quantity, unit
 * price and taxes with a live total that prefills `amount` (buy: quantity x price + taxes; sell:
 * quantity x price - taxes; typing in Amount overrides it), and - on create - an optional resulting
 * balance, prefilled with a suggestion the user edits or clears, plus a "Sold entire position"
 * checkbox that sends `0`. Editing never touches snapshots, so those two are hidden then.
 *
 * The "To" picker excludes the "From" account (no same-account transfer, F005 spec), and an
 * INVESTMENT account can't be on both sides - the backend's `409`/`400` rules, prevented here.
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
  const [form, setForm] = useState(() => initialState(editing, preset))
  const [saving, setSaving] = useState(false)

  const openAccounts = accounts.filter((a) => !a.closed)
  const fromAccount = accounts.find((a) => a.id === form.fromAccountId)
  const toAccount = accounts.find((a) => a.id === form.toAccountId)
  const direction: TradeDirection | null =
    toAccount?.type === 'INVESTMENT' ? 'buy' : fromAccount?.type === 'INVESTMENT' ? 'sell' : null
  const investmentAccount =
    toAccount?.type === 'INVESTMENT'
      ? toAccount
      : fromAccount?.type === 'INVESTMENT'
        ? fromAccount
        : undefined

  const fromOptions = openAccounts.filter((a) => !(toAccount?.type === 'INVESTMENT' && isInv(a)))
  const toOptions = openAccounts.filter(
    (a) => a.id !== form.fromAccountId && !(fromAccount?.type === 'INVESTMENT' && isInv(a)),
  )

  const investmentAccountId = investmentAccount?.id
  const productsQuery = useInvestmentProducts(investmentAccountId, {
    enabled: investmentAccountId !== undefined,
  })
  useQueryState(productsQuery, onError)
  const products = investmentAccountId ? productsQuery.data : undefined
  const createMutation = useCreateTransfer()
  const editMutation = useEditTransfer()
  const productOptions = (products ?? []).filter(
    (p) => p.accountId === investmentAccountId && (!p.closed || p.id === form.investmentProductId),
  )
  const selectedProduct = productOptions.find((p) => p.id === form.investmentProductId)
  const hasProduct = direction !== null && form.investmentProductId !== ''

  const quantity = numberOrNull(form.quantity)
  const unitPrice = numberOrNull(form.unitPrice)
  const taxes = numberOrNull(form.taxes)
  const amount = Number(form.amount)
  const total = direction ? tradeTotal(direction, quantity, unitPrice, taxes) : null

  // The resulting balance shown: forced to 0 for a full sell, the user's own entry once they typed,
  // otherwise a suggestion from the prior latest snapshot and this trade's gross value.
  let suggestion = ''
  if (direction && selectedProduct && amount > 0) {
    const gross = grossTradedValue(direction, amount, taxes, quantity, unitPrice)
    suggestion = String(
      suggestedResultingBalance(direction, selectedProduct.latestSnapshot?.balance ?? 0, gross),
    )
  }
  const showResulting = hasProduct && editing === null
  const resultingValue = form.soldEntirePosition
    ? '0'
    : form.resultingTouched
      ? form.resultingBalance
      : suggestion

  function patch(changes: Partial<FormState>) {
    setForm((prev) => ({ ...prev, ...changes }))
  }

  /** A trade field changed: keep the prefilled amount in step unless the user overrode it. */
  function patchTradeField(changes: Partial<FormState>) {
    setForm((prev) => {
      const next = { ...prev, ...changes }
      if (next.amountOverridden) return next
      const nextDirection =
        accounts.find((a) => a.id === next.toAccountId)?.type === 'INVESTMENT'
          ? 'buy'
          : accounts.find((a) => a.id === next.fromAccountId)?.type === 'INVESTMENT'
            ? 'sell'
            : null
      if (!nextDirection) return next
      const computed = tradeTotal(
        nextDirection,
        numberOrNull(next.quantity),
        numberOrNull(next.unitPrice),
        numberOrNull(next.taxes),
      )
      return computed === null ? next : { ...next, amount: String(computed) }
    })
  }

  /**
   * Applies new from/to accounts. The product belongs to the INVESTMENT account, so it (and the
   * resulting balance suggestion built on it) is dropped when that account changes.
   */
  function applyAccounts(fromAccountId: string, toAccountId: string) {
    const nextFrom = accounts.find((a) => a.id === fromAccountId)
    const nextTo = accounts.find((a) => a.id === toAccountId)
    const nextInvestment =
      nextTo?.type === 'INVESTMENT'
        ? nextTo
        : nextFrom?.type === 'INVESTMENT'
          ? nextFrom
          : undefined
    const sameInvestmentAccount = nextInvestment?.id === investmentAccount?.id
    setForm((prev) => ({
      ...prev,
      fromAccountId,
      toAccountId,
      ...(sameInvestmentAccount
        ? {}
        : { investmentProductId: '', resultingBalance: '', resultingTouched: false }),
    }))
  }

  function setFromAccountId(fromAccountId: string) {
    const nextFrom = accounts.find((a) => a.id === fromAccountId)
    // Clear "To" if it now collides with the newly-picked "From" (F005 spec: can't pick the same
    // account twice) or would put an investment account on both sides.
    const collides =
      form.toAccountId === fromAccountId ||
      (nextFrom?.type === 'INVESTMENT' && toAccount?.type === 'INVESTMENT')
    applyAccounts(fromAccountId, collides ? '' : form.toAccountId)
  }

  function setToAccountId(toAccountId: string) {
    applyAccounts(form.fromAccountId, toAccountId)
  }

  function isFormValid() {
    if (
      form.date === '' ||
      !(amount > 0) ||
      form.fromAccountId === '' ||
      form.toAccountId === '' ||
      form.fromAccountId === form.toAccountId ||
      form.description.trim() === ''
    ) {
      return false
    }
    if (direction !== null) {
      if (form.investmentProductId === '') return false
      if ((form.quantity.trim() === '') !== (form.unitPrice.trim() === '')) return false
      if (form.quantity.trim() !== '' && !((quantity ?? 0) > 0 && (unitPrice ?? 0) > 0)) {
        return false
      }
      if (form.taxes.trim() !== '' && !((taxes ?? -1) >= 0)) return false
      if (
        showResulting &&
        resultingValue.trim() !== '' &&
        !((numberOrNull(resultingValue) ?? -1) >= 0)
      ) {
        return false
      }
    }
    return true
  }

  async function handleSubmit() {
    if (!isFormValid()) return
    onError(null)
    setSaving(true)
    const trade =
      direction !== null
        ? {
            investmentProductId: form.investmentProductId,
            quantity: quantity ?? undefined,
            unitPrice: unitPrice ?? undefined,
            taxes: taxes ?? undefined,
          }
        : {}
    const request = {
      date: form.date,
      fromAccountId: form.fromAccountId,
      toAccountId: form.toAccountId,
      amount,
      description: form.description.trim(),
      additionalNotes: form.additionalNotes.trim() || undefined,
      ...trade,
    }
    try {
      const saved = editing
        ? await editMutation.mutateAsync({ id: editing.id, ...request })
        : await createMutation.mutateAsync({
            ...request,
            ...(showResulting && resultingValue.trim() !== ''
              ? { resultingBalance: Number(resultingValue) }
              : {}),
          })
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
      {fieldRow(
        <>
          <TextField
            label="Date"
            type="date"
            size="small"
            value={form.date}
            onChange={(e) => patch({ date: e.target.value })}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <Select
            size="small"
            displayEmpty
            value={form.fromAccountId}
            onChange={(e) => setFromAccountId(e.target.value)}
            aria-label="From Account"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="" disabled>
              From Account
            </MenuItem>
            {fromOptions.map((a) => (
              <MenuItem key={a.id} value={a.id}>
                {a.name}
              </MenuItem>
            ))}
          </Select>
          <Select
            size="small"
            displayEmpty
            value={form.toAccountId}
            onChange={(e) => setToAccountId(e.target.value)}
            aria-label="To Account"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="" disabled>
              To Account
            </MenuItem>
            {toOptions.map((a) => (
              <MenuItem key={a.id} value={a.id}>
                {a.name}
              </MenuItem>
            ))}
          </Select>
          {direction !== null && (
            <Select
              size="small"
              displayEmpty
              value={
                productOptions.some((p) => p.id === form.investmentProductId)
                  ? form.investmentProductId
                  : ''
              }
              onChange={(e) =>
                patchTradeField({
                  investmentProductId: e.target.value,
                  resultingBalance: '',
                  resultingTouched: false,
                })
              }
              aria-label="Product"
              sx={{ minWidth: 180 }}
            >
              <MenuItem value="" disabled>
                Product
              </MenuItem>
              {productOptions.map((p) => (
                <MenuItem key={p.id} value={p.id}>
                  {p.name}
                </MenuItem>
              ))}
            </Select>
          )}
          <TextField
            label="Amount"
            type="number"
            size="small"
            value={form.amount}
            onChange={(e) => patch({ amount: e.target.value, amountOverridden: true })}
            slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
          />
          <TextField
            label="Description"
            size="small"
            required
            value={form.description}
            onChange={(e) => patch({ description: e.target.value })}
            slotProps={{ htmlInput: { maxLength: 150 } }}
            sx={wide}
          />
          <TextField
            label="Additional Notes"
            size="small"
            value={form.additionalNotes}
            onChange={(e) => patch({ additionalNotes: e.target.value })}
            slotProps={{ htmlInput: { maxLength: 500 } }}
            sx={wide}
          />
        </>,
      )}

      {hasProduct && (
        <Box sx={{ mt: 2 }} role="group" aria-label="Trade details">
          <Typography variant="subtitle2" gutterBottom>
            Trade details (optional, record-only)
          </Typography>
          {fieldRow(
            <>
              <TextField
                label="Quantity"
                type="number"
                size="small"
                value={form.quantity}
                onChange={(e) => patchTradeField({ quantity: e.target.value })}
                slotProps={{ htmlInput: { step: 'any', min: '0' } }}
              />
              <TextField
                label="Unit price"
                type="number"
                size="small"
                value={form.unitPrice}
                onChange={(e) => patchTradeField({ unitPrice: e.target.value })}
                slotProps={{ htmlInput: { step: 'any', min: '0' } }}
              />
              <TextField
                label="Taxes"
                type="number"
                size="small"
                value={form.taxes}
                onChange={(e) => patchTradeField({ taxes: e.target.value })}
                slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
              />
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ alignSelf: 'center', ...wide }}
                aria-live="polite"
              >
                {total === null
                  ? 'Enter quantity and unit price for a live total.'
                  : `Total ${total.toFixed(2)} (quantity x unit price ${direction === 'buy' ? '+' : '-'} taxes)`}
              </Typography>
            </>,
            3,
          )}
          {showResulting ? (
            <Box sx={{ mt: 2, display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
              <TextField
                label="Resulting balance"
                type="number"
                size="small"
                value={resultingValue}
                disabled={form.soldEntirePosition}
                onChange={(e) =>
                  patch({ resultingBalance: e.target.value, resultingTouched: true })
                }
                helperText="The product's value after this trade: recorded as a snapshot on the transfer date. A suggestion - edit it to your broker's balance, or clear it to skip."
                slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
                sx={dialog ? { minWidth: 0, width: 1 } : { minWidth: 260 }}
              />
              {direction === 'sell' && (
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={form.soldEntirePosition}
                      onChange={(e) => patch({ soldEntirePosition: e.target.checked })}
                    />
                  }
                  label="Sold entire position"
                />
              )}
            </Box>
          ) : (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              Editing a trade never changes snapshots.
            </Typography>
          )}
        </Box>
      )}
    </>
  )

  const cancelButton =
    editing || onCancel ? (
      <Button onClick={onCancel} disabled={saving}>
        Cancel
      </Button>
    ) : null
  const submitButton = (
    <Button
      variant="contained"
      disabled={saving || !isFormValid()}
      onClick={() => void handleSubmit()}
    >
      {editing ? 'Save changes' : 'Add'}
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

function isInv(account: Account): boolean {
  return account.type === 'INVESTMENT'
}
