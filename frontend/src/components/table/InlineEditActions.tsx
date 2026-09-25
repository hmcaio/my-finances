import { IconButton } from '@mui/material'
import EditIcon from '@mui/icons-material/Edit'
import CheckIcon from '@mui/icons-material/Check'
import CloseIcon from '@mui/icons-material/Close'

interface InlineEditActionsProps {
  editing: boolean
  onEdit: () => void
  onSave: () => void
  onCancel: () => void
  editLabel: string
  saveLabel?: string
  saving?: boolean
}

/**
 * The pencil → check/✕ icon-button trio every inline-edit table row uses, previously copy-pasted
 * into each settings-style page with only the aria-labels differing. `editLabel` is required since
 * it varies per call site ("Rename", "Edit", "Edit cap", "Edit amount and day" - matched to what
 * each row is editing); `saveLabel` defaults to `'Save'` (Budgets/RecurringTemplates pass `'Save
 * cap'`). `saving`, when `true`, disables Save/Cancel while a request is in flight - Categories/
 * PaymentMethods/Accounts never pass it since their inline save has no such in-flight state.
 */
export function InlineEditActions({
  editing,
  onEdit,
  onSave,
  onCancel,
  editLabel,
  saveLabel = 'Save',
  saving = false,
}: InlineEditActionsProps) {
  if (editing) {
    return (
      <>
        <IconButton size="small" aria-label={saveLabel} disabled={saving} onClick={onSave}>
          <CheckIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" aria-label="Cancel" disabled={saving} onClick={onCancel}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </>
    )
  }
  return (
    <IconButton size="small" aria-label={editLabel} onClick={onEdit}>
      <EditIcon fontSize="small" />
    </IconButton>
  )
}
