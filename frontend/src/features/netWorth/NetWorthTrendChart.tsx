import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Skeleton, ToggleButton, ToggleButtonGroup, Typography, useTheme } from '@mui/material'
import type { NetWorthGranularity, NetWorthPoint } from '../../api/netWorth/netWorth'
import { useNetWorthTrend } from '../../api/netWorth/netWorthQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useLocale, type Locale } from '../../hooks/locale/useLocale'
import { formatMoney, formatNumber } from '../../i18n/format'

const WIDTH = 640
const HEIGHT = 260
const MARGIN = { top: 16, right: 16, bottom: 32, left: 72 }
const DAY_MS = 24 * 60 * 60 * 1000
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

/** Rounds a raw axis range up to a "nice" tick step (1, 2, 5 x 10^n). */
function niceStep(range: number, ticks: number): number {
  const rough = range / ticks
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const normalized = rough / magnitude
  const factor = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return factor * magnitude
}

/** `YYYY-MM-DD` as a day count, timezone-free (parsed as UTC on purpose: only differences matter). */
function dayNumber(date: string): number {
  return Date.parse(`${date}T00:00:00Z`) / DAY_MS
}

/** The x-axis label of a point: a month for the monthly series, the full date otherwise. */
function dateLabel(date: string, granularity: NetWorthGranularity): string {
  return granularity === 'MONTH' ? date.slice(0, 7) : date
}

/**
 * The net worth line over time (F010 spec, PRD S5.9) with a monthly / per-change toggle. Monthly
 * points are month-ends (the current month at today) joined by straight segments; change-date
 * points are drawn as a step line, since net worth holds its value until the next change. Points
 * are placed proportionally to their dates, so an inflection sits where the transaction, transfer
 * or snapshot really is. A prop-less widget that fetches its own data on mount (frontend
 * `CLAUDE.md`, "Pages that embed other pages' widgets"), so F012's dashboard can drop it in. The
 * assets / investments / liabilities split is in each point's tooltip and the latest one below the
 * chart. Hand-drawn SVG, no chart dependency.
 */
export function NetWorthTrendChart() {
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
        <Skeleton variant="rounded" height={HEIGHT} />
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
          lineColor={theme.palette.primary.main}
          axisColor={theme.palette.text.secondary}
          gridColor={theme.palette.divider}
          t={t as (key: string, options?: Record<string, unknown>) => string}
          locale={locale}
        />
      )}
    </Box>
  )
}

interface ChartProps {
  points: NetWorthPoint[]
  granularity: NetWorthGranularity
  lineColor: string
  axisColor: string
  gridColor: string
  t: (key: string, options?: Record<string, unknown>) => string
  locale: Locale
}

function Chart({ points, granularity, lineColor, axisColor, gridColor, t, locale }: ChartProps) {
  const values = points.map((p) => p.netWorth)
  const max = Math.max(0, ...values)
  const min = Math.min(0, ...values)
  const step = niceStep(max - min || 1, 4)
  const top = Math.ceil(max / step) * step
  const bottom = Math.floor(min / step) * step
  const innerWidth = WIDTH - MARGIN.left - MARGIN.right
  const innerHeight = HEIGHT - MARGIN.top - MARGIN.bottom
  const y = (v: number) => MARGIN.top + ((top - v) / (top - bottom || 1)) * innerHeight

  const firstDay = dayNumber(points[0].date)
  const span = dayNumber(points[points.length - 1].date) - firstDay
  // A single point (or several on one day) sits in the middle.
  const x = (date: string) =>
    MARGIN.left + (span === 0 ? innerWidth / 2 : ((dayNumber(date) - firstDay) / span) * innerWidth)

  const ticks: number[] = []
  for (let t = bottom; t <= top + step / 2; t += step) ticks.push(t)

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
      <Box
        component="svg"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={granularity === 'MONTH' ? t('chartAriaMonth') : t('chartAriaChange')}
        sx={{ width: '100%', maxWidth: WIDTH, height: 'auto', display: 'block' }}
      >
        {ticks.map((tick) => (
          <g key={tick}>
            <line
              x1={MARGIN.left}
              x2={WIDTH - MARGIN.right}
              y1={y(tick)}
              y2={y(tick)}
              stroke={gridColor}
            />
            <text
              x={MARGIN.left - 8}
              y={y(tick)}
              textAnchor="end"
              dominantBaseline="middle"
              fontSize="11"
              fill={axisColor}
            >
              {formatNumber(tick, locale, 0)}
            </text>
          </g>
        ))}
        {labelIndexes.map((index) => (
          <text
            key={points[index].date}
            x={x(points[index].date)}
            y={HEIGHT - MARGIN.bottom + 18}
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
      </Box>
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
