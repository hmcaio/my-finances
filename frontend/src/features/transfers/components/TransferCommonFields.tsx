import { useTranslation } from 'react-i18next'
import { TextField } from '@mui/material'
import type { FormState } from '../lib/transferFormState'

interface TransferCommonFieldsProps {
  form: FormState
  patch: (changes: Partial<FormState>) => void
  /** Dialog mode: the long fields span the whole `FormGrid` row (`gridColumn: '1 / -1'`). */
  wide?: { gridColumn: string }
}

/** The date, description and notes every transfer has, in either mode. */
export function TransferCommonFields({ form, patch, wide }: TransferCommonFieldsProps) {
  const { t } = useTranslation(['transfers'])
  return (
    <>
      <TextField
        label={t('transfers:form.dateLabel')}
        type="date"
        size="small"
        value={form.date}
        onChange={(e) => patch({ date: e.target.value })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        label={t('transfers:form.descriptionLabel')}
        size="small"
        required
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
