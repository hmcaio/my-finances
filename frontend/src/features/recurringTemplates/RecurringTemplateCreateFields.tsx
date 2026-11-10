import type { Dispatch, SetStateAction } from 'react'
import { useTranslation } from 'react-i18next'
import { MenuItem, Select, TextField } from '@mui/material'
import type { Account } from '../../api/accounts/accounts'
import type { Category } from '../../api/categories/categories'
import { translateSeedName } from '../../i18n/seedNames'
import type { RecurringTemplateCreateForm } from './recurringTemplateEdit'
import { FORM_GRID_FULL_ROW } from '../../components/layout/layoutSx'

interface Props {
  form: RecurringTemplateCreateForm
  setForm: Dispatch<SetStateAction<RecurringTemplateCreateForm>>
  /** Already narrowed to what a template may use (no fuel category). */
  categories: Category[]
  /** Already narrowed to what a template may use (no investment account). */
  accounts: Account[]
}

/** The create dialog's fields (the dialog shell and its grid come from `AddFormDialog`). */
export function RecurringTemplateCreateFields({ form, setForm, categories, accounts }: Props) {
  const { t } = useTranslation(['recurringTemplates', 'common'])
  return (
    <>
      <Select
        size="small"
        displayEmpty
        required
        autoFocus
        value={form.categoryId}
        onChange={(e) => setForm((prev) => ({ ...prev, categoryId: e.target.value }))}
        aria-label={t('recurringTemplates:categoryLabel')}
      >
        <MenuItem value="" disabled>
          {`${t('recurringTemplates:categoryLabel')} *`}
        </MenuItem>
        {categories.map((c) => (
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
        onChange={(e) => setForm((prev) => ({ ...prev, accountId: e.target.value }))}
        aria-label={t('recurringTemplates:accountLabel')}
      >
        <MenuItem value="" disabled>
          {`${t('recurringTemplates:accountLabel')} *`}
        </MenuItem>
        {accounts.map((a) => (
          <MenuItem key={a.id} value={a.id}>
            {a.name}
          </MenuItem>
        ))}
      </Select>
      <TextField
        label={t('recurringTemplates:descriptionLabel')}
        size="small"
        required
        value={form.description}
        onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
        slotProps={{ htmlInput: { maxLength: 150 } }}
        sx={FORM_GRID_FULL_ROW}
      />
      <TextField
        label={t('recurringTemplates:amountLabel')}
        type="number"
        size="small"
        required
        value={form.amount}
        onChange={(e) => setForm((prev) => ({ ...prev, amount: e.target.value }))}
        slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
      />
      <TextField
        label={t('recurringTemplates:dayOfMonthLabel')}
        type="number"
        size="small"
        required
        value={form.dayOfMonth}
        onChange={(e) => setForm((prev) => ({ ...prev, dayOfMonth: e.target.value }))}
        slotProps={{ htmlInput: { min: 1, max: 31 } }}
      />
    </>
  )
}
