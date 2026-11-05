import { useTranslation } from 'react-i18next'
import { Box, Skeleton, Typography, useTheme } from '@mui/material'
import type { GoalPoint } from '../../../api/goals/goals'
import { useGoalHistory } from '../../../api/goals/goalsQueries'
import { CHART_HEIGHT, CHART_WIDTH, ChartSvg, YGrid } from '../../../components/charts/ChartSvg'
import { createYScale } from '../../../components/charts/chartMath'
import { fadeInSx } from '../../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../../hooks/queryState'
import { useDelayedFlag } from '../../../hooks/useDelayedFlag'
import { formatMoney, formatNumber } from '../../../i18n/format'
import { useLocale, type Locale } from '../../../providers/locale/useLocale'
import { formatGoalMonth } from '../lib/goalFormat'

const MARGIN = { top: 16, right: 16, bottom: 32, left: 72 }
const X_LABELS = 4

const VISUALLY_HIDDEN = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

/**
 * A goal's progress over the last twelve months (PRD S5.16, F039): the goal's value as the
 * *current* split of each product's monthly value series (past splits are not stored). It says so
 * in its caption, "as of the current allocation". Hand-drawn SVG on the shared `ChartSvg` frame;
 * it fetches its own data.
 */
export function GoalHistoryChart({ goalId }: { goalId: string }) {
  const { t } = useTranslation('goals')
  const { locale } = useLocale()
  const theme = useTheme()
  const historyQuery = useGoalHistory(goalId)
  const points = historyQuery.data
  const { loading, loadError, reload } = useQueryState(historyQuery)
  const showSkeleton = useDelayedFlag(loading)

  if (loadError) return <LoadFailedNotice message={loadError} onRetry={reload} />
  if (showSkeleton) {
    return (
      <Box role="status" aria-label={t('history.loading')}>
        <Box component="span" sx={VISUALLY_HIDDEN}>
          {t('history.loading')}
        </Box>
        <Skeleton variant="rounded" height={CHART_HEIGHT} />
      </Box>
    )
  }
  if (!points) return null

  return (
    <Box sx={fadeInSx}>
      {points.length === 0 ? (
        <Typography color="text.secondary">{t('history.empty')}</Typography>
      ) : (
        <Chart
          points={points}
          lineColor={theme.palette.primary.main}
          axisColor={theme.palette.text.secondary}
          gridColor={theme.palette.divider}
          ariaLabel={t('history.chartAria')}
          tooltip={(point) =>
            t('history.tooltip', {
              month: formatGoalMonth(point.month, locale),
              value: formatMoney(point.value, locale),
            })
          }
          locale={locale}
        />
      )}
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        {t('history.caption')}
      </Typography>
    </Box>
  )
}

interface ChartProps {
  points: GoalPoint[]
  lineColor: string
  axisColor: string
  gridColor: string
  ariaLabel: string
  tooltip: (point: GoalPoint) => string
  locale: Locale
}

function Chart({
  points,
  lineColor,
  axisColor,
  gridColor,
  ariaLabel,
  tooltip,
  locale,
}: ChartProps) {
  const values = points.map((p) => p.value)
  const { ticks, y } = createYScale({
    min: Math.min(0, ...values),
    max: Math.max(0, ...values),
    height: CHART_HEIGHT,
    margin: MARGIN,
  })
  const innerWidth = CHART_WIDTH - MARGIN.left - MARGIN.right
  const x = (index: number) =>
    MARGIN.left +
    (points.length === 1 ? innerWidth / 2 : (index / (points.length - 1)) * innerWidth)
  const labelIndexes = [
    ...new Set(
      Array.from({ length: Math.min(X_LABELS, points.length) }, (_, i) =>
        points.length === 1 ? 0 : Math.round((i * (points.length - 1)) / (X_LABELS - 1)),
      ),
    ),
  ]
  const path = points
    .map((p, index) => `${index === 0 ? 'M' : 'L'}${x(index)},${y(p.value)}`)
    .join(' ')

  return (
    <ChartSvg label={ariaLabel}>
      <YGrid
        ticks={ticks}
        y={y}
        marginLeft={MARGIN.left}
        marginRight={MARGIN.right}
        gridColor={gridColor}
        axisColor={axisColor}
        formatTick={(tick) => formatNumber(tick, locale, 0)}
      />
      {labelIndexes.map((index) => (
        <text
          key={points[index].month}
          x={x(index)}
          y={CHART_HEIGHT - MARGIN.bottom + 18}
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
          {formatGoalMonth(points[index].month, locale)}
        </text>
      ))}
      <path data-history="" d={path} fill="none" stroke={lineColor} strokeWidth={2} />
      {points.map((point, index) => (
        <circle key={point.month} cx={x(index)} cy={y(point.value)} r={3.5} fill={lineColor}>
          <title>{tooltip(point)}</title>
        </circle>
      ))}
    </ChartSvg>
  )
}
