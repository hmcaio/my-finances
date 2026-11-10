import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import type { Goal, GoalProjection } from '../../../api/goals/goals'
import { useGoalProjection } from '../../../api/goals/goalsQueries'
import { ResponsiveTable, type ResponsiveColumn } from '../../../components/table/ResponsiveTable'
import { useQueryState } from '../../../hooks/queryState'
import { formatMoney, formatNumber } from '../../../i18n/format'
import { useLocale, type Locale } from '../../../providers/locale/useLocale'
import { formatGoalDate } from '../lib/goalFormat'

/**
 * One line of the table: a scenario, or (F041) the current pace, which has no real return and no
 * monthly contribution needed (`null`, shown as a dash).
 */
interface ResultRow {
  key: string
  name: string
  annualReturn: number | null
  projectedValueAtTargetDate: number | null
  shortfallAtTargetDate: number | null
  requiredMonthlyContribution: number | null
  monthsToTarget: number | null
}

function rowsOf(projection: GoalProjection, t: TFunction<'goals'>): ResultRow[] {
  const rows: ResultRow[] = projection.scenarios.map((s) => ({
    key: s.scenario,
    name: t(`projection.scenario.${s.scenario}`),
    annualReturn: s.annualReturn,
    projectedValueAtTargetDate: s.projectedValueAtTargetDate,
    shortfallAtTargetDate: s.shortfallAtTargetDate,
    requiredMonthlyContribution: s.requiredMonthlyContribution,
    monthsToTarget: s.monthsToTarget,
  }))
  if (projection.pace) {
    rows.push({
      key: 'PACE',
      name: t('pace.name'),
      annualReturn: null,
      projectedValueAtTargetDate: projection.pace.projectedValueAtTargetDate,
      shortfallAtTargetDate: projection.pace.shortfallAtTargetDate,
      requiredMonthlyContribution: null,
      monthsToTarget: projection.pace.monthsToTarget,
    })
  }
  return rows
}

/**
 * The projection results per scenario (PRD S6.17, F039), then a "Current pace" row when the goal
 * has enough history for one (F041). A dated goal gets the projected value at the target date, the
 * shortfall or surplus against the target and the monthly contribution needed to reach it on time;
 * a dateless goal gets the time until the target is reached (or that it is not within 50 years).
 * The pace row has no real return and no contribution needed, so those cells are dashes. Reads the
 * same query as `GoalTimelineChart`, so it costs no extra request. The scenarios are in today's
 * money; the pace is in recorded (nominal) money.
 */
export function GoalScenarioTable({ goal }: { goal: Goal }) {
  const { t } = useTranslation('goals')
  const { locale } = useLocale()
  const projectionQuery = useGoalProjection(goal.id)
  const state = useQueryState(projectionQuery)

  const columns = goal.targetDate
    ? datedColumns(goal.targetDate, t, locale)
    : datelessColumns(t, locale)

  return (
    <ResponsiveTable
      aria-label={t('projection.table.aria')}
      columns={columns}
      rows={projectionQuery.data ? rowsOf(projectionQuery.data, t) : undefined}
      getRowKey={(row) => row.key}
      state={state}
    />
  )
}

function scenarioColumns(t: TFunction<'goals'>, locale: Locale): ResponsiveColumn<ResultRow>[] {
  return [
    {
      key: 'scenario',
      header: t('projection.table.scenario'),
      role: 'primary',
      render: (row) => row.name,
    },
    {
      key: 'return',
      header: t('projection.table.return'),
      align: 'right',
      tabletPriority: 'low',
      render: (row) =>
        row.annualReturn === null
          ? '—'
          : `${formatNumber(row.annualReturn, locale, Number.isInteger(row.annualReturn) ? 0 : 2)}%`,
    },
  ]
}

function datedColumns(
  targetDate: string,
  t: TFunction<'goals'>,
  locale: Locale,
): ResponsiveColumn<ResultRow>[] {
  return [
    ...scenarioColumns(t, locale),
    {
      key: 'valueAtDate',
      header: t('projection.table.valueAtDate', { date: formatGoalDate(targetDate, locale) }),
      align: 'right',
      render: (row) =>
        row.projectedValueAtTargetDate === null
          ? '—'
          : formatMoney(row.projectedValueAtTargetDate, locale),
    },
    {
      key: 'gap',
      header: t('projection.table.gap'),
      align: 'right',
      render: (row) => {
        if (row.shortfallAtTargetDate === null) return '—'
        return row.shortfallAtTargetDate > 0
          ? t('projection.table.short', { amount: formatMoney(row.shortfallAtTargetDate, locale) })
          : t('projection.table.surplus', {
              amount: formatMoney(-row.shortfallAtTargetDate, locale),
            })
      },
    },
    {
      key: 'required',
      header: t('projection.table.required'),
      align: 'right',
      render: (row) => {
        if (row.requiredMonthlyContribution === null) return '—'
        return row.requiredMonthlyContribution === 0
          ? t('projection.table.noneNeeded')
          : formatMoney(row.requiredMonthlyContribution, locale)
      },
    },
  ]
}

function datelessColumns(t: TFunction<'goals'>, locale: Locale): ResponsiveColumn<ResultRow>[] {
  return [
    ...scenarioColumns(t, locale),
    {
      key: 'timeToTarget',
      header: t('projection.table.timeToTarget'),
      align: 'right',
      render: (row) => {
        if (row.monthsToTarget === null) return t('projection.table.notWithin50Years')
        return row.monthsToTarget === 0
          ? t('projection.table.reachedNow')
          : t('projection.table.reachedInMonths', { count: row.monthsToTarget })
      },
    },
  ]
}
