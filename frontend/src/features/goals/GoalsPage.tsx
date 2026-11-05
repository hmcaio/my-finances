import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import {
  Box,
  Button,
  Chip,
  FormControlLabel,
  IconButton,
  LinearProgress,
  Link as MuiLink,
  Paper,
  Skeleton,
  Switch,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import type { Goal } from '../../api/goals/goals'
import { useDeleteGoal, useGoals, useSetGoalShowOnDashboard } from '../../api/goals/goalsQueries'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { PageContainer } from '../../components/layout/PageContainer'
import { PageHeader } from '../../components/layout/PageHeader'
import { WRAP_ANYWHERE } from '../../components/layout/layoutSx'
import { useQueryState } from '../../hooks/queryState'
import { useAction } from '../../hooks/useAction'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { formatMoney } from '../../i18n/format'
import { useLocale } from '../../providers/locale/useLocale'
import { GoalForm } from './components/GoalForm'
import { GoalStatusLine } from './components/GoalStatusLine'
import { barValue, formatGoalDate, formatProgress } from './lib/goalFormat'

/** The goal being created (`{}`), edited (`{ goal }`), or none (`null`: dialog closed). */
type FormTarget = { goal?: Goal } | null

/**
 * The goals list (PRD S6.17, F039): each goal with its progress bar (allocated value over the
 * target, which may pass 100%), a "target reached" badge, the one base-scenario status line and a
 * "Show on dashboard" switch. Dated goals come first by date, then dateless ones by name (the
 * backend's order); archived goals only appear when asked for. Create and edit share `GoalForm`;
 * a goal opens its own page for the allocation editor and the projection.
 */
export function GoalsPage() {
  const { t } = useTranslation(['goals', 'common'])
  const { locale } = useLocale()
  const [error, setError] = useState<string | null>(null)
  const [includeArchived, setIncludeArchived] = useState(false)
  const [formTarget, setFormTarget] = useState<FormTarget>(null)
  const [deleteTarget, setDeleteTarget] = useState<Goal | null>(null)
  const [togglingId, setTogglingId] = useState<string | null>(null)

  const goalsQuery = useGoals(includeArchived)
  const goals = goalsQuery.data
  const { loading, loadError, reload } = useQueryState(goalsQuery, setError)
  const showSkeleton = useDelayedFlag(loading)

  const toggleMutation = useSetGoalShowOnDashboard()
  const toggleAction = useAction(setError)
  const deleteMutation = useDeleteGoal()
  const deleteAction = useAction(setError)

  async function toggleDashboard(goal: Goal, showOnDashboard: boolean) {
    setTogglingId(goal.id)
    await toggleAction.run(() => toggleMutation.mutateAsync({ id: goal.id, showOnDashboard }))
    setTogglingId(null)
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    await deleteAction.run(() => deleteMutation.mutateAsync(deleteTarget.id))
    setDeleteTarget(null)
  }

  // Clears the stale banner before retrying, so it doesn't outlive a successful retry.
  function retry() {
    setError(null)
    reload()
  }

  return (
    <PageContainer>
      <PageHeader title={t('goals:title')}>
        <Button variant="contained" onClick={() => setFormTarget({})}>
          {t('goals:newGoal')}
        </Button>
      </PageHeader>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {t('goals:description')}
      </Typography>

      {/* While the form is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert message={formTarget ? null : error} onDismiss={() => setError(null)} />

      <FormControlLabel
        control={
          <Switch
            checked={includeArchived}
            onChange={(e) => setIncludeArchived(e.target.checked)}
          />
        }
        label={t('goals:showArchived')}
        sx={{ mb: 2 }}
      />

      {loadError && <LoadFailedNotice message={loadError} onRetry={retry} />}
      {loading && showSkeleton && (
        <Box role="status" aria-label={t('goals:loading')} sx={{ display: 'grid', gap: 2 }}>
          <Skeleton variant="rounded" height={120} />
          <Skeleton variant="rounded" height={120} />
        </Box>
      )}

      {goals && goals.length === 0 && (
        <Paper variant="outlined" sx={{ p: 3, textAlign: 'center', ...fadeInSx }}>
          <Typography variant="h6" component="h2" gutterBottom>
            {t('goals:empty.title')}
          </Typography>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            {t('goals:empty.body')}
          </Typography>
          <Button variant="contained" onClick={() => setFormTarget({})}>
            {t('goals:empty.cta')}
          </Button>
        </Paper>
      )}

      {goals && goals.length > 0 && (
        <Box
          component="ul"
          sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2, ...fadeInSx }}
        >
          {goals.map((goal) => (
            <GoalCard
              key={goal.id}
              goal={goal}
              locale={locale}
              toggling={togglingId === goal.id}
              onToggle={(show) => void toggleDashboard(goal, show)}
              onEdit={() => setFormTarget({ goal })}
              onDelete={() => setDeleteTarget(goal)}
            />
          ))}
        </Box>
      )}

      {formTarget && <GoalForm open goal={formTarget.goal} onClose={() => setFormTarget(null)} />}

      <ConfirmDialog
        open={deleteTarget !== null}
        title={t('goals:delete.title')}
        body={t('goals:delete.body', { name: deleteTarget?.name ?? '' })}
        confirmLabel={t('goals:delete.confirm')}
        loading={deleteAction.pending}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleteTarget(null)}
      />
    </PageContainer>
  )
}

interface GoalCardProps {
  goal: Goal
  locale: ReturnType<typeof useLocale>['locale']
  toggling: boolean
  onToggle: (show: boolean) => void
  onEdit: () => void
  onDelete: () => void
}

function GoalCard({ goal, locale, toggling, onToggle, onEdit, onDelete }: GoalCardProps) {
  const { t } = useTranslation('goals')
  const archived = goal.status === 'ARCHIVED'
  return (
    <Paper component="li" variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="h6" component="h2" sx={{ ...WRAP_ANYWHERE, flexGrow: 1 }}>
          <MuiLink component={RouterLink} to={`/goals/${goal.id}`} underline="hover">
            {goal.name}
          </MuiLink>
        </Typography>
        {goal.status !== 'ACTIVE' && (
          <Chip size="small" label={t(`status.${goal.status}`)} variant="outlined" />
        )}
        {goal.targetReached && <Chip size="small" color="success" label={t('targetReached')} />}
        <IconButton size="small" aria-label={t('editAria', { name: goal.name })} onClick={onEdit}>
          <EditIcon fontSize="small" />
        </IconButton>
        <IconButton
          size="small"
          aria-label={t('deleteAria', { name: goal.name })}
          onClick={onDelete}
        >
          <DeleteIcon fontSize="small" />
        </IconButton>
      </Box>
      {goal.notes && (
        <Typography color="text.secondary" sx={WRAP_ANYWHERE}>
          {goal.notes}
        </Typography>
      )}
      <Typography variant="body2" color="text.secondary">
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
      <Typography variant="body2">
        {t('progress', {
          allocated: formatMoney(goal.allocatedValue, locale),
          target: formatMoney(goal.targetAmount, locale),
          percent: formatProgress(goal.progressPercent, locale),
        })}
      </Typography>
      {!archived && <GoalStatusLine goal={goal} />}

      <FormControlLabel
        sx={{ mt: 1 }}
        control={
          <Switch
            checked={goal.showOnDashboard}
            disabled={archived || toggling}
            onChange={(e) => onToggle(e.target.checked)}
            slotProps={{ input: { 'aria-label': t('showOnDashboardAria', { name: goal.name }) } }}
          />
        }
        label={t('showOnDashboard')}
      />
    </Paper>
  )
}
