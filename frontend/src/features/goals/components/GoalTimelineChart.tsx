import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { Box, Skeleton, ToggleButton, ToggleButtonGroup, Typography, useTheme } from '@mui/material'
import type {
  Goal,
  GoalPace,
  GoalPoint,
  ProjectionScenarioName,
  ScenarioProjection,
} from '../../../api/goals/goals'
import { useGoalHistory, useGoalProjection } from '../../../api/goals/goalsQueries'
import { CHART_HEIGHT, CHART_WIDTH, ChartSvg, YGrid } from '../../../components/charts/ChartSvg'
import { createYScale } from '../../../components/charts/chartMath'
import { fadeInSx } from '../../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../../hooks/queryState'
import { useDelayedFlag } from '../../../hooks/useDelayedFlag'
import { useElementWidth } from '../../../hooks/useElementWidth'
import { formatMoney, formatNumber } from '../../../i18n/format'
import { useLocale, type Locale } from '../../../providers/locale/useLocale'
import { formatGoalDate, formatGoalMonth } from '../lib/goalFormat'
import { monthRange } from '../lib/monthRange'

const MARGIN = { top: 24, right: 16, bottom: 32, left: 72 }
const X_LABELS = 4
/** Below this drawing width the axis shows one month label fewer, so neighbours do not touch. */
const NARROW_CHART_WIDTH = 480

/** The drawing never gets narrower than this, so the plot stays readable on a tiny container. */
const MIN_CHART_WIDTH = 280
/** The drawing is about this tall per unit of width, within the bounds below. */
const HEIGHT_RATIO = 0.35
const MAX_CHART_HEIGHT = 400

/** The shorter horizons the selector offers, in months; "All" is always there. */
const HORIZON_MONTHS = [12, 24, 60]

/** Dash patterns tell the lines apart without relying on colour alone. */
const DASH: Record<ProjectionScenarioName, string | undefined> = {
  PESSIMISTIC: '6 4',
  BASE: undefined,
  OPTIMISTIC: '2 3',
}

/** The pace line is drawn apart from the scenarios: grey and dash-dot (ADR 0035). */
const PACE_DASH = '8 3 2 3'

/** Visually hidden marker the skeleton carries for screen readers. */
const VISUALLY_HIDDEN = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

interface GoalTimelineChartProps {
  goal: Goal
}

/**
 * A goal's value over time (PRD S5.16, S6.17, F039) in one graph: the progress history on the left
 * (the *current* split applied to each product's monthly series, so it is labelled "as of the
 * current allocation") and the pessimistic, base and optimistic projections forking from today on
 * the right, against a dashed target line. Hand-drawn SVG on the shared `ChartSvg` frame (no chart
 * library). It fetches its own data; the scenario table beside it reads the same projection query.
 *
 * The projection starts at the current month and the history ends there, so both sit on one
 * month axis and meet at the "today" marker. A failed or empty history leaves the projection
 * drawn alone. A long projection can be cut to the next 1, 2 or 5 years with the horizon selector,
 * so the history is not squashed against a 20-year line. The scenarios are in today's money.
 *
 * A fourth, grey dash-dot line is the current pace (F041, ADR 0035): the value now rising at the
 * average monthly increase of the last 12 months of recorded history. It starts on the "today"
 * marker, follows the horizon selector and is nominal money, so a note says it is not adjusted for
 * inflation; a goal with allocations but too little history says so instead of drawing it.
 *
 * The chart fills the width it is given: it measures its container and draws at that width (a
 * 1:1 viewBox, so text and strokes keep their size), instead of scaling a fixed 640 wide frame.
 * Where the width cannot be measured it falls back to that default frame.
 */
export function GoalTimelineChart({ goal }: GoalTimelineChartProps) {
  const [ref, width] = useElementWidth<HTMLDivElement>()
  return (
    <Box ref={ref}>
      <GoalTimeline goal={goal} availableWidth={width} />
    </Box>
  )
}

