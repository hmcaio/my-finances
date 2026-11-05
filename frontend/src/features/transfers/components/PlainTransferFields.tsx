import { useTranslation } from 'react-i18next'
import { MenuItem, Select, TextField } from '@mui/material'
import type { Account } from '../../../api/accounts/accounts'
import type { FormState } from '../lib/transferFormState'
import { SELECT_MIN_WIDTH } from '../../../components/layout/layoutSx'

interface PlainTransferFieldsProps {
  form: FormState
  patch: (changes: Partial<FormState>) => void
  /** Open, non-INVESTMENT accounts: a plain transfer never touches an INVESTMENT account. */
  cashOptions: Account[]
}

/** A plain transfer's From/To account pickers and amount. */
export function PlainTransferFields({ form, patch, cashOptions }: PlainTransferFieldsProps) {
  const { t } = useTranslation(['transfers'])
  return (
    <>
      <Select
        size="small"
        displayEmpty
        value={form.fromAccountId}
        onChange={(e) => patch({ fromAccountId: e.target.value })}
        aria-label={t('transfers:form.fromAccountLabel')}
        sx={SELECT_MIN_WIDTH}
      >
        <MenuItem value="" disabled>
          {t('transfers:form.fromAccountLabel')}
        </MenuItem>
        {cashOptions.map((a) => (
          <MenuItem key={a.id} value={a.id}>
            {a.name}
          </MenuItem>
        ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={form.toAccountId}
        onChange={(e) => patch({ toAccountId: e.target.value })}
        aria-label={t('transfers:form.toAccountLabel')}
        sx={SELECT_MIN_WIDTH}
      >
        <MenuItem value="" disabled>
          {t('transfers:form.toAccountLabel')}
        </MenuItem>
        {cashOptions
          .filter((a) => a.id !== form.fromAccountId)
          .map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
      </Select>
      <TextField
        label={t('transfers:form.amountLabel')}
        type="number"
        size="small"
        value={form.amount}
        onChange={(e) => patch({ amount: e.target.value })}
        slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
      />
    </>
  )
}
