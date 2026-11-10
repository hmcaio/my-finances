import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { Box, Skeleton, ToggleButton, ToggleButtonGroup, Typography, useTheme } from '@mui/material'
import type { NetWorthGranularity, NetWorthPoint } from '../../api/netWorth/netWorth'
import { useNetWorthTrend } from '../../api/netWorth/netWorthQueries'
import { CHART_HEIGHT, ChartSvg, YGrid } from '../../components/charts/ChartSvg'
import { createDayScale, createYScale, fitChartSize } from '../../components/charts/chartMath'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useElementWidth } from '../../hooks/useElementWidth'
import { useLocale, type Locale } from '../../providers/locale/useLocale'
import { formatMoney, formatNumber } from '../../i18n/format'

const MARGIN = { top: 16, right: 16, bottom: 32, left: 72 }
const X_LABELS = 4

/** Screen-reader-only text, the same "Loading…" marker `DataTableBody`'s skeleton carries. */
const VISUALLY_HIDDEN = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

/** The x-axis label of a point: a month for the monthly series, the full date otherwise. */
function dateLabel(date: string, granularity: NetWorthGranularity): string {
  return granularity === 'MONTH' ? date.slice(0, 7) : date
}

/**
 * The net worth line over time (F010 spec, PRD S5.9) with a monthly / per-change toggle. Monthly
 * points are month-ends (the current month at today) joined by straight segments; change-date
 * points are drawn as a step line, since net worth holds its value until the next change. Points
 * are placed proportionally to their dates, so an inflection sits where the transaction, transfer
 * or snapshot really is. A prop-less widget that fetches its own data on mount (`docs/conventions/frontend-data-loading.md`,
 * embedded widgets), so F012's dashboard can drop it in. The
 * assets / investments / liabilities split is in each point's tooltip and the latest one below the
 * chart. Hand-drawn SVG, no chart dependency.
 *
 * The chart fills the width it is given (F040): it measures its container and draws at that width
 * (`fitChartSize`, a 1:1 viewBox, so text and strokes keep their size) instead of scaling a fixed
 * 640 wide frame, which on the dashboard's two-column card left it short and capped at 640. The
 * measured box is a wrapper mounted before the data loads; where the width cannot be measured the
 * default frame is used.
 */
export function NetWorthTrendChart() {
  const [ref, width] = useElementWidth<HTMLDivElement>()
  return (
    <Box ref={ref}>
      <NetWorthTrend availableWidth={width} />
    </Box>
  )
}

function NetWorthTrend({ availableWidth }: { availableWidth: number | null }) {
  const { t } = useTranslation('netWorth')
  const { locale } = useLocale()
  const theme = useTheme()
  const [granularity, setGranularity] = useState<NetWorthGranularity>('MONTH')
  const trendQuery = useNetWorthTrend({ granularity })
  const points = trendQuery.data
  const { loading, loadError, reload } = useQueryState(trendQuery)
  const showSkeleton = useDelayedFlag(loading)

  const toggle = (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={granularity}
      aria-label={t('granularityAria')}
      onChange={(_event, next: NetWorthGranularity | null) => {
        if (next) setGranularity(next)
      }}
      sx={{ mb: 1 }}
    >
      <ToggleButton value="MONTH">{t('monthlyOption')}</ToggleButton>
      <ToggleButton value="CHANGE_DATE">{t('everyChangeOption')}</ToggleButton>
    </ToggleButtonGroup>
  )

  if (loadError) {
    return (
      <Box>
        {toggle}
        <LoadFailedNotice message={loadError} onRetry={reload} />
      </Box>
    )
  }
  if (showSkeleton) {
    return (
      <Box role="status" aria-label={t('loadingAria')}>
        <Box component="span" sx={VISUALLY_HIDDEN}>
          {t('loading')}
        </Box>
        <Skeleton variant="rounded" height={CHART_HEIGHT} />
      </Box>
    )
  }
  if (!points) return null

  return (
    <Box sx={fadeInSx}>
      {toggle}
      {points.length === 0 ? (
        <Typography color="text.secondary">{t('emptyMessage')}</Typography>
      ) : (
        <Chart
          points={points}
          granularity={granularity}
          availableWidth={availableWidth}
          lineColor={theme.palette.primary.main}
          axisColor={theme.palette.text.secondary}
          gridColor={theme.palette.divider}
          t={t}
          locale={locale}
        />
      )}
    </Box>
  )
}

