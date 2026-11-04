import type { TFunction } from 'i18next'
import { Chip, Link as MuiLink } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import type { Account } from '../../api/accounts/accounts'
import type { ResponsiveColumn } from '../../components/table/ResponsiveTable'
import type { Locale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { InstitutionSelect } from '../institutions/InstitutionSelect'
import { AccountNameField } from './AccountEditFields'
import { accountTypeLabel } from './accountTypes'

/** The name/institution draft of the row being renamed inline (tablet/desktop). */
export interface AccountInlineEdit {
  rowId: string
  name: string
  institutionId: string | undefined
  onNameChange: (name: string) => void
  onInstitutionChange: (institutionId: string) => void
  /** Enter in the name field. */
  onSave: (id: string) => void
  /** Escape in the name field. */
  onCancel: () => void
}

export interface AccountColumnsContext {
  t: TFunction<['accounts', 'common']>
  locale: Locale
  institutionName: (id: string) => string
  /** `null` when no row is edited inline, and on mobile, where the edit opens a dialog. */
  inlineEdit: AccountInlineEdit | null
}

/**
 * The accounts table's columns. Name and Institution lead the mobile card (`primary` /
 * `secondary`); only Type is `tabletPriority: 'low'`.
 */
export function buildAccountColumns({
  t,
  locale,
  institutionName,
  inlineEdit,
}: AccountColumnsContext): ResponsiveColumn<Account>[] {
  return [
    {
      key: 'name',
      header: t('accounts:columns.name'),
      role: 'primary',
      render: (account) =>
        inlineEdit?.rowId === account.id ? (
          <AccountNameField
            value={inlineEdit.name}
            onChange={inlineEdit.onNameChange}
            autoFocus
            onKeyDown={(e) => {
              if (e.key === 'Enter') inlineEdit.onSave(account.id)
              if (e.key === 'Escape') inlineEdit.onCancel()
            }}
          />
        ) : (
          <MuiLink component={RouterLink} to={`/accounts/${account.id}`} underline="hover">
            {account.name}
          </MuiLink>
        ),
    },
    {
      key: 'institution',
      header: t('accounts:columns.institution'),
      role: 'secondary',
      render: (account) =>
        inlineEdit?.rowId === account.id ? (
          <InstitutionSelect
            value={inlineEdit.institutionId}
            onChange={inlineEdit.onInstitutionChange}
          />
        ) : (
          institutionName(account.institutionId)
        ),
    },
    {
      key: 'type',
      header: t('accounts:columns.type'),
      role: 'secondary',
      tabletPriority: 'low',
      render: (account) => accountTypeLabel(account.type, t),
    },
    {
      key: 'balance',
      header: t('accounts:columns.balance'),
      align: 'right',
      render: (account) => formatMoney(account.balance, locale),
    },
    {
      key: 'status',
      header: t('accounts:columns.status'),
      render: (account) =>
        account.closed ? (
          <Chip label={t('accounts:status.closed')} size="small" />
        ) : (
          <Chip label={t('accounts:status.open')} size="small" color="success" />
        ),
    },
  ]
}
