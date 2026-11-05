import { useTranslation } from 'react-i18next'
import { Typography } from '@mui/material'
import { useLocale } from '../../../providers/locale/useLocale'
import { formatMoney } from '../../../i18n/format'
import { goalStatusLine } from '../lib/goalStatusLine'
import { formatGoalDate } from '../lib/goalFormat'

interface GoalStatusLineProps {
  goal: {
    targetDate: string | null
    projectedShortfall: number | null
    monthsToTarget: number | null
  }
}

/**
 * The one base-scenario status line of a goal (PRD S6.17, F039), shared by the goals list and the
 * dashboard card: "On track", "Short by X by <date>", "Reached in N months", "Target reached" or
 * "Not reached within 50 years".
 */
export function GoalStatusLine({ goal }: GoalStatusLineProps) {
  const { t } = useTranslation('goals')
  const { locale } = useLocale()
  const line = goalStatusLine(goal)
  const text = (() => {
    switch (line.kind) {
      case 'onTrack':
        return t('statusLine.onTrack')
      case 'short':
        return t('statusLine.short', {
          amount: formatMoney(line.amount, locale),
          date: formatGoalDate(line.date, locale),
        })
      case 'reached':
        return t('statusLine.reached')
      case 'reachedInMonths':
        return t('statusLine.reachedInMonths', { count: line.months })
      case 'notWithin50Years':
        return t('statusLine.notWithin50Years')
    }
  })()
  const good = line.kind === 'onTrack' || line.kind === 'reached' || line.kind === 'reachedInMonths'
  return (
    <Typography variant="body2" color={good ? 'success.main' : 'warning.main'}>
      {text}
    </Typography>
  )
}
