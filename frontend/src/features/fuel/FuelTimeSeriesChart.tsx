import { Box, Typography } from '@mui/material'

const WIDTH = 640
const HEIGHT = 260
const MARGIN = { top: 16, right: 16, bottom: 32, left: 64 }
const DAY_MS = 24 * 60 * 60 * 1000
const X_LABELS = 4

/** Rounds a raw axis range up to a "nice" tick step (1, 2, 5 x 10^n) - same helper as
 * `NetWorthTrendChart`/`ValueSeriesChart`. */
function niceStep(range: number, ticks: number): number {
  const rough = range / ticks
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const normalized = rough / magnitude
  const factor = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return factor * magnitude
}

/** `YYYY-MM-DD` as a day count, timezone-free (parsed as UTC on purpose: only differences
 * matter) - same helper as `NetWorthTrendChart`. */
function dayNumber(date: string): number {
  return Date.parse(`${date}T00:00:00Z`) / DAY_MS
}

export interface FuelSeriesPoint {
  date: string
  value: number
  /** Extra context for the point's tooltip (e.g. the transaction's description). */
  tooltip?: string
}

export interface FuelSeries {
  label: string
  color: string
  points: FuelSeriesPoint[]
}

interface FuelTimeSeriesChartProps {
  series: FuelSeries[]
  /** Accessible name of the chart. */
  label: string
  /** How a raw value is formatted for the axis/tooltip (e.g. 2 decimals for money, 3 for L/km). */
  formatValue?: (value: number) => string
  /** Shown when every series has no points. */
  emptyMessage?: string
  /** Axis label/gridline colors - the caller passes its theme's `text.secondary`/`divider` (same
   * convention as `NetWorthTrendChart`/`ValueSeriesChart`). */
  axisColor: string
  gridColor: string
  /** Forces 0 into the axis range (default `true`). A ratio series that is never negative (km/L,
   * spend/km, price/liter) sets this `false` so the axis tracks its actual range instead of always
   * including a baseline it can never reach, which otherwise flattens its real variation. */
  zeroBaseline?: boolean
}

/**
 * A generic per-fill time-series line chart (F024 spec's Fuel page): raw points over time, no
 * date aggregation - one line per {@link FuelSeries}, with a legend when there's more than one
 * (the price/liter-by-fuel-type chart; km/L and spend/km each pass a single series). Hand-drawn
 * SVG, no chart dependency, same shape as `NetWorthTrendChart`/`ValueSeriesChart`.
 */
export function FuelTimeSeriesChart({
  series,
  label,
  formatValue = (v) => v.toFixed(2),
  emptyMessage = 'No data for this vehicle yet.',
  axisColor,
  gridColor,
  zeroBaseline = true,
}: FuelTimeSeriesChartProps) {
  const nonEmptySeries = series.filter((s) => s.points.length > 0)
  const allPoints = nonEmptySeries.flatMap((s) => s.points)

  if (allPoints.length === 0) {
    return <Typography color="text.secondary">{emptyMessage}</Typography>
  }

  const values = allPoints.map((p) => p.value)
  const baseline = zeroBaseline ? [0] : []
  const max = Math.max(...baseline, ...values)
  const min = Math.min(...baseline, ...values)
  const step = niceStep(max - min || 1, 4)
  const top = Math.ceil(max / step) * step
  const bottom = Math.floor(min / step) * step
  const innerWidth = WIDTH - MARGIN.left - MARGIN.right
  const innerHeight = HEIGHT - MARGIN.top - MARGIN.bottom
  const y = (v: number) => MARGIN.top + ((top - v) / (top - bottom || 1)) * innerHeight

  const days = allPoints.map((p) => dayNumber(p.date))
  const firstDay = Math.min(...days)
  const span = Math.max(...days) - firstDay
  const x = (date: string) =>
    MARGIN.left + (span === 0 ? innerWidth / 2 : ((dayNumber(date) - firstDay) / span) * innerWidth)

  const ticks: number[] = []
  for (let t = bottom; t <= top + step / 2; t += step) ticks.push(t)

  const sortedAllDates = [...new Set(allPoints.map((p) => p.date))].sort()
  const labelIndexes = [
    ...new Set(
      Array.from({ length: Math.min(X_LABELS, sortedAllDates.length) }, (_, i) =>
        sortedAllDates.length === 1
          ? 0
          : Math.round((i * (sortedAllDates.length - 1)) / (X_LABELS - 1)),
      ),
    ),
  ]

  return (
    <Box>
      <Box
        component="svg"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={label}
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
              {formatValue(t)}
            </text>
          </g>
        ))}
        {labelIndexes.map((index) => (
          <text
            key={sortedAllDates[index]}
            x={x(sortedAllDates[index])}
            y={HEIGHT - MARGIN.bottom + 18}
            textAnchor={
              sortedAllDates.length === 1
                ? 'middle'
                : index === 0
                  ? 'start'
                  : index === sortedAllDates.length - 1
                    ? 'end'
                    : 'middle'
            }
            fontSize="11"
            fill={axisColor}
          >
            {sortedAllDates[index]}
          </text>
        ))}
        {nonEmptySeries.map((s) => {
          const sorted = [...s.points].sort((a, b) => (a.date < b.date ? -1 : 1))
          const path = sorted
            .map((p, index) => `${index === 0 ? 'M' : 'L'}${x(p.date)},${y(p.value)}`)
            .join(' ')
          return (
            <g key={s.label}>
              <path d={path} fill="none" stroke={s.color} strokeWidth={2} />
              {sorted.map((p, index) => (
                <circle
                  key={`${s.label}-${p.date}-${index}`}
                  cx={x(p.date)}
                  cy={y(p.value)}
                  r={3.5}
                  fill={s.color}
                >
                  <title>
                    {`${p.date}: ${formatValue(p.value)}${p.tooltip ? ` (${p.tooltip})` : ''}`}
                  </title>
                </circle>
              ))}
            </g>
          )
        })}
      </Box>
      {series.length > 1 && (
        <Box sx={{ display: 'flex', gap: 3, mt: 1, flexWrap: 'wrap' }} aria-hidden="true">
          {series.map((s) => (
            <Typography key={s.label} variant="caption" sx={{ color: s.color }}>
              &#9679; {s.label}
            </Typography>
          ))}
        </Box>
      )}
    </Box>
  )
}
