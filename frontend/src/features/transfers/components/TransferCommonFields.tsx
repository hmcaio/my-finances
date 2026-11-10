import type { Ref } from 'react'
import { useTranslation } from 'react-i18next'
import { TextField } from '@mui/material'
import type { FormState } from '../lib/transferFormState'

interface TransferCommonFieldsProps {
  form: FormState
  patch: (changes: Partial<FormState>) => void
  /** Dialog mode: the long fields span the whole `FormGrid` row (`gridColumn: '1 / -1'`). */
  wide?: { gridColumn: string }
  /** The description input, for an explicit `focus()` (F040: after "Add and new"). */
  descriptionInputRef?: Ref<HTMLInputElement>
  /** Focuses the description when the form mounts: the first field a user must fill. */
  autoFocusDescription?: boolean
}

/** The date, description and notes every transfer has, in either mode. */
export function TransferCommonFields({
  form,
  patch,
  wide,
  descriptionInputRef,
  autoFocusDescription,
}: TransferCommonFieldsProps) {
  const { t } = useTranslation(['transfers'])
  return (
    <>
      <TextField
        label={t('transfers:form.dateLabel')}
        type="date"
        size="small"
        required
        value={form.date}
        onChange={(e) => patch({ date: e.target.value })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        label={t('transfers:form.descriptionLabel')}
        size="small"
        required
        autoFocus={autoFocusDescription}
        inputRef={descriptionInputRef}
        value={form.description}
        onChange={(e) => patch({ description: e.target.value })}
        slotProps={{ htmlInput: { maxLength: 150 } }}
        sx={wide}
      />
      <TextField
        label={t('transfers:form.additionalNotesLabel')}
        size="small"
        value={form.additionalNotes}
        onChange={(e) => patch({ additionalNotes: e.target.value })}
        slotProps={{ htmlInput: { maxLength: 500 } }}
        sx={wide}
      />
    </>
  )
}