function GoalTimeline({ goal, availableWidth }: { goal: Goal; availableWidth: number | null }) {
  const { t } = useTranslation('goals')
  const { locale } = useLocale()
  const theme = useTheme()
  const [horizon, setHorizon] = useState<number | null>(null)
  const projectionQuery = useGoalProjection(goal.id)
  const historyQuery = useGoalHistory(goal.id)
  const projection = projectionQuery.data
  const projectionState = useQueryState(projectionQuery)
  const showSkeleton = useDelayedFlag(projectionState.loading || historyQuery.isPending)

  if (projectionState.loadError) {
    return <LoadFailedNotice message={projectionState.loadError} onRetry={projectionState.reload} />
  }
  if (showSkeleton) {
    return (
      <Box role="status" aria-label={t('detail.timelineHeading')}>
        <Box component="span" sx={VISUALLY_HIDDEN}>
          {t('detail.timelineHeading')}
        </Box>
        <Skeleton variant="rounded" height={CHART_HEIGHT} />
      </Box>
    )
  }
  if (!projection) return null

  const history = historyQuery.data ?? []
  const fullMonths = projection.scenarios[0].points.length - 1
  const horizons = HORIZON_MONTHS.filter((months) => months < fullMonths)
  const activeHorizon = horizon !== null && horizons.includes(horizon) ? horizon : null
  const scenarios =
    activeHorizon === null
      ? projection.scenarios
      : projection.scenarios.map((scenario) => ({
          ...scenario,
          points: scenario.points.slice(0, activeHorizon + 1),
        }))
  const pace =
    projection.pace === null
      ? null
      : activeHorizon === null
        ? projection.pace
        : { ...projection.pace, points: projection.pace.points.slice(0, activeHorizon + 1) }
  const paceColor = theme.palette.grey[500]
  const colors: Record<ProjectionScenarioName, string> = {
    PESSIMISTIC: theme.palette.error.main,
    BASE: theme.palette.primary.main,
    OPTIMISTIC: theme.palette.success.main,
  }
  const historyColor = theme.palette.text.primary
  const dateless = goal.targetDate === null

  return (
    <Box sx={fadeInSx}>
      {horizons.length > 0 && (
        <ToggleButtonGroup
          size="small"
          exclusive
          value={activeHorizon ?? 'ALL'}
          aria-label={t('timeline.horizonAria')}
          onChange={(_event, next: number | 'ALL' | null) => {
            if (next !== null) setHorizon(next === 'ALL' ? null : next)
          }}
          sx={{ mb: 1 }}
        >
          {horizons.map((months) => (
            <ToggleButton key={months} value={months}>
              {t('timeline.horizonYears', { count: months / 12 })}
            </ToggleButton>
          ))}
          <ToggleButton value="ALL">{t('timeline.horizonAll')}</ToggleButton>
        </ToggleButtonGroup>
      )}
      <Chart
        history={history}
        scenarios={scenarios}
        pace={pace}
        paceColor={paceColor}
        target={goal.targetAmount}
        colors={colors}
        historyColor={historyColor}
        availableWidth={availableWidth}
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
        {history.length > 0 && (
          <Box component="li" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <svg width="24" height="8" aria-hidden="true">
              <line x1="0" x2="24" y1="4" y2="4" stroke={historyColor} strokeWidth={2} />
            </svg>
            <Typography variant="body2">{t('timeline.historyLegend')}</Typography>
          </Box>
        )}
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
        {projection.pace && (
          <Box component="li" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <svg width="24" height="8" aria-hidden="true">
              <line
                x1="0"
                x2="24"
                y1="4"
                y2="4"
                stroke={paceColor}
                strokeWidth={2}
                strokeDasharray={PACE_DASH}
              />
            </svg>
            <Typography variant="body2">
              {t('pace.legend', { amount: signedMoney(projection.pace.monthlyPace, locale) })}
            </Typography>
          </Box>
        )}
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        {projection.latestSnapshotDate
          ? t('projection.captionWithSnapshot', {
              date: formatGoalDate(projection.latestSnapshotDate, locale),
            })
          : t('projection.captionNoSnapshot')}
      </Typography>
      {projection.pace && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
          {t('pace.inflationNote')}
        </Typography>
      )}
      {projection.pace === null && goal.allocations.length > 0 && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
          {t('pace.notEnoughHistory')}
        </Typography>
      )}
      {history.length > 0 && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
          {t('history.caption')}
        </Typography>
      )}
      {historyQuery.isError && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
          {t('timeline.historyUnavailable')}
        </Typography>
      )}
    </Box>
  )
}

/** An amount with an explicit `+` when it rises (a pace of zero or below carries its own sign). */
function signedMoney(amount: number, locale: Locale): string {
  return `${amount > 0 ? '+' : ''}${formatMoney(amount, locale)}`
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
  history: GoalPoint[]
  scenarios: ScenarioProjection[]
  /** The current-pace line, already cut to the horizon; `null` when there is none. */
  pace: GoalPace | null
  paceColor: string
  target: number
  colors: Record<ProjectionScenarioName, string>
  historyColor: string
  /** The container's measured width, or `null` to use the default frame. */
  availableWidth: number | null
  axisColor: string
  gridColor: string
  targetColor: string
  t: TFunction<'goals'>
  locale: Locale
}

