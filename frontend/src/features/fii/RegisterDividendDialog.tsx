import { useMemo, useState } from 'react'
import {
  Button,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Select,
  TextField,
} from '@mui/material'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useCategories } from '../../api/categories/categoriesQueries'
import { useRegisterDividend } from '../../api/investments/fiiDividendsQueries'
import { useInvestmentHoldingsByProduct } from '../../api/investments/investmentHoldingsQueries'
import type { InvestmentProduct } from '../../api/investments/investmentProducts'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { today } from '../../utils/localDate'
import { nameLookup } from '../../utils/nameLookup'

interface RegisterDividendDialogProps {
  open: boolean
  onClose: () => void
  /** Every product classified under the "REITs (FIIs)" sub-category - the ticker picker. */
  fiiProducts: InvestmentProduct[]
}

/**
 * The dedicated "Register Dividend" form (F026 spec): faster than the generic transaction form
 * for the actual recurring workflow - a ticker/holding picker pre-filtered to FII holdings,
 * amount and date. Still creates a plain `Transaction` in the dedicated dividend category, tagged
 * with the chosen holding (the XOR invariant `TransactionService` enforces).
 *
 * <p>The spec's field list ("FII/holding picker, amount, date") doesn't account for the fact that
 * a `Transaction` always also needs a cash-destination `accountId` (never the `INVESTMENT`
 * account itself - `TransactionService` rejects that type) and a `paymentMethodId`, exactly like
 * any other income transaction; both are additional required pickers here, not asked for in the
 * spec but structurally unavoidable.
 */
export function RegisterDividendDialog({
  open,
  onClose,
  fiiProducts,
}: RegisterDividendDialogProps) {
  const [error, setError] = useState<string | null>(null)
  const categoriesQuery = useCategories()
  const dividendCategory = categoriesQuery.data?.find((c) => c.dividendCategory)
  const accountsQuery = useAccounts()
  const accounts = (accountsQuery.data ?? []).filter((a) => a.type !== 'INVESTMENT')
  // Unfiltered - a holding's accountId always points at an INVESTMENT account, excluded above.
  const holdingAccountName = useMemo(
    () => nameLookup(accountsQuery.data ?? [], (a) => a.name),
    [accountsQuery.data],
  )
  const paymentMethodsQuery = usePaymentMethods()
  const paymentMethods = paymentMethodsQuery.data ?? []
  const registerDividend = useRegisterDividend()

  const [productId, setProductId] = useState('')
  const holdingsQuery = useInvestmentHoldingsByProduct(productId || undefined)
  const holdings = holdingsQuery.data?.filter((h) => !h.closed) ?? []
  const [holdingId, setHoldingId] = useState('')
  const [accountId, setAccountId] = useState('')
  const [paymentMethodId, setPaymentMethodId] = useState('')
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState(today())
  const [description, setDescription] = useState('')
  const [saving, setSaving] = useState(false)

  const selectedProduct = fiiProducts.find((p) => p.id === productId)

  function reset() {
    setProductId('')
    setHoldingId('')
    setAccountId('')
    setPaymentMethodId('')
    setAmount('')
    setDate(today())
    setDescription('')
    setError(null)
  }

  function handleClose() {
    reset()
    onClose()
  }

  function changeProduct(next: string) {
    setProductId(next)
    setHoldingId('')
  }

  async function handleSubmit() {
    if (!dividendCategory || !holdingId || !accountId || !paymentMethodId || !amount) return
    setError(null)
    setSaving(true)
    try {
      await registerDividend.mutateAsync({
        date,
        amount: Number(amount),
        categoryId: dividendCategory.id,
        accountId,
        paymentMethodId,
        description:
          description.trim() ||
          `Dividend - ${selectedProduct?.ticker ?? selectedProduct?.name ?? ''}`,
        investmentHoldingId: holdingId,
      })
      handleClose()
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const canSubmit = Boolean(
    dividendCategory && holdingId && accountId && paymentMethodId && Number(amount) > 0 && date,
  )

  return (
    <ResponsiveDialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Register dividend</DialogTitle>
      <DialogContent>
        <ErrorAlert message={error} onDismiss={() => setError(null)} />
        <FormGrid>
          <Select
            size="small"
            displayEmpty
            value={productId}
            onChange={(e) => changeProduct(e.target.value)}
            aria-label="Ticker"
          >
            <MenuItem value="" disabled>
              Ticker
            </MenuItem>
            {fiiProducts.map((product) => (
              <MenuItem key={product.id} value={product.id}>
                {product.ticker ?? product.name}
              </MenuItem>
            ))}
          </Select>
          <Select
            size="small"
            displayEmpty
            value={holdingId}
            onChange={(e) => setHoldingId(e.target.value)}
            disabled={!productId}
            aria-label="Holding"
          >
            <MenuItem value="" disabled>
              Holding
            </MenuItem>
            {holdings.map((holding) => (
              <MenuItem key={holding.id} value={holding.id}>
                {holdingAccountName(holding.accountId)}
              </MenuItem>
            ))}
          </Select>
          <TextField
            label="Amount"
            size="small"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
          />
          <TextField
            label="Date"
            size="small"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <Select
            size="small"
            displayEmpty
            value={accountId}
            onChange={(e) => setAccountId(e.target.value)}
            aria-label="Deposit into account"
          >
            <MenuItem value="" disabled>
              Deposit into account
            </MenuItem>
            {accounts.map((account) => (
              <MenuItem key={account.id} value={account.id}>
                {account.name}
              </MenuItem>
            ))}
          </Select>
          <Select
            size="small"
            displayEmpty
            value={paymentMethodId}
            onChange={(e) => setPaymentMethodId(e.target.value)}
            aria-label="Payment method"
          >
            <MenuItem value="" disabled>
              Payment method
            </MenuItem>
            {paymentMethods.map((paymentMethod) => (
              <MenuItem key={paymentMethod.id} value={paymentMethod.id}>
                {paymentMethod.name}
              </MenuItem>
            ))}
          </Select>
          <TextField
            label="Description (optional)"
            size="small"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            sx={{ gridColumn: '1 / -1' }}
          />
        </FormGrid>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={saving}>
          Cancel
        </Button>
        <Button
          variant="contained"
          disabled={saving || !canSubmit}
          onClick={() => void handleSubmit()}
        >
          Register
        </Button>
      </DialogActions>
    </ResponsiveDialog>
  )
}
