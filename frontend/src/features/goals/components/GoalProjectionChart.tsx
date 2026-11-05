import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { Box, Skeleton, Typography, useTheme } from '@mui/material'
import type { Goal, ProjectionScenarioName, ScenarioProjection } from '../../../api/goals/goals'
import { useGoalProjection } from '../../../api/goals/goalsQueries'
import { CHART_HEIGHT, CHART_WIDTH, ChartSvg, YGrid } from '../../../components/charts/ChartSvg'
import { createYScale } from '../../../components/charts/chartMath'
import { fadeInSx } from '../../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../../hooks/queryState'
import { useDelayedFlag } from '../../../hooks/useDelayedFlag'
import { formatMoney, formatNumber } from '../../../i18n/format'
import { useLocale, type Locale } from '../../../providers/locale/useLocale'
import { formatGoalDate, formatGoalMonth } from '../lib/goalFormat'

const MARGIN = { top: 24, right: 16, bottom: 32, left: 72 }
const X_LABELS = 4

/** Dash patterns tell the lines apart without relying on colour alone. */
const DASH: Record<ProjectionScenarioName, string | undefined> = {
  PESSIMISTIC: '6 4',
  BASE: undefined,
  OPTIMISTIC: '2 3',
}

/** Visually hidden marker the skeleton carries for screen readers. */
const VISUALLY_HIDDEN = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

interface GoalProjectionChartProps {
  goal: Goal
}

/**
 * The projection of a goal (PRD S6.17, F039): the pessimistic, base and optimistic lines from now
 * against a dashed target line, hand-drawn SVG on the shared `ChartSvg` frame (no chart library).
 * It fetches its own data; the scenario table beside it reads the same query. Every value is in
 * today's money and the caption names the latest snapshot behind the starting point. For a
 * dateless goal each legend entry also says when that scenario reaches the target, or that it does
 * not within 50 years.
 */
export function GoalProjectionChart({ goal }: GoalProjectionChartProps) {
  const { t } = useTranslation('goals')
  const { locale } = useLocale()
  const theme = useTheme()
  const projectionQuery = useGoalProjection(goal.id)
  const projection = projectionQuery.data
  const { loading, loadError, reload } = useQueryState(projectionQuery)
  const showSkeleton = useDelayedFlag(loading)

  if (loadError) return <LoadFailedNotice message={loadError} onRetry={reload} />
  if (showSkeleton) {
    return (
      <Box role="status" aria-label={t('detail.projectionHeading')}>
        <Box component="span" sx={VISUALLY_HIDDEN}>
          {t('detail.projectionHeading')}
        </Box>
        <Skeleton variant="rounded" height={CHART_HEIGHT} />
      </Box>
    )
  }
  if (!projection) return null

  const colors: Record<ProjectionScenarioName, string> = {
    PESSIMISTIC: theme.palette.error.main,
    BASE: theme.palette.primary.main,
    OPTIMISTIC: theme.palette.success.main,
  }
  const dateless = goal.targetDate === null

  return (
    <Box sx={fadeInSx}>
      <Chart
        scenarios={projection.scenarios}
        target={goal.targetAmount}
        colors={colors}
        axisColor={theme.palette.text.secondary}
        gridColor={theme.palette.divider}
        targetColor={theme.palette.text.primary}
        t={t}
        locale={locale}
      />
      <Box
        component="ul"
        sx={{ listStyle: 'none', m: 0, mt: 1, p: 0, display: 'flex', flexWrap: 'wrap', gap: 2 }}
      >
        {projection.scenarios.map((scenario) => (
          <Box
            component="li"
            key={scenario.scenario}
            sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
          >
            <svg width="24" height="8" aria-hidden="true">
              <line
                x1="0"
                x2="24"
                y1="4"
                y2="4"
                stroke={colors[scenario.scenario]}
                strokeWidth={2}
                strokeDasharray={DASH[scenario.scenario]}
              />
            </svg>
            <Typography variant="body2">{legendText(scenario, dateless, t, locale)}</Typography>
          </Box>
        ))}
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        {projection.latestSnapshotDate
          ? t('projection.captionWithSnapshot', {
              date: formatGoalDate(projection.latestSnapshotDate, locale),
            })
          : t('projection.captionNoSnapshot')}
      </Typography>
    </Box>
  )
}

