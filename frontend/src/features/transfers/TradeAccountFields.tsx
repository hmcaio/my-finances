import { useTranslation } from 'react-i18next'
import { MenuItem, Select, TextField } from '@mui/material'
import type { Account } from '../../api/accounts/accounts'
import type { FormState } from './transferFormState'

interface TradeAccountFieldsProps {
  form: FormState
  patch: (changes: Partial<FormState>) => void
  /** Open, non-INVESTMENT accounts. */
  cashOptions: Account[]
  /** Open INVESTMENT accounts. */
  investmentAccounts: Account[]
  /** Changing the investment account also drops every line's product pick. */
  onInvestmentAccountChange: (id: string) => void
}

/** A trade confirmation's cash and INVESTMENT account pickers and the shared taxes field. */
export function TradeAccountFields({
  form,
  patch,
  cashOptions,
  investmentAccounts,
  onInvestmentAccountChange,
}: TradeAccountFieldsProps) {
  const { t } = useTranslation(['transfers'])
  return (
    <>
      <Select
        size="small"
        displayEmpty
        value={form.cashAccountId}
        onChange={(e) => patch({ cashAccountId: e.target.value })}
        aria-label={t('transfers:form.cashAccountLabel')}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          {t('transfers:form.cashAccountLabel')}
        </MenuItem>
        {cashOptions
          .filter((a) => a.id !== form.investmentAccountId)
          .map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={form.investmentAccountId}
        onChange={(e) => onInvestmentAccountChange(e.target.value)}
        aria-label={t('transfers:form.investmentAccountLabel')}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="" disabled>
          {t('transfers:form.investmentAccountLabel')}
        </MenuItem>
        {investmentAccounts
          .filter((a) => a.id !== form.cashAccountId)
          .map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
      </Select>
      <TextField
        label={t('transfers:form.taxesLabel')}
        type="number"
        size="small"
        value={form.taxes}
        onChange={(e) => patch({ taxes: e.target.value })}
        slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
      />
    </>
  )
}
