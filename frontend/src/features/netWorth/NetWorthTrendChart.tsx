import { useState } from 'react'
import { Box, Skeleton, ToggleButton, ToggleButtonGroup, Typography, useTheme } from '@mui/material'
import type { NetWorthGranularity, NetWorthPoint } from '../../api/netWorth/netWorth'
import { useNetWorthTrend } from '../../api/netWorth/netWorthQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'

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

function money(value: number): string {
  return value.toFixed(2)
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
      aria-label="Net worth granularity"
      onChange={(_event, next: NetWorthGranularity | null) => {
        if (next) setGranularity(next)
      }}
      sx={{ mb: 1 }}
    >
      <ToggleButton value="MONTH">Monthly</ToggleButton>
      <ToggleButton value="CHANGE_DATE">Every change</ToggleButton>
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
      <Box role="status" aria-label="Loading net worth">
        <Box component="span" sx={VISUALLY_HIDDEN}>
          Loading…
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
        <Typography color="text.secondary">No net worth data for this period.</Typography>
      ) : (
        <Chart
          points={points}
          granularity={granularity}
          lineColor={theme.palette.primary.main}
          axisColor={theme.palette.text.secondary}
          gridColor={theme.palette.divider}
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
}

function Chart({ points, granularity, lineColor, axisColor, gridColor }: ChartProps) {
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
        aria-label={granularity === 'MONTH' ? 'Net worth by month' : 'Net worth at every change'}
        sx={{ width: '100%', maxWidth: WIDTH, height: 'auto', display: 'block' }}
      >
        {ticks.map((t) => (
          <g key={t}>
            <line
              x1={MARGIN.left}
              x2={WIDTH - MARGIN.right}
              y1={y(t)}
              y2={y(t)}
              stroke={gridColor}
            />
            <text
              x={MARGIN.left - 8}
              y={y(t)}
              textAnchor="end"
              dominantBaseline="middle"
              fontSize="11"
              fill={axisColor}
            >
              {t.toLocaleString(undefined, { maximumFractionDigits: 0 })}
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
              {`${p.date}: net worth ${money(p.netWorth)} (assets ${money(p.assets)}, ` +
                `investments ${money(p.investments)}, liabilities ${money(p.liabilities)})`}
            </title>
          </circle>
        ))}
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
        As of {latest.date}: net worth {money(latest.netWorth)} = assets {money(latest.assets)} +
        investments {money(latest.investments)} - liabilities {money(latest.liabilities)}
      </Typography>
    </Box>
  )
}
