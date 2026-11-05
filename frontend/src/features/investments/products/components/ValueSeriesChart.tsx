import { useTranslation } from 'react-i18next'
import { Box, Typography, useTheme } from '@mui/material'
import { CHART_HEIGHT, CHART_WIDTH, ChartSvg, YGrid } from '../../../../components/charts/ChartSvg'
import { createYScale } from '../../../../components/charts/chartMath'
import type { ValueSeriesPoint } from '../../../../api/investments/products/investmentValueSeries'
import { useIsMobile } from '../../../../hooks/useBreakpointBand'
import { useLocale } from '../../../../providers/locale/useLocale'
import { formatMoney, formatNumber } from '../../../../i18n/format'

const MARGIN = { top: 16, right: 16, bottom: 44, left: 64 }

interface ValueSeriesChartProps {
  points: ValueSeriesPoint[]
  /** Accessible name of the chart. */
  label?: string
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
  const { ticks, y } = createYScale({ min, max, height: CHART_HEIGHT, margin: MARGIN })
  const innerWidth = CHART_WIDTH - MARGIN.left - MARGIN.right
  const slot = innerWidth / points.length
  const x = (index: number) => MARGIN.left + slot * index + slot / 2
  const barWidth = Math.min(28, slot * 0.5)

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
      <ChartSvg label={resolvedLabel}>
        <YGrid
          ticks={ticks}
          y={y}
          marginLeft={MARGIN.left}
          marginRight={MARGIN.right}
          gridColor={gridColor}
          axisColor={axisColor}
          formatTick={(tick) => formatNumber(tick, locale, 0)}
        />
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
                  y={CHART_HEIGHT - MARGIN.bottom + 16}
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
      </ChartSvg>
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
