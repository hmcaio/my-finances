import { useTranslation } from 'react-i18next'
import { Box, Typography } from '@mui/material'
import type { LedgerSummary as LedgerSummaryData } from '../../api/ledger/ledger'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'

interface LedgerSummaryProps {
  /** `undefined` until the first Ledger response has arrived. */
  summary: LedgerSummaryData | undefined
}

/**
 * Income, expense and net for the whole filtered scope (F037), as three labelled figures. They
 * come from the backend over every match, not from the visible rows, so they stay right when the
 * list is truncated. Transfers are excluded (PRD S5.5). Wraps on a phone instead of scrolling.
 */
export function LedgerSummary({ summary }: LedgerSummaryProps) {
  const { t } = useTranslation('ledger')
  const { locale } = useLocale()
  const figure = (value: number | undefined) =>
    value === undefined ? '—' : formatMoney(value, locale)

  const items = [
    { key: 'income', label: t('summary.income'), value: summary?.income, color: 'success.main' },
    { key: 'expense', label: t('summary.expense'), value: summary?.expense, color: 'error.main' },
    { key: 'net', label: t('summary.net'), value: summary?.net, color: 'text.primary' },
  ]

  return (
    <Box
      role="group"
      aria-label={t('summary.aria')}
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        columnGap: { xs: 2, sm: 4 },
        rowGap: 0.5,
        mb: 2,
      }}
    >
      {items.map((item) => (
        <Box key={item.key} sx={{ minWidth: 0 }}>
          <Typography
            variant="overline"
            color="text.secondary"
            component="div"
            sx={{ lineHeight: 1.5 }}
          >
            {item.label}
          </Typography>
          <Typography
            variant="h6"
            component="div"
            sx={{ color: item.color, fontWeight: 600, overflowWrap: 'anywhere' }}
          >
            {figure(item.value)}
          </Typography>
        </Box>
      ))}
    </Box>
  )
}
