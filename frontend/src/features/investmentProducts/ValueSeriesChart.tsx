import { useTranslation } from 'react-i18next'
import { Box, Typography, useTheme } from '@mui/material'
import type { ValueSeriesPoint } from '../../api/investments/investmentValueSeries'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney, formatNumber } from '../../i18n/format'

const WIDTH = 640
const HEIGHT = 260
const MARGIN = { top: 16, right: 16, bottom: 44, left: 64 }

interface ValueSeriesChartProps {
  points: ValueSeriesPoint[]
  /** Accessible name of the chart. */
  label?: string
}

/** Rounds a raw axis maximum up to a "nice" tick step (1, 2, 5 x 10^n). */
function niceStep(range: number, ticks: number): number {
  const rough = range / ticks
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const normalized = rough / magnitude
  const factor = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return factor * magnitude
}

/**
 * A product's monthly value line with contribution bars (F009 spec): the line is the month-end
 * value (a gap where there is no snapshot yet), the bars are that month's buys minus sells - up
 * for net buying, down for net selling - on the same money axis. Hand-drawn SVG, no chart
 * dependency; the raw numbers are in the table beside it, not derived here.
 *
 * Responsive (F021): the `<svg>` was already fluid-width (`width: '100%'`, `maxWidth`, `height:
 * 'auto'`), so it never causes overflow on its own. Two narrow-screen fixes: month labels below
 * `sm` thin out to every other month (first and last always shown) past six points, since 12
 * un-rotated labels overlap at phone width; and the legend row below the chart gets `flexWrap` so
 * its two captions stack instead of forcing width.
 */
export function ValueSeriesChart({ points, label }: ValueSeriesChartProps) {
  const { t } = useTranslation('investmentProducts')
  const { locale } = useLocale()
  const resolvedLabel = label ?? t('chart.defaultLabel')
  const theme = useTheme()
  const isMobile = useIsMobile()
  const lineColor = theme.palette.primary.main
  const barColor = theme.palette.secondary.main
  const axisColor = theme.palette.text.secondary
  const gridColor = theme.palette.divider

  if (points.length === 0) {
    return <Typography color="text.secondary">{t('chart.noData')}</Typography>
  }

  const values = points.flatMap((p) => [p.value ?? 0, p.contributed])
  const max = Math.max(0, ...values)
  const min = Math.min(0, ...values)
  const step = niceStep(max - min || 1, 4)
  const top = Math.ceil(max / step) * step
  const bottom = Math.floor(min / step) * step
  const innerWidth = WIDTH - MARGIN.left - MARGIN.right
  const innerHeight = HEIGHT - MARGIN.top - MARGIN.bottom
  const y = (v: number) => MARGIN.top + ((top - v) / (top - bottom || 1)) * innerHeight
  const slot = innerWidth / points.length
  const x = (index: number) => MARGIN.left + slot * index + slot / 2
  const barWidth = Math.min(28, slot * 0.5)

  const ticks: number[] = []
  for (let t = bottom; t <= top + step / 2; t += step) ticks.push(t)

  // The line breaks at months with no snapshot yet instead of dropping to zero.
  const segments: string[] = []
  let current: string[] = []
  points.forEach((p, index) => {
    if (p.value === null) {
      if (current.length > 0) segments.push(current.join(' '))
      current = []
    } else {
      current.push(`${current.length === 0 ? 'M' : 'L'}${x(index)},${y(p.value)}`)
    }
  })
  if (current.length > 0) segments.push(current.join(' '))

  return (
    <Box>
      <Box
        component="svg"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={resolvedLabel}
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
        {points.map((p, index) => {
          const barTop = y(Math.max(p.contributed, 0))
          const barBottom = y(Math.min(p.contributed, 0))
          return (
            <g key={p.month}>
              {p.contributed !== 0 && (
                <rect
                  x={x(index) - barWidth / 2}
                  y={barTop}
                  width={barWidth}
                  height={Math.max(1, barBottom - barTop)}
                  fill={barColor}
                  opacity={0.55}
                >
                  <title>
                    {t('chart.contributedTooltip', {
                      month: p.month,
                      amount: formatMoney(p.contributed, locale),
                    })}
                  </title>
                </rect>
              )}
              {(!isMobile ||
                points.length <= 6 ||
                index % 2 === 0 ||
                index === points.length - 1) && (
                <text
                  x={x(index)}
                  y={HEIGHT - MARGIN.bottom + 16}
                  textAnchor="middle"
                  fontSize="11"
                  fill={axisColor}
                >
                  {p.month}
                </text>
              )}
            </g>
          )
        })}
        {segments.map((d) => (
          <path key={d} d={d} fill="none" stroke={lineColor} strokeWidth={2} />
        ))}
        {points.map(
          (p, index) =>
            p.value !== null && (
              <circle key={p.month} cx={x(index)} cy={y(p.value)} r={3.5} fill={lineColor}>
                <title>
                  {t('chart.valueTooltip', {
                    month: p.month,
                    amount: formatMoney(p.value, locale),
                  })}
                </title>
              </circle>
            ),
        )}
      </Box>
      <Box sx={{ display: 'flex', gap: 3, mt: 1, flexWrap: 'wrap' }} aria-hidden="true">
        <Typography variant="caption" sx={{ color: lineColor }}>
          {t('chart.legendValue')}
        </Typography>
        <Typography variant="caption" sx={{ color: barColor }}>
          {t('chart.legendContributed')}
        </Typography>
      </Box>
    </Box>
  )
}
