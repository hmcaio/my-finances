import { MenuItem, Select, TextField } from '@mui/material'
import type { Account } from '../../api/accounts/accounts'
import type { Category } from '../../api/categories/categories'
import type { PaymentMethod } from '../../api/paymentMethods/paymentMethods'
import type { TransactionFormValues } from './transactionForm'

interface TransactionFormFieldsProps {
  form: TransactionFormValues
  onChange: (patch: Partial<TransactionFormValues>) => void
  categories: Category[] | undefined
  /** Accounts the form may offer (open, non-investment); the page decides. */
  accounts: Account[]
  paymentMethods: PaymentMethod[] | undefined
}

/**
 * The transaction inputs, shared by the inline form (tablet/desktop) and the full-screen dialog
 * (mobile), so both surfaces have the same fields, limits and labels (F021 row-edit pattern). It
 * renders the fields only: the parent supplies the layout (a wrapping flex row inline, a
 * `FormGrid` in the dialog, where the two long text fields span the full width) and the buttons.
 */
export function TransactionFormFields({
  form,
  onChange,
  categories,
  accounts,
  paymentMethods,
}: TransactionFormFieldsProps) {
  return (
    <>
      <TextField
        label="Date"
        type="date"
        size="small"
        value={form.date}
        onChange={(e) => onChange({ date: e.target.value })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        label="Amount"
        type="number"
        size="small"
        value={form.amount}
        onChange={(e) => onChange({ amount: e.target.value })}
        slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
      />
      <Select
        size="small"
        displayEmpty
        value={form.categoryId}
        onChange={(e) => onChange({ categoryId: e.target.value })}
        aria-label="Category"
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          Category
        </MenuItem>
        {categories?.map((c) => (
          <MenuItem key={c.id} value={c.id}>
            {c.name}
          </MenuItem>
        ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={form.accountId}
        onChange={(e) => onChange({ accountId: e.target.value })}
        aria-label="Account"
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          Account
        </MenuItem>
        {accounts.map((a) => (
          <MenuItem key={a.id} value={a.id}>
            {a.name}
          </MenuItem>
        ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={form.paymentMethodId}
        onChange={(e) => onChange({ paymentMethodId: e.target.value })}
        aria-label="Payment Method"
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          Payment Method
        </MenuItem>
        {paymentMethods?.map((p) => (
          <MenuItem key={p.id} value={p.id}>
            {p.name}
          </MenuItem>
        ))}
      </Select>
      <TextField
        label="Description"
        size="small"
        required
        value={form.description}
        onChange={(e) => onChange({ description: e.target.value })}
        slotProps={{ htmlInput: { maxLength: 150 } }}
        sx={{ gridColumn: '1 / -1' }}
      />
      <TextField
        label="Additional Notes"
        size="small"
        value={form.additionalNotes}
        onChange={(e) => onChange({ additionalNotes: e.target.value })}
        slotProps={{ htmlInput: { maxLength: 500 } }}
        sx={{ gridColumn: '1 / -1' }}
      />
    </>
  )
}
