import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import type { Goal, ScenarioProjection } from '../../../api/goals/goals'
import { useGoalProjection } from '../../../api/goals/goalsQueries'
import { ResponsiveTable, type ResponsiveColumn } from '../../../components/table/ResponsiveTable'
import { useQueryState } from '../../../hooks/queryState'
import { formatMoney, formatNumber } from '../../../i18n/format'
import { useLocale, type Locale } from '../../../providers/locale/useLocale'
import { formatGoalDate } from '../lib/goalFormat'

/**
 * The projection results per scenario (PRD S6.17, F039). A dated goal gets the projected value at
 * the target date, the shortfall or surplus against the target and the monthly contribution needed
 * to reach it on time; a dateless goal gets the time until the target is reached (or that it is
 * not within 50 years). Reads the same query as `GoalProjectionChart`, so it costs no extra
 * request. Amounts are in today's money.
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
      rows={projectionQuery.data?.scenarios}
      getRowKey={(scenario) => scenario.scenario}
      state={state}
    />
  )
}

function scenarioColumns(
  t: TFunction<'goals'>,
  locale: Locale,
): ResponsiveColumn<ScenarioProjection>[] {
  return [
    {
      key: 'scenario',
      header: t('projection.table.scenario'),
      role: 'primary',
      render: (s) => t(`projection.scenario.${s.scenario}`),
    },
    {
      key: 'return',
      header: t('projection.table.return'),
      align: 'right',
      tabletPriority: 'low',
      render: (s) =>
        `${formatNumber(s.annualReturn, locale, Number.isInteger(s.annualReturn) ? 0 : 2)}%`,
    },
  ]
}

function datedColumns(
  targetDate: string,
  t: TFunction<'goals'>,
  locale: Locale,
): ResponsiveColumn<ScenarioProjection>[] {
  return [
    ...scenarioColumns(t, locale),
    {
      key: 'valueAtDate',
      header: t('projection.table.valueAtDate', { date: formatGoalDate(targetDate, locale) }),
      align: 'right',
      render: (s) =>
        s.projectedValueAtTargetDate === null
          ? '—'
          : formatMoney(s.projectedValueAtTargetDate, locale),
    },
    {
      key: 'gap',
      header: t('projection.table.gap'),
      align: 'right',
      render: (s) => {
        if (s.shortfallAtTargetDate === null) return '—'
        return s.shortfallAtTargetDate > 0
          ? t('projection.table.short', { amount: formatMoney(s.shortfallAtTargetDate, locale) })
          : t('projection.table.surplus', {
              amount: formatMoney(-s.shortfallAtTargetDate, locale),
            })
      },
    },
    {
      key: 'required',
      header: t('projection.table.required'),
      align: 'right',
      render: (s) => {
        if (s.requiredMonthlyContribution === null) return '—'
        return s.requiredMonthlyContribution === 0
          ? t('projection.table.noneNeeded')
          : formatMoney(s.requiredMonthlyContribution, locale)
      },
    },
  ]
}

function datelessColumns(
  t: TFunction<'goals'>,
  locale: Locale,
): ResponsiveColumn<ScenarioProjection>[] {
  return [
    ...scenarioColumns(t, locale),
    {
      key: 'timeToTarget',
      header: t('projection.table.timeToTarget'),
      align: 'right',
      render: (s) => {
        if (s.monthsToTarget === null) return t('projection.table.notWithin50Years')
        return s.monthsToTarget === 0
          ? t('projection.table.reachedNow')
          : t('projection.table.reachedInMonths', { count: s.monthsToTarget })
      },
    },
  ]
}
