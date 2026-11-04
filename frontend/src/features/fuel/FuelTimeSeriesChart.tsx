import { useTranslation } from 'react-i18next'
import { Box, Typography } from '@mui/material'
import { CHART_HEIGHT, CHART_WIDTH, ChartSvg, YGrid } from '../../components/charts/ChartSvg'
import { createDayScale, createYScale } from '../../components/charts/chartMath'

const MARGIN = { top: 16, right: 16, bottom: 32, left: 64 }
const X_LABELS = 4

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
  /** How a raw value is formatted for the axis/tooltip (e.g. 2 decimals for money, 3 for L/km).
   * No locale-unaware default (F033/ADR 0027): every caller passes its own, already-locale-aware
   * formatter (`formatMoney`/`formatNumber` from `src/i18n/format.ts`), since this component has
   * no `locale` of its own to fall back on. */
  formatValue: (value: number) => string
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
  formatValue,
  emptyMessage,
  axisColor,
  gridColor,
  zeroBaseline = true,
}: FuelTimeSeriesChartProps) {
  const { t } = useTranslation('fuel')
  const resolvedEmptyMessage = emptyMessage ?? t('chart.defaultEmptyMessage')
  const nonEmptySeries = series.filter((s) => s.points.length > 0)
  const allPoints = nonEmptySeries.flatMap((s) => s.points)

  if (allPoints.length === 0) {
    return <Typography color="text.secondary">{resolvedEmptyMessage}</Typography>
  }

  const values = allPoints.map((p) => p.value)
  const baseline = zeroBaseline ? [0] : []
  const max = Math.max(...baseline, ...values)
  const min = Math.min(...baseline, ...values)
  const { ticks, y } = createYScale({ min, max, height: CHART_HEIGHT, margin: MARGIN })

  const sortedAllDates = [...new Set(allPoints.map((p) => p.date))].sort()
  const x = createDayScale(
    sortedAllDates[0],
    sortedAllDates[sortedAllDates.length - 1],
    MARGIN.left,
    CHART_WIDTH - MARGIN.left - MARGIN.right,
  )
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
      <ChartSvg label={label}>
        <YGrid
          ticks={ticks}
          y={y}
          marginLeft={MARGIN.left}
          marginRight={MARGIN.right}
          gridColor={gridColor}
          axisColor={axisColor}
          formatTick={formatValue}
        />
        {labelIndexes.map((index) => (
          <text
            key={sortedAllDates[index]}
            x={x(sortedAllDates[index])}
            y={CHART_HEIGHT - MARGIN.bottom + 18}
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
      </ChartSvg>
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
