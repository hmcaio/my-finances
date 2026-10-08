import { useState, type ReactNode } from 'react'
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
import type { CreateTransferRequest, Transfer } from '../../api/transfers/transfers'
import { useCreateTransfer, useEditTransfer } from '../../api/transfers/transfersQueries'
import { FormGrid } from '../../components/feedback/ResponsiveDialog'
import { useQueryState } from '../../hooks/queryState'
import { today } from '../../utils/localDate'
import { netSettlementDirection, netSettlementPreview, type TradeSide } from './tradeMath'

/**
 * A buy/sell to start the form with (F009 spec, kept by F027): the product's own INVESTMENT
 * account on the traded side, the product selected and a suggested description. The user still
 * picks the cash account and everything else.
 */
export interface TransferFormPreset {
  direction: 'buy' | 'sell'
  investmentAccountId: string
  productId: string
  productName: string
}

interface LineState {
  key: number
  productId: string
  side: TradeSide
  quantity: string
  unitPrice: string
  resultingBalance: string
  closeHolding: boolean
}

type FormMode = 'plain' | 'trade'

interface FormState {
  mode: FormMode
  date: string
  description: string
  additionalNotes: string
  // Plain mode.
  fromAccountId: string
  toAccountId: string
  amount: string
  // Trade mode (F027, ADR 0024): cashAccountId/investmentAccountId are unlabeled - direction is
  // derived from the lines' net settlement, never picked by the user.
  cashAccountId: string
  investmentAccountId: string
  taxes: string
  lines: LineState[]
}

function blankLine(key: number, productId = '', side: TradeSide = 'BUY'): LineState {
  return {
    key,
    productId,
    side,
    quantity: '',
    unitPrice: '',
    resultingBalance: '',
    closeHolding: false,
  }
}

/** A fresh form, built per use so its date is today's local date rather than the date the module
 * was first loaded. */