interface ChartProps {
  points: NetWorthPoint[]
  granularity: NetWorthGranularity
  /** The container's measured width, or `null` to use the default frame. */
  availableWidth: number | null
  lineColor: string
  axisColor: string
  gridColor: string
  t: TFunction<'netWorth'>
  locale: Locale
}

function Chart({
  points,
  granularity,
  availableWidth,
  lineColor,
  axisColor,
  gridColor,
  t,
  locale,
}: ChartProps) {
  const { width: chartWidth, height: chartHeight } = fitChartSize(availableWidth)
  const values = points.map((p) => p.netWorth)
  const max = Math.max(0, ...values)
  const min = Math.min(0, ...values)
  const { ticks, y } = createYScale({ min, max, height: chartHeight, margin: MARGIN })
  // A single point (or several on one day) sits in the middle.
  const x = createDayScale(
    points[0].date,
    points[points.length - 1].date,
    MARGIN.left,
    chartWidth - MARGIN.left - MARGIN.right,
  )

  const labelIndexes = [
    ...new Set(
      Array.from({ length: Math.min(X_LABELS, points.length) }, (_, i) =>
        points.length === 1 ? 0 : Math.round((i * (points.length - 1)) / (X_LABELS - 1)),
      ),
    ),
  ]

  // Step line for change dates (value holds until the next change), straight segments for months.
  const path = points
    .map((p, index) => {
      if (index === 0) return `M${x(p.date)},${y(p.netWorth)}`
      if (granularity === 'CHANGE_DATE') {
        return `L${x(p.date)},${y(points[index - 1].netWorth)} L${x(p.date)},${y(p.netWorth)}`
      }
      return `L${x(p.date)},${y(p.netWorth)}`
    })
    .join(' ')
  const latest = points[points.length - 1]

  return (
    <Box>
      <ChartSvg
        label={granularity === 'MONTH' ? t('chartAriaMonth') : t('chartAriaChange')}
        width={chartWidth}
        height={chartHeight}
        fillWidth={availableWidth !== null}
      >
        <YGrid
          ticks={ticks}
          width={chartWidth}
          y={y}
          marginLeft={MARGIN.left}
          marginRight={MARGIN.right}
          gridColor={gridColor}
          axisColor={axisColor}
          formatTick={(tick) => formatNumber(tick, locale, 0)}
        />
        {labelIndexes.map((index) => (
          <text
            key={points[index].date}
            x={x(points[index].date)}
            y={chartHeight - MARGIN.bottom + 18}
            textAnchor={
              points.length === 1
                ? 'middle'
                : index === 0
                  ? 'start'
                  : index === points.length - 1
                    ? 'end'
                    : 'middle'
            }
            fontSize="11"
            fill={axisColor}
          >
            {dateLabel(points[index].date, granularity)}
          </text>
        ))}
        <path d={path} fill="none" stroke={lineColor} strokeWidth={2} />
        {points.map((p) => (
          <circle key={p.date} cx={x(p.date)} cy={y(p.netWorth)} r={3.5} fill={lineColor}>
            <title>
              {t('tooltip', {
                date: p.date,
                netWorth: formatMoney(p.netWorth, locale),
                assets: formatMoney(p.assets, locale),
                investments: formatMoney(p.investments, locale),
                liabilities: formatMoney(p.liabilities, locale),
              })}
            </title>
          </circle>
        ))}
      </ChartSvg>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
        {t('summary', {
          date: latest.date,
          netWorth: formatMoney(latest.netWorth, locale),
          assets: formatMoney(latest.assets, locale),
          investments: formatMoney(latest.investments, locale),
          liabilities: formatMoney(latest.liabilities, locale),
        })}
      </Typography>
    </Box>
  )
}
