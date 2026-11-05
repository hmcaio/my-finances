import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useParams } from 'react-router-dom'
import {
  Alert,
  Box,
  Button,
  Chip,
  LinearProgress,
  Link as MuiLink,
  Skeleton,
  Typography,
} from '@mui/material'
import EditIcon from '@mui/icons-material/Edit'
import { defaultErrorMessage } from '../../api/core/apiError'
import type { Goal } from '../../api/goals/goals'
import { useGoal } from '../../api/goals/goalsQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { PageContainer } from '../../components/layout/PageContainer'
import { PageSection } from '../../components/layout/PageSection'
import { WRAP_ANYWHERE } from '../../components/layout/layoutSx'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { formatMoney, formatNumber } from '../../i18n/format'
import { useLocale } from '../../providers/locale/useLocale'
import { GoalAllocationEditor } from './components/GoalAllocationEditor'
import { GoalForm } from './components/GoalForm'
import { GoalHistoryChart } from './components/GoalHistoryChart'
import { GoalProjectionChart } from './components/GoalProjectionChart'
import { GoalScenarioTable } from './components/GoalScenarioTable'
import { GoalStatusLine } from './components/GoalStatusLine'
import { barValue, formatGoalDate, formatProgress } from './lib/goalFormat'

/**
 * One goal (PRD S6.17, F039): its summary and progress, the allocation editor, the projection
 * (chart plus the per-scenario results table) and the progress history. The numbers are
 * estimates in today's money; each section fetches and fails on its own, so one failed read does
 * not blank the page.
 */
export function GoalDetailPage() {
  const { t } = useTranslation(['goals', 'common'])
  const { id } = useParams<{ id: string }>()
  const goalQuery = useGoal(id)
  const goal = goalQuery.data
  const { loading, loadError } = useQueryState(goalQuery, undefined, (err) =>
    defaultErrorMessage(err, { 404: t('goals:detail.notFound') }),
  )
  const showSkeleton = useDelayedFlag(loading)
  const [editing, setEditing] = useState(false)

  return (
    <PageContainer>
      <MuiLink component={RouterLink} to="/goals" underline="hover">
        {t('goals:detail.backLink')}
      </MuiLink>

      {loadError && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {loadError}
        </Alert>
      )}
      {loading && showSkeleton && (
        <Box role="status" aria-label={t('goals:loadingGoal')} sx={{ mt: 2 }}>
          <Skeleton variant="text" width={280} sx={{ typography: 'h4' }} />
          <Skeleton variant="rounded" height={160} sx={{ mt: 2 }} />
        </Box>
      )}

      {goal && (
        <Box sx={fadeInSx}>
          <GoalSummary goal={goal} onEdit={() => setEditing(true)} />

          <PageSection sx={{ mb: 3 }}>
            <Typography variant="h5" component="h2" gutterBottom>
              {t('goals:detail.allocationHeading')}
            </Typography>
            <GoalAllocationEditor goal={goal} />
          </PageSection>

          <PageSection sx={{ mb: 3 }}>
            <Typography variant="h5" component="h2" gutterBottom>
              {t('goals:detail.projectionHeading')}
            </Typography>
            <GoalProjectionChart goal={goal} />
            <Box sx={{ mt: 2 }}>
              <GoalScenarioTable goal={goal} />
            </Box>
          </PageSection>

          <PageSection sx={{ mb: 3 }}>
            <Typography variant="h5" component="h2" gutterBottom>
              {t('goals:detail.historyHeading')}
            </Typography>
            <GoalHistoryChart goalId={goal.id} />
          </PageSection>

          {editing && <GoalForm open goal={goal} onClose={() => setEditing(false)} />}
        </Box>
      )}
    </PageContainer>
  )
}

function GoalSummary({ goal, onEdit }: { goal: Goal; onEdit: () => void }) {
  const { t } = useTranslation('goals')
  const { locale } = useLocale()
  const rate = (value: number) => formatNumber(value, locale, Number.isInteger(value) ? 0 : 2)
  return (
    <PageSection sx={{ mt: 2, mb: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="h4" component="h1" sx={{ ...WRAP_ANYWHERE, flexGrow: 1 }}>
          {goal.name}
        </Typography>
        {goal.status !== 'ACTIVE' && (
          <Chip size="small" variant="outlined" label={t(`status.${goal.status}`)} />
        )}
        {goal.targetReached && <Chip size="small" color="success" label={t('targetReached')} />}
        <Button startIcon={<EditIcon />} onClick={onEdit}>
          {t('detail.edit')}
        </Button>
      </Box>
      {goal.notes && (
        <Typography color="text.secondary" sx={WRAP_ANYWHERE}>
          {goal.notes}
        </Typography>
      )}
      <Typography color="text.secondary">
        {goal.targetDate
          ? t('targetDate', { date: formatGoalDate(goal.targetDate, locale) })
          : t('noDate')}
      </Typography>
      <LinearProgress
        variant="determinate"
        value={barValue(goal.progressPercent)}
        aria-label={t('progressAria', { name: goal.name })}
        color={goal.targetReached ? 'success' : 'primary'}
        sx={{ my: 1.5, height: 8, borderRadius: 4 }}
      />
      <Typography>
        {t('progress', {
          allocated: formatMoney(goal.allocatedValue, locale),
          target: formatMoney(goal.targetAmount, locale),
          percent: formatProgress(goal.progressPercent, locale),
        })}
      </Typography>
      {goal.status !== 'ARCHIVED' && <GoalStatusLine goal={goal} omitReached />}
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
        {t('detail.plannedContribution', {
          amount: formatMoney(goal.plannedMonthlyContribution, locale),
        })}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {t('detail.returns', {
          pessimistic: rate(goal.returnPessimistic),
          base: rate(goal.returnBase),
          optimistic: rate(goal.returnOptimistic),
        })}
      </Typography>
    </PageSection>
  )
}
