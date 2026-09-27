import type { KeyboardEventHandler } from 'react'
import { TextField } from '@mui/material'
import { InstitutionSelect } from '../institutions/InstitutionSelect'

interface AccountNameFieldProps {
  value: string
  onChange: (name: string) => void
  onKeyDown?: KeyboardEventHandler<HTMLDivElement>
  autoFocus?: boolean
}

/** The name input of an account edit: rendered in the table cell inline, and inside the dialog. */
export function AccountNameField({ value, onChange, onKeyDown, autoFocus }: AccountNameFieldProps) {
  return (
    <TextField
      size="small"
      label="Name"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      autoFocus={autoFocus}
      onKeyDown={onKeyDown}
    />
  )
}

interface AccountEditFieldsProps {
  name: string
  institutionId: string | undefined
  onNameChange: (name: string) => void
  onInstitutionChange: (institutionId: string) => void
  onSubmit: () => void
}

/**
 * The account edit inputs for the mobile dialog (F021 row-edit pattern): the same name field and
 * institution picker the table row shows inline on tablet/desktop, stacked. The parent supplies
 * the layout (`FormGrid`) and buttons; Enter in the name field submits, like the inline row.
 */
export function AccountEditFields({
  name,
  institutionId,
  onNameChange,
  onInstitutionChange,
  onSubmit,
}: AccountEditFieldsProps) {
  return (
    <>
      <AccountNameField
        value={name}
        onChange={onNameChange}
        onKeyDown={(e) => {
          if (e.key === 'Enter') onSubmit()
        }}
      />
      <InstitutionSelect value={institutionId} onChange={onInstitutionChange} fullWidth />
    </>
  )
}
