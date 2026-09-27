import { TextField, type SxProps, type Theme } from '@mui/material'

interface AmountFieldProps {
  value: string
  onChange: (amount: string) => void
  autoFocus?: boolean
  sx?: SxProps<Theme>
}

/** The amount input of a recurring template's cap edit (F021 row-edit pattern): narrow (`sx`) when
 * inline in the table cell, full width in the mobile dialog via {@link RecurringTemplateEditFields}. */
export function RecurringTemplateAmountField({ value, onChange, autoFocus, sx }: AmountFieldProps) {
  return (
    <TextField
      size="small"
      type="number"
      label="Amount"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
      autoFocus={autoFocus}
      sx={sx}
    />
  )
}

interface DayOfMonthFieldProps {
  value: string
  onChange: (dayOfMonth: string) => void
  sx?: SxProps<Theme>
}

/** The day-of-month input of a recurring template's cap edit; same reuse as {@link
 * RecurringTemplateAmountField}. */
export function RecurringTemplateDayOfMonthField({ value, onChange, sx }: DayOfMonthFieldProps) {
  return (
    <TextField
      size="small"
      type="number"
      label="Day of month"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      slotProps={{ htmlInput: { min: 1, max: 31 } }}
      sx={sx}
    />
  )
}

interface RecurringTemplateEditFieldsProps {
  amount: string
  dayOfMonth: string
  onAmountChange: (amount: string) => void
  onDayOfMonthChange: (dayOfMonth: string) => void
}

/**
 * The amount/day-of-month inputs together, full width, for the mobile edit dialog (F021 row-edit
 * pattern): the same two fields the table row shows inline (narrower) on tablet/desktop, stacked.
 * The parent supplies the surrounding `FormGrid` and buttons.
 */
export function RecurringTemplateEditFields({
  amount,
  dayOfMonth,
  onAmountChange,
  onDayOfMonthChange,
}: RecurringTemplateEditFieldsProps) {
  return (
    <>
      <RecurringTemplateAmountField value={amount} onChange={onAmountChange} autoFocus />
      <RecurringTemplateDayOfMonthField value={dayOfMonth} onChange={onDayOfMonthChange} />
    </>
  )
}
