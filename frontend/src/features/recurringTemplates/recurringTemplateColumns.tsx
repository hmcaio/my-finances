import type { ReactNode } from 'react'
import type { TFunction } from 'i18next'
import { Box, Chip, Paper, Typography } from '@mui/material'
import type { RecurringTemplate } from '../../api/recurringTemplates/recurringTemplates'
import type { ResponsiveColumn } from '../../components/table/ResponsiveTable'
import type { Locale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import {
  RecurringTemplateAmountField,
  RecurringTemplateDayOfMonthField,
} from './RecurringTemplateEditFields'

type RecurringT = TFunction<['recurringTemplates', 'common']>

/** What a read-only row (table cell or mobile card) needs: labels, locale and the name lookups. */
export interface RecurringTemplateDisplayContext {
  t: RecurringT
  locale: Locale
  categoryName: (id: string) => string
  accountName: (id: string) => string
}

/** The amount/day-of-month draft of the row being edited inline (tablet/desktop). */
export interface RecurringTemplateInlineEdit {
  rowId: string
  amount: string
  dayOfMonth: string
  onAmountChange: (amount: string) => void
  onDayOfMonthChange: (dayOfMonth: string) => void
}

export interface RecurringTemplateColumnsContext extends RecurringTemplateDisplayContext {
  /** `null` when no row is being edited inline, and on mobile, where the edit opens a dialog. */
  inlineEdit: RecurringTemplateInlineEdit | null
}

function statusChip(template: RecurringTemplate, t: RecurringT) {
  return (
    <Chip
      size="small"
      label={
        template.active
          ? t('recurringTemplates:statusActive')
          : t('recurringTemplates:statusStopped')
      }
      color={template.active ? 'success' : 'default'}
    />
  )
}

/** The templates table's columns. Only Category is `tabletPriority: 'low'` (see the page doc). */
export function buildRecurringTemplateColumns(
  ctx: RecurringTemplateColumnsContext,
): ResponsiveColumn<RecurringTemplate>[] {
  const { t, locale, categoryName, accountName, inlineEdit } = ctx
  return [
    {
      key: 'description',
      header: t('recurringTemplates:columns.description'),
      render: (template) => template.description,
    },
    {
      key: 'category',
      header: t('recurringTemplates:columns.category'),
      tabletPriority: 'low',
      render: (template) => categoryName(template.categoryId),
    },
    {
      key: 'account',
      header: t('recurringTemplates:columns.account'),
      render: (template) => accountName(template.accountId),
    },
    {
      key: 'amount',
      header: t('recurringTemplates:columns.amount'),
      align: 'right',
      render: (template) =>
        inlineEdit?.rowId === template.id ? (
          <RecurringTemplateAmountField
            value={inlineEdit.amount}
            onChange={inlineEdit.onAmountChange}
            autoFocus
            sx={{ width: 110 }}
          />
        ) : template.currentAmount !== null ? (
          formatMoney(template.currentAmount, locale)
        ) : (
          <Typography color="text.secondary" component="span">
            {t('recurringTemplates:noCapYet')}
          </Typography>
        ),
    },
    {
      key: 'dayOfMonth',
      header: t('recurringTemplates:columns.dayOfMonth'),
      align: 'right',
      render: (template) =>
        inlineEdit?.rowId === template.id ? (
          <RecurringTemplateDayOfMonthField
            value={inlineEdit.dayOfMonth}
            onChange={inlineEdit.onDayOfMonthChange}
            sx={{ width: 80 }}
          />
        ) : (
          (template.currentDayOfMonth ?? '—')
        ),
    },
    {
      key: 'status',
      header: t('recurringTemplates:columns.status'),
      render: (template) => statusChip(template, t),
    },
  ]
}

/**
 * The mobile card leads with what/how much, like Transactions: description and status up top, then
 * category/account, then amount and day of month. The edit happens in a dialog, so the card stays
 * plain text.
 */
export function renderRecurringTemplateCard(
  { t, locale, categoryName, accountName }: RecurringTemplateDisplayContext,
  template: RecurringTemplate,
  actions: ReactNode,
) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
        <Typography variant="subtitle1" component="div" sx={{ overflowWrap: 'anywhere' }}>
          {template.description}
        </Typography>
        {statusChip(template, t)}
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
        {categoryName(template.categoryId)} · {accountName(template.accountId)}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {template.currentAmount !== null
          ? formatMoney(template.currentAmount, locale)
          : t('recurringTemplates:noCapYet')}{' '}
        · {t('recurringTemplates:dayPrefix')} {template.currentDayOfMonth ?? '—'}
      </Typography>
      {actions && <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 1 }}>{actions}</Box>}
    </Paper>
  )
}