function Chart({
  history,
  scenarios,
  pace,
  paceColor,
  target,
  colors,
  historyColor,
  availableWidth,
  axisColor,
  gridColor,
  targetColor,
  t,
  locale,
}: ChartProps) {
  const measured = availableWidth !== null
  const chartWidth = measured ? Math.max(MIN_CHART_WIDTH, availableWidth) : CHART_WIDTH
  const chartHeight = measured
    ? Math.min(MAX_CHART_HEIGHT, Math.max(CHART_HEIGHT, Math.round(chartWidth * HEIGHT_RATIO)))
    : CHART_HEIGHT
  const projectionStart = scenarios[0].points[0].month
  const projectionEnd = scenarios[0].points[scenarios[0].points.length - 1].month
  // One continuous month axis: the history (if any) ends where the projection starts.
  const firstMonth = history.length > 0 ? history[0].month : projectionStart
  const months = monthRange(
    firstMonth < projectionStart ? firstMonth : projectionStart,
    projectionEnd,
  )
  const indexOf = new Map(months.map((month, index) => [month, index]))
  const values = [...history, ...scenarios.flatMap((s) => s.points), ...(pace?.points ?? [])].map(
    (p) => p.value,
  )
  const { ticks, y } = createYScale({
    min: Math.min(0, ...values),
    max: Math.max(target, ...values),
    height: chartHeight,
    margin: MARGIN,
  })
  const innerWidth = chartWidth - MARGIN.left - MARGIN.right
  const labelCount = chartWidth < NARROW_CHART_WIDTH ? X_LABELS - 1 : X_LABELS
  // Months are evenly spaced; a single month sits in the middle.
  const x = (month: string) => {
    const index = indexOf.get(month) ?? 0
    return (
      MARGIN.left +
      (months.length === 1 ? innerWidth / 2 : (index / (months.length - 1)) * innerWidth)
    )
  }
  const labelIndexes = [
    ...new Set(
      Array.from({ length: Math.min(labelCount, months.length) }, (_, i) =>
        months.length === 1 ? 0 : Math.round((i * (months.length - 1)) / (labelCount - 1)),
      ),
    ),
  ]
  const linePath = (points: GoalPoint[]) =>
    points.map((p, index) => `${index === 0 ? 'M' : 'L'}${x(p.month)},${y(p.value)}`).join(' ')
  const todayX = x(projectionStart)
  const todayLabelX = Math.min(Math.max(todayX, MARGIN.left + 18), chartWidth - MARGIN.right - 18)

  return (
    <ChartSvg
      label={t('timeline.chartAria')}
      width={chartWidth}
      height={chartHeight}
      fillWidth={measured}
    >
      <YGrid
        ticks={ticks}
        y={y}
        marginLeft={MARGIN.left}
        marginRight={MARGIN.right}
        width={chartWidth}
        gridColor={gridColor}
        axisColor={axisColor}
        formatTick={(tick) => formatNumber(tick, locale, 0)}
      />
      {labelIndexes.map((index) => (
        <text
          key={months[index]}
          x={x(months[index])}
          y={chartHeight - MARGIN.bottom + 18}
          textAnchor={
            months.length === 1
              ? 'middle'
              : index === 0
                ? 'start'
                : index === months.length - 1
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
        x2={chartWidth - MARGIN.right}
        y1={y(target)}
        y2={y(target)}
        stroke={targetColor}
        strokeWidth={1.5}
        strokeDasharray="8 4"
      />
      <text
        x={chartWidth - MARGIN.right}
        y={y(target) - 6}
        textAnchor="end"
        fontSize="11"
        fill={targetColor}
      >
        {t('projection.target', { amount: formatMoney(target, locale) })}
      </text>
      {history.length > 0 && (
        <>
          <line
            data-today=""
            x1={todayX}
            x2={todayX}
            y1={MARGIN.top}
            y2={chartHeight - MARGIN.bottom}
            stroke={axisColor}
            strokeWidth={1}
            strokeDasharray="2 3"
          />
          <text
            x={todayLabelX}
            y={MARGIN.top - 8}
            textAnchor="middle"
            fontSize="11"
            fill={axisColor}
          >
            {t('timeline.today')}
          </text>
          <path
            data-history=""
            d={linePath(history)}
            fill="none"
            stroke={historyColor}
            strokeWidth={2}
          />
          {history.map((point) => (
            <circle
              key={point.month}
              cx={x(point.month)}
              cy={y(point.value)}
              r={3}
              fill={historyColor}
            >
              <title>
                {t('history.tooltip', {
                  month: formatGoalMonth(point.month, locale),
                  value: formatMoney(point.value, locale),
                })}
              </title>
            </circle>
          ))}
        </>
      )}
      {scenarios.map((scenario) => {
        const last = scenario.points[scenario.points.length - 1]
        return (
          <g key={scenario.scenario}>
            <path
              data-scenario={scenario.scenario}
              d={linePath(scenario.points)}
              fill="none"
              stroke={colors[scenario.scenario]}
              strokeWidth={2}
              strokeDasharray={DASH[scenario.scenario]}
            />
            <circle cx={x(last.month)} cy={y(last.value)} r={3.5} fill={colors[scenario.scenario]}>
              <title>{`${t(`projection.scenario.${scenario.scenario}`)}: ${formatMoney(last.value, locale)}`}</title>
            </circle>
          </g>
        )
      })}
      {pace && pace.points.length > 0 && (
        <g>
          <path
            data-pace=""
            d={linePath(pace.points)}
            fill="none"
            stroke={paceColor}
            strokeWidth={2}
            strokeDasharray={PACE_DASH}
          />
          <circle
            cx={x(pace.points[pace.points.length - 1].month)}
            cy={y(pace.points[pace.points.length - 1].value)}
            r={3.5}
            fill={paceColor}
          >
            <title>
              {t('pace.tooltip', {
                value: formatMoney(pace.points[pace.points.length - 1].value, locale),
              })}
            </title>
          </circle>
        </g>
      )}
    </ChartSvg>
  )
}
