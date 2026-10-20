import { TextField, type TextFieldProps } from '@mui/material'
import { currentMonth } from '../../utils/localDate'

interface MonthPickerProps {
  label: string
  value: string
  onChange: (value: string) => void
  /**
   * Clamp selection so it never exceeds the current month (F026's FII page: there's no "future"
   * trade/snapshot data to show). Omit for a month that may legitimately be in the future, e.g.
   * an allocation plan's "effective from" or a budget report month.
   */
  clampToCurrentMonth?: boolean
  size?: TextFieldProps['size']
}

/** A `<input type="month">` field (`YYYY-MM`), shared by every month-scoped view (Budgets, FII). */
export function MonthPicker({
  label,
  value,
  onChange,
  clampToCurrentMonth = false,
  size = 'small',
}: MonthPickerProps) {
  return (
    <TextField
      label={label}
      type="month"
      size={size}
      value={value}
      onChange={(e) => {
        const next = e.target.value
        onChange(clampToCurrentMonth && next > currentMonth() ? currentMonth() : next)
      }}
      slotProps={{
        inputLabel: { shrink: true },
        htmlInput: clampToCurrentMonth ? { max: currentMonth() } : undefined,
      }}
    />
  )
}
