import { MenuItem, Select, TextField } from '@mui/material'
import type { Account } from '../../api/accounts/accounts'
import type { Category } from '../../api/categories/categories'
import type { PaymentMethod } from '../../api/paymentMethods/paymentMethods'
import type { Vehicle } from '../../api/vehicles/vehicles'
import { FUEL_TYPES, fuelTypeLabel } from '../../utils/fuelType'
import type { TransactionFormValues } from './transactionForm'

/** Every field F024 added, empty/cleared. */
const EMPTY_FUEL_FIELDS = {
  vehicleId: '',
  fuelType: '',
  liters: '',
  pricePerLiter: '',
  kmSinceLastFill: '',
  odometer: '',
}

interface TransactionFormFieldsProps {
  form: TransactionFormValues
  onChange: (patch: Partial<TransactionFormValues>) => void
  categories: Category[] | undefined
  /** Accounts the form may offer (open, non-investment); the page decides. */
  accounts: Account[]
  paymentMethods: PaymentMethod[] | undefined
  /** F024: offered only while the selected category is the fuel category. */
  vehicles: Vehicle[] | undefined
}

/**
 * The transaction inputs, shared by the inline form (tablet/desktop) and the full-screen dialog
 * (mobile), so both surfaces have the same fields, limits and labels (F021 row-edit pattern). It
 * renders the fields only: the parent supplies the layout (a wrapping flex row inline, a
 * `FormGrid` in the dialog, where the two long text fields span the full width) and the buttons.
 *
 * <p>F024 (ADR 0021): selecting the fuel category reveals vehicle/fuel type/liters/price-per-liter
 * (mandatory) and km-since-last-fill/odometer (optional); selecting any other category clears them
 * (mirrors the backend rejecting the mismatch, so the form never submits an invalid combination).
 */
export function TransactionFormFields({
  form,
  onChange,
  categories,
  accounts,
  paymentMethods,
  vehicles,
}: TransactionFormFieldsProps) {
  const selectedCategory = categories?.find((c) => c.id === form.categoryId)
  const isFuelCategory = selectedCategory?.fuelCategory ?? false

  function handleCategoryChange(categoryId: string) {
    const category = categories?.find((c) => c.id === categoryId)
    onChange(category?.fuelCategory ? { categoryId } : { categoryId, ...EMPTY_FUEL_FIELDS })
  }

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
        onChange={(e) => handleCategoryChange(e.target.value)}
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
      {isFuelCategory && (
        <>
          <Select
            size="small"
            displayEmpty
            value={form.vehicleId}
            onChange={(e) => onChange({ vehicleId: e.target.value })}
            aria-label="Vehicle"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="" disabled>
              Vehicle
            </MenuItem>
            {vehicles?.map((v) => (
              <MenuItem key={v.id} value={v.id}>
                {v.name}
              </MenuItem>
            ))}
          </Select>
          <Select
            size="small"
            displayEmpty
            value={form.fuelType}
            onChange={(e) => onChange({ fuelType: e.target.value })}
            aria-label="Fuel Type"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="" disabled>
              Fuel Type
            </MenuItem>
            {FUEL_TYPES.map((f) => (
              <MenuItem key={f} value={f}>
                {fuelTypeLabel(f)}
              </MenuItem>
            ))}
          </Select>
          <TextField
            label="Liters"
            type="number"
            size="small"
            required
            value={form.liters}
            onChange={(e) => onChange({ liters: e.target.value })}
            slotProps={{ htmlInput: { step: '0.001', min: '0.001' } }}
          />
          <TextField
            label="Price per Liter"
            type="number"
            size="small"
            required
            value={form.pricePerLiter}
            onChange={(e) => onChange({ pricePerLiter: e.target.value })}
            slotProps={{ htmlInput: { step: '0.001', min: '0.001' } }}
          />
          <TextField
            label="Km Since Last Fill"
            type="number"
            size="small"
            value={form.kmSinceLastFill}
            onChange={(e) => onChange({ kmSinceLastFill: e.target.value })}
            slotProps={{ htmlInput: { step: '0.1', min: '0.1' } }}
            helperText="Leave blank on the vehicle's first recorded fill"
          />
          <TextField
            label="Odometer"
            type="number"
            size="small"
            value={form.odometer}
            onChange={(e) => onChange({ odometer: e.target.value })}
            slotProps={{ htmlInput: { step: '0.1', min: '0.1' } }}
          />
        </>
      )}
    </>
  )
}
