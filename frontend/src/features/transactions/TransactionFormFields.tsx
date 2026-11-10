import type { Ref } from 'react'
import { MenuItem, Select, TextField } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { Account } from '../../api/accounts/accounts'
import type { Category, CategoryType } from '../../api/categories/categories'
import type { PaymentMethod } from '../../api/paymentMethods/paymentMethods'
import type { Vehicle } from '../../api/vehicles/vehicles'
import { translateSeedName } from '../../i18n/seedNames'
import { FUEL_TYPES, fuelTypeLabel } from '../../utils/fuelType'
import { EMPTY_FUEL_FIELDS, type TransactionFormValues } from './transactionForm'
import { SELECT_MIN_WIDTH } from '../../components/layout/layoutSx'
import { FORM_GRID_FULL_ROW } from '../../components/layout/layoutSx'

interface TransactionFormFieldsProps {
  form: TransactionFormValues
  onChange: (patch: Partial<TransactionFormValues>) => void
  categories: Category[] | undefined
  /** Accounts the form may offer (open, non-investment); the page decides. */
  accounts: Account[]
  paymentMethods: PaymentMethod[] | undefined
  /** F024: offered only while the selected category is the fuel category. */
  vehicles: Vehicle[] | undefined
  /** F040: offers only the categories of this type (Expense or Income); omit to offer every one. */
  type?: CategoryType
  /** The amount input, for an explicit `focus()` (F040: after "Add and new"). */
  amountInputRef?: Ref<HTMLInputElement>
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
  type,
  amountInputRef,
}: TransactionFormFieldsProps) {
  const { t } = useTranslation(['transactions', 'common'])
  const categoryOptions = type ? categories?.filter((c) => c.type === type) : categories
  const selectedCategory = categoryOptions?.find((c) => c.id === form.categoryId)
  const isFuelCategory = selectedCategory?.fuelCategory ?? false

  function handleCategoryChange(categoryId: string) {
    const category = categories?.find((c) => c.id === categoryId)
    onChange(category?.fuelCategory ? { categoryId } : { categoryId, ...EMPTY_FUEL_FIELDS })
  }

  return (
    <>
      <TextField
        label={t('transactions:form.dateLabel')}
        type="date"
        size="small"
        required
        value={form.date}
        onChange={(e) => onChange({ date: e.target.value })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        label={t('transactions:form.amountLabel')}
        type="number"
        size="small"
        required
        autoFocus
        inputRef={amountInputRef}
        value={form.amount}
        onChange={(e) => onChange({ amount: e.target.value })}
        slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
      />
      <Select
        size="small"
        displayEmpty
        required
        // A preset category (the Fuel page's "add fill-up") can arrive before the list has loaded;
        // showing it only once its option exists avoids MUI's out-of-range warning.
        value={selectedCategory ? form.categoryId : ''}
        onChange={(e) => handleCategoryChange(e.target.value)}
        aria-label={t('transactions:form.categoryAria')}
        sx={SELECT_MIN_WIDTH}
      >
        <MenuItem value="" disabled>
          {`${t('transactions:form.categoryPlaceholder')} *`}
        </MenuItem>
        {categoryOptions?.map((c) => (
          <MenuItem key={c.id} value={c.id}>
            {translateSeedName('categories', c.name, t)}
          </MenuItem>
        ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        required
        value={form.accountId}
        onChange={(e) => onChange({ accountId: e.target.value })}
        aria-label={t('transactions:form.accountAria')}
        sx={SELECT_MIN_WIDTH}
      >
        <MenuItem value="" disabled>
          {`${t('transactions:form.accountPlaceholder')} *`}
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
        required
        value={form.paymentMethodId}
        onChange={(e) => onChange({ paymentMethodId: e.target.value })}
        aria-label={t('transactions:form.paymentMethodAria')}
        sx={SELECT_MIN_WIDTH}
      >
        <MenuItem value="" disabled>
          {`${t('transactions:form.paymentMethodPlaceholder')} *`}
        </MenuItem>
        {paymentMethods?.map((p) => (
          <MenuItem key={p.id} value={p.id}>
            {translateSeedName('paymentMethods', p.name, t)}
          </MenuItem>
        ))}
      </Select>
      <TextField
        label={t('transactions:form.descriptionLabel')}
        size="small"
        required
        value={form.description}
        onChange={(e) => onChange({ description: e.target.value })}
        slotProps={{ htmlInput: { maxLength: 150 } }}
        sx={FORM_GRID_FULL_ROW}
      />
      <TextField
        label={t('transactions:form.additionalNotesLabel')}
        size="small"
        value={form.additionalNotes}
        onChange={(e) => onChange({ additionalNotes: e.target.value })}
        slotProps={{ htmlInput: { maxLength: 500 } }}
        sx={FORM_GRID_FULL_ROW}
      />
      {isFuelCategory && (
        <>
          <Select
            size="small"
            displayEmpty
            required
            value={form.vehicleId}
            onChange={(e) => onChange({ vehicleId: e.target.value })}
            aria-label={t('transactions:form.vehicleAria')}
            sx={SELECT_MIN_WIDTH}
          >
            <MenuItem value="" disabled>
              {`${t('transactions:form.vehiclePlaceholder')} *`}
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
            required
            value={form.fuelType}
            onChange={(e) => onChange({ fuelType: e.target.value })}
            aria-label={t('transactions:form.fuelTypeAria')}
            sx={SELECT_MIN_WIDTH}
          >
            <MenuItem value="" disabled>
              {`${t('transactions:form.fuelTypePlaceholder')} *`}
            </MenuItem>
            {FUEL_TYPES.map((f) => (
              <MenuItem key={f} value={f}>
                {fuelTypeLabel(f)}
              </MenuItem>
            ))}
          </Select>
          <TextField
            label={t('transactions:form.litersLabel')}
            type="number"
            size="small"
            required
            value={form.liters}
            onChange={(e) => onChange({ liters: e.target.value })}
            slotProps={{ htmlInput: { step: '0.001', min: '0.001' } }}
          />
          <TextField
            label={t('transactions:form.pricePerLiterLabel')}
            type="number"
            size="small"
            required
            value={form.pricePerLiter}
            onChange={(e) => onChange({ pricePerLiter: e.target.value })}
            slotProps={{ htmlInput: { step: '0.001', min: '0.001' } }}
          />
          <TextField
            label={t('transactions:form.kmSinceLastFillLabel')}
            type="number"
            size="small"
            value={form.kmSinceLastFill}
            onChange={(e) => onChange({ kmSinceLastFill: e.target.value })}
            slotProps={{ htmlInput: { step: '0.1', min: '0.1' } }}
            helperText={t('transactions:form.kmSinceLastFillHelper')}
          />
          <TextField
            label={t('transactions:form.odometerLabel')}
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
