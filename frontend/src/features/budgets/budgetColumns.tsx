import type { TFunction } from 'i18next'
import { Typography } from '@mui/material'
import type { Budget } from '../../api/budgets/budgets'
import type { ResponsiveColumn } from '../../components/table/ResponsiveTable'
import type { Locale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { BudgetCapField } from './BudgetEditFields'

/** The cap draft of the row being edited inline (tablet/desktop). */
export interface BudgetInlineEdit {
  rowId: string
  cap: string
  onCapChange: (cap: string) => void
}

export interface BudgetColumnsContext {
  t: TFunction<['budgets', 'common']>
  locale: Locale
  categoryName: (id: string) => string
  /** `null` when no row is edited inline, and on mobile, where the edit opens a dialog. */
  inlineEdit: BudgetInlineEdit | null
}

/** The budgets table's columns; the category leads the mobile card. */
export function buildBudgetColumns({
  t,
  locale,
  categoryName,
  inlineEdit,
}: BudgetColumnsContext): ResponsiveColumn<Budget>[] {
  return [
    {
      key: 'category',
      header: t('budgets:columns.category'),
      role: 'primary',
      render: (budget) => categoryName(budget.categoryId),
    },
    {
      key: 'cap',
      header: t('budgets:columns.currentCap'),
      align: 'right',
      render: (budget) =>
        inlineEdit?.rowId === budget.id ? (
          <BudgetCapField value={inlineEdit.cap} onChange={inlineEdit.onCapChange} autoFocus />
        ) : budget.stopped ? (
          <Typography color="text.secondary" component="span">
            {t('budgets:stopped')}
          </Typography>
        ) : budget.currentCap !== null ? (
          formatMoney(budget.currentCap, locale)
        ) : (
          <Typography color="text.secondary" component="span">
            {t('budgets:noCapYet')}
          </Typography>
        ),
    },
    {
      key: 'effectiveFrom',
      header: t('budgets:columns.effectiveFrom'),
      render: (budget) => budget.currentCapEffectiveFrom ?? '—',
    },
  ]
}
