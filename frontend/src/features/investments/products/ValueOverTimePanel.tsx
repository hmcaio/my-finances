import { useTranslation } from 'react-i18next'
import { Box, Skeleton, Typography } from '@mui/material'
import type { ValueSeriesPoint } from '../../../api/investments/products/investmentValueSeries'
import { ResponsiveTable, type ResponsiveColumn } from '../../../components/table/ResponsiveTable'
import type { LoadState } from '../../../hooks/queryState'
import { useLocale } from '../../../providers/locale/useLocale'
import { formatMoney } from '../../../i18n/format'
import { ValueSeriesChart } from './ValueSeriesChart'
import { PageSection } from '../../../components/layout/PageSection'

interface ValueOverTimePanelProps {
  points: ValueSeriesPoint[]
  state: LoadState
}

/** The value/contribution chart with the raw monthly numbers beneath it. */
export function ValueOverTimePanel({ points, state }: ValueOverTimePanelProps) {
  const { t } = useTranslation('investmentProducts')
  const { locale } = useLocale()
  const hasUnits = points.some((p) => p.units !== null)

  const monthlyColumns: ResponsiveColumn<ValueSeriesPoint>[] = [
    {
      key: 'month',
      header: t('detail.monthlyColumns.month'),
      role: 'primary',
      render: (p) => p.month,
    },
    {
      key: 'value',
      header: t('detail.monthlyColumns.value'),
      align: 'right',
      render: (p) => (p.value === null ? '-' : formatMoney(p.value, locale)),
    },
    {
      key: 'contributed',
      header: t('detail.monthlyColumns.contributed'),
      align: 'right',
      render: (p) => formatMoney(p.contributed, locale),
    },
    ...(hasUnits
      ? ([
          {
            key: 'units',
            header: t('detail.monthlyColumns.units'),
            align: 'right',
            render: (p: ValueSeriesPoint) => (p.units === null ? '-' : String(p.units)),
          },
        ] satisfies ResponsiveColumn<ValueSeriesPoint>[])
      : []),
  ]

  return (
    <PageSection sx={{ mb: 3 }}>
      <Typography variant="h6" gutterBottom>
        {t('detail.valueOverTimeTitle')}
      </Typography>
      {state.loading ? (
        <Skeleton variant="rounded" height={200} />
      ) : (
        <>
          <ValueSeriesChart points={points} />
          <Box sx={{ mt: 2 }}>
            <ResponsiveTable
              aria-label={t('detail.monthlyValuesAria')}
              columns={monthlyColumns}
              rows={points}
              getRowKey={(p) => p.month}
              state={state}
              onRetry={state.reload}
              emptyMessage={t('detail.emptyMonthly')}
            />
          </Box>
        </>
      )}
    </PageSection>
  )
}