function rateText(rate: number, locale: Locale): string {
  return formatNumber(rate, locale, Number.isInteger(rate) ? 0 : 2)
}

function legendText(
  scenario: ScenarioProjection,
  dateless: boolean,
  t: TFunction<'goals'>,
  locale: Locale,
): string {
  const name = t(`projection.scenario.${scenario.scenario}`)
  const rate = rateText(scenario.annualReturn, locale)
  if (!dateless) return t('projection.legend', { name, rate })
  const detail =
    scenario.monthsToTarget === null
      ? t('projection.notReached')
      : scenario.monthsToTarget === 0
        ? t('projection.alreadyReached')
        : t('projection.reachedIn', { count: scenario.monthsToTarget })
  return t('projection.legendWithDetail', { name, rate, detail })
}

interface ChartProps {
  scenarios: ScenarioProjection[]
  target: number
  colors: Record<ProjectionScenarioName, string>
  axisColor: string
  gridColor: string
  targetColor: string
  t: TFunction<'goals'>
  locale: Locale
}

function Chart({
  scenarios,
  target,
  colors,
  axisColor,
  gridColor,
  targetColor,
  t,
  locale,
}: ChartProps) {
  const pointCount = Math.max(...scenarios.map((s) => s.points.length))
  const values = scenarios.flatMap((s) => s.points.map((p) => p.value))
  const { ticks, y } = createYScale({
    min: Math.min(0, ...values),
    max: Math.max(target, ...values),
    height: CHART_HEIGHT,
    margin: MARGIN,
  })
  const innerWidth = CHART_WIDTH - MARGIN.left - MARGIN.right
  // Months are evenly spaced; a single point sits in the middle.
  const x = (index: number) =>
    MARGIN.left + (pointCount === 1 ? innerWidth / 2 : (index / (pointCount - 1)) * innerWidth)
  const months = scenarios[0].points.map((p) => p.month)
  const labelIndexes = [
    ...new Set(
      Array.from({ length: Math.min(X_LABELS, pointCount) }, (_, i) =>
        pointCount === 1 ? 0 : Math.round((i * (pointCount - 1)) / (X_LABELS - 1)),
      ),
    ),
  ]

  return (
    <ChartSvg label={t('projection.chartAria')}>
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
          key={months[index]}
          x={x(index)}
          y={CHART_HEIGHT - MARGIN.bottom + 18}
          textAnchor={
            pointCount === 1
              ? 'middle'
              : index === 0
                ? 'start'
                : index === pointCount - 1
                  ? 'end'
                  : 'middle'
          }
          fontSize="11"
          fill={axisColor}
        >
          {formatGoalMonth(months[index], locale)}
        </text>
      ))}
      <line
        data-target=""
        x1={MARGIN.left}
        x2={CHART_WIDTH - MARGIN.right}
        y1={y(target)}
        y2={y(target)}
        stroke={targetColor}
        strokeWidth={1.5}
        strokeDasharray="8 4"
      />
      <text
        x={CHART_WIDTH - MARGIN.right}
        y={y(target) - 6}
        textAnchor="end"
        fontSize="11"
        fill={targetColor}
      >
        {t('projection.target', { amount: formatMoney(target, locale) })}
      </text>
      {scenarios.map((scenario) => {
        const path = scenario.points
          .map((p, index) => `${index === 0 ? 'M' : 'L'}${x(index)},${y(p.value)}`)
          .join(' ')
        const last = scenario.points[scenario.points.length - 1]
        return (
          <g key={scenario.scenario}>
            <path
              data-scenario={scenario.scenario}
              d={path}
              fill="none"
              stroke={colors[scenario.scenario]}
              strokeWidth={2}
              strokeDasharray={DASH[scenario.scenario]}
            />
            <circle
              cx={x(scenario.points.length - 1)}
              cy={y(last.value)}
              r={3.5}
              fill={colors[scenario.scenario]}
            >
              <title>{`${t(`projection.scenario.${scenario.scenario}`)}: ${formatMoney(last.value, locale)}`}</title>
            </circle>
          </g>
        )
      })}
    </ChartSvg>
  )
}