function initialState(editing: Transfer | null, preset: TransferFormPreset | null): FormState {
  const blank: FormState = {
    mode: 'plain',
    date: today(),
    description: '',
    additionalNotes: '',
    fromAccountId: '',
    toAccountId: '',
    amount: '',
    cashAccountId: '',
    investmentAccountId: '',
    taxes: '0',
    lines: [blankLine(0)],
  }
  if (editing) {
    if (editing.tradeConfirmation) {
      return {
        ...blank,
        mode: 'trade',
        date: editing.date,
        description: editing.description,
        additionalNotes: editing.additionalNotes ?? '',
        taxes: editing.taxes === null ? '0' : String(editing.taxes),
        lines: editing.tradeConfirmation.lines.map((line, i) => ({
          key: i,
          productId: line.productId,
          side: line.side,
          quantity: String(line.quantity),
          unitPrice: String(line.unitPrice),
          resultingBalance: line.resultingBalance === null ? '' : String(line.resultingBalance),
          closeHolding: line.closeHolding,
        })),
        // cashAccountId/investmentAccountId are re-derived from from/to once accounts load (the
        // caller passes `accounts`, not available here); the form fills them in on first render
        // via `deriveTradeAccounts`, called from the component body below.
        fromAccountId: editing.fromAccountId,
        toAccountId: editing.toAccountId,
      }
    }
    return {
      ...blank,
      mode: 'plain',
      date: editing.date,
      amount: String(editing.amount),
      fromAccountId: editing.fromAccountId,
      toAccountId: editing.toAccountId,
      description: editing.description,
      additionalNotes: editing.additionalNotes ?? '',
    }
  }
  if (preset) {
    return {
      ...blank,
      mode: 'trade',
      investmentAccountId: preset.investmentAccountId,
      lines: [blankLine(0, preset.productId, preset.direction === 'buy' ? 'BUY' : 'SELL')],
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
  const [form, setForm] = useState(() => {
    const initial = initialState(editing, preset)
    // Trade-confirmation edit: derive cashAccountId/investmentAccountId from from/to now that
    // `accounts` is available (the INVESTMENT side is whichever endpoint is an INVESTMENT account).
    if (initial.mode === 'trade' && editing?.tradeConfirmation) {
      const toAccount = accounts.find((a) => a.id === editing.toAccountId)
      const fromAccount = accounts.find((a) => a.id === editing.fromAccountId)
      const investmentId =
        toAccount?.type === 'INVESTMENT'
          ? editing.toAccountId
          : fromAccount?.type === 'INVESTMENT'
            ? editing.fromAccountId
            : ''
      const cashId =
        investmentId === editing.toAccountId ? editing.fromAccountId : editing.toAccountId
      return { ...initial, investmentAccountId: investmentId, cashAccountId: cashId }
    }
    return initial
  })
  const [saving, setSaving] = useState(false)
  const [nextLineKey, setNextLineKey] = useState(form.lines.length)

  const openAccounts = accounts.filter((a) => !a.closed)
  // The cash side of a confirmation must not itself be INVESTMENT (F027/ADR 0024, same rule as
  // F009's original "a transfer between two investment accounts is not supported").
  const investmentAccounts = openAccounts.filter((a) => a.type === 'INVESTMENT')
  const cashOptions = openAccounts.filter((a) => a.type !== 'INVESTMENT')

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

  function patch(changes: Partial<FormState>) {
    setForm((prev) => ({ ...prev, ...changes }))
  }

  function setMode(mode: FormMode) {
    patch({ mode })
  }

  function setInvestmentAccountId(investmentAccountId: string) {
    // The holdings offered depend on the account, so changing it drops every line's product pick.
    patch({
      investmentAccountId,
      lines: form.lines.map((l) => ({ ...l, productId: '' })),
    })
  }

  function holdingOptionsFor(line: LineState) {
    return (holdings ?? []).filter((h) => !h.closed || h.productId === line.productId)
  }

  function updateLine(key: number, changes: Partial<LineState>) {
    patch({ lines: form.lines.map((l) => (l.key === key ? { ...l, ...changes } : l)) })
  }

  function addLine() {
    patch({ lines: [...form.lines, blankLine(nextLineKey)] })
    setNextLineKey((n) => n + 1)
  }

  function removeLine(key: number) {
    patch({ lines: form.lines.filter((l) => l.key !== key) })
  }

  const taxes = numberOrNull(form.taxes)
  const previewInputs = form.lines.map((l) => ({
    side: l.side,
    quantity: numberOrNull(l.quantity),
    unitPrice: numberOrNull(l.unitPrice),
  }))
  const netPreview = form.mode === 'trade' ? netSettlementPreview(previewInputs, taxes) : null
  const direction = netSettlementDirection(netPreview)
  const cashAccountName = accounts.find((a) => a.id === form.cashAccountId)?.name ?? 'cash account'
  const investmentAccountName =
    accounts.find((a) => a.id === form.investmentAccountId)?.name ?? 'investment account'

  function isLineValid(line: LineState): boolean {
    if (line.productId === '') return false
    const quantity = numberOrNull(line.quantity)
    const unitPrice = numberOrNull(line.unitPrice)
    if (!(quantity !== null && quantity > 0)) return false
    if (!(unitPrice !== null && unitPrice > 0)) return false
    if (
      line.resultingBalance.trim() !== '' &&
      !((numberOrNull(line.resultingBalance) ?? -1) >= 0)
    ) {
      return false
    }
    if (line.closeHolding && line.side !== 'SELL') return false
    return true
  }

  function isFormValid(): boolean {
    if (form.date === '' || form.description.trim() === '') return false
    if (form.mode === 'plain') {
      const amount = Number(form.amount)
      return (
        amount > 0 &&
        form.fromAccountId !== '' &&
        form.toAccountId !== '' &&
        form.fromAccountId !== form.toAccountId
      )
    }
    if (form.cashAccountId === '' || form.investmentAccountId === '') return false
    if (form.cashAccountId === form.investmentAccountId) return false
    if (form.lines.length === 0) return false
    if (!(taxes !== null && taxes >= 0)) return false
    if (!form.lines.every(isLineValid)) return false
    return netPreview !== null && netPreview !== 0
  }

  async function handleSubmit() {
    if (!isFormValid()) return
    onError(null)
    setSaving(true)
    try {
      const request: CreateTransferRequest =
        form.mode === 'plain'
          ? {
              date: form.date,
              fromAccountId: form.fromAccountId,
              toAccountId: form.toAccountId,
              amount: Number(form.amount),
              description: form.description.trim(),
              additionalNotes: form.additionalNotes.trim() || undefined,
            }
          : {
              date: form.date,
              cashAccountId: form.cashAccountId,
              investmentAccountId: form.investmentAccountId,
              description: form.description.trim(),
              additionalNotes: form.additionalNotes.trim() || undefined,
              tradeConfirmation: {
                taxes: taxes ?? 0,
                lines: form.lines.map((l) => ({
                  productId: l.productId,
                  side: l.side,
                  quantity: Number(l.quantity),
                  unitPrice: Number(l.unitPrice),
                  resultingBalance:
                    l.resultingBalance.trim() === '' ? undefined : Number(l.resultingBalance),
                  closeHolding: l.closeHolding,
                })),
              },
            }
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
        label="Date"
        type="date"
        size="small"
        value={form.date}
        onChange={(e) => patch({ date: e.target.value })}
        slotProps={{ inputLabel: { shrink: true } }}
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
    </>
  )

  const plainFields = (
    <>
      <Select
        size="small"
        displayEmpty
        value={form.fromAccountId}
        onChange={(e) => patch({ fromAccountId: e.target.value })}
        aria-label="From Account"
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          From Account
        </MenuItem>
        {openAccounts.map((a) => (
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
        aria-label="To Account"
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          To Account
        </MenuItem>
        {openAccounts
          .filter((a) => a.id !== form.fromAccountId)
          .map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
      </Select>
      <TextField
        label="Amount"
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
        aria-label="Cash Account"
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          Cash Account
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
        aria-label="Investment Account"
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          Investment Account
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
        label="Taxes"
        type="number"
        size="small"
        value={form.taxes}
        onChange={(e) => patch({ taxes: e.target.value })}
        slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
      />
    </>
  )

  const lineRows = form.lines.map((line) => {
    const holdingOptions = holdingOptionsFor(line)
    return (
      <Box
        key={line.key}
        sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap', mb: 1 }}
      >
        <Select
          size="small"
          displayEmpty
          value={holdingOptions.some((h) => h.productId === line.productId) ? line.productId : ''}
          onChange={(e) => updateLine(line.key, { productId: e.target.value })}
          aria-label="Product"
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="" disabled>
            Product
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
          onChange={(e) =>
            updateLine(line.key, {
              side: e.target.value as TradeSide,
              closeHolding: e.target.value === 'SELL' ? line.closeHolding : false,
            })
          }
          aria-label="Side"
        >
          <MenuItem value="BUY">Buy</MenuItem>
          <MenuItem value="SELL">Sell</MenuItem>
        </Select>
        <TextField
          label="Quantity"
          type="number"
          size="small"
          value={line.quantity}
          onChange={(e) => updateLine(line.key, { quantity: e.target.value })}
          slotProps={{ htmlInput: { step: 'any', min: '0' } }}
        />
        <TextField
          label="Unit price"
          type="number"
          size="small"
          value={line.unitPrice}
          onChange={(e) => updateLine(line.key, { unitPrice: e.target.value })}
          slotProps={{ htmlInput: { step: 'any', min: '0' } }}
        />
        <TextField
          label="Resulting balance"
          type="number"
          size="small"
          value={line.resultingBalance}
          onChange={(e) => updateLine(line.key, { resultingBalance: e.target.value })}
          helperText="Optional: recorded as a snapshot on the transfer date."
          slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
        />
        {line.side === 'SELL' && (
          <FormControlLabel
            control={
              <Checkbox
                checked={line.closeHolding}
                onChange={(e) => updateLine(line.key, { closeHolding: e.target.checked })}
              />
            }
            label="Close this holding"
          />
        )}
        {form.lines.length > 1 && (
          <IconButton
            size="small"
            aria-label="Remove line"
            onClick={() => removeLine(line.key)}
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
        aria-label="Transfer type"
      >
        <ToggleButton value="plain">Plain transfer</ToggleButton>
        <ToggleButton value="trade">Trade confirmation</ToggleButton>
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
        <Box sx={{ mt: 2 }} role="group" aria-label="Trade lines">
          <Typography variant="subtitle2" gutterBottom>
            Product lines
          </Typography>
          {lineRows}
          <Button size="small" startIcon={<AddIcon />} onClick={addLine} sx={{ mt: 1 }}>
            Add line
          </Button>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }} aria-live="polite">
            {netPreview === null
              ? 'Enter every line’s quantity and unit price for a settlement preview.'
              : netPreview === 0
                ? 'This settlement nets to exactly zero and will be rejected - check the lines.'
                : direction === 'cost'
                  ? `Net settlement: ${Math.abs(netPreview).toFixed(2)} (${cashAccountName} → ${investmentAccountName})`
                  : `Net settlement: ${Math.abs(netPreview).toFixed(2)} (${investmentAccountName} → ${cashAccountName})`}
          </Typography>
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
