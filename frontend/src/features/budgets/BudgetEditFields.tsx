import { TextField } from '@mui/material'

interface BudgetCapFieldProps {
  value: string
  onChange: (cap: string) => void
  autoFocus?: boolean
}

/**
 * The monthly-cap input of a budget edit (F021 row-edit pattern): the same field rendered inline
 * in the table cell on tablet/desktop and inside the mobile edit dialog, so both surfaces share the
 * same limits. Serves both "Edit cap" and "Resume budget" (a stopped budget's cap edit) - the
 * parent supplies the surrounding label/button text, which differs between the two.
 */
export function BudgetCapField({ value, onChange, autoFocus }: BudgetCapFieldProps) {
  return (
    <TextField
      size="small"
      type="number"
      label="Monthly cap"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
      autoFocus={autoFocus}
    />
  )
}
