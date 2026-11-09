import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import { Box, LinearProgress, Link as MuiLink, Paper, Skeleton, Typography } from '@mui/material'
import { useGoalDashboard } from '../../api/goals/goalsQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { WRAP_ANYWHERE } from '../../components/layout/layoutSx'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useLocale } from '../../providers/locale/useLocale'
import { GoalStatusLine } from '../goals/components/GoalStatusLine'
import { barValue, formatProgress } from '../goals/lib/goalFormat'

/**
 * The goals card of the dashboard (PRD S6.8, F039): every goal flagged "Show on dashboard", in
 * the goals list's order, each with a progress bar and one base-scenario status line, plus a
 * "View all" link to `/goals`. A prop-less widget that fetches its own data (one request serves the
 * whole card, however many goals are flagged); it renders nothing - not even its heading - when no
 * goal is flagged, so a dashboard without goals looks as it did before.
 */
export function GoalsWidget() {
  const { t } = useTranslation('goals')
  const { locale } = useLocale()
  const dashboardQuery = useGoalDashboard()
  const goals = dashboardQuery.data
  const { loading, loadError, reload } = useQueryState(dashboardQuery)
  const showSkeleton = useDelayedFlag(loading)

  if (goals?.length === 0) return null
  if (goals === undefined && !loadError && !showSkeleton) return null

  return (
    <Box component="section" aria-label={t('title')}>
      <Typography variant="h5" component="h2" gutterBottom>
        {t('title')}
      </Typography>
      <Paper variant="outlined" sx={{ p: 2 }}>
        {loadError && <LoadFailedNotice message={loadError} onRetry={reload} />}
        {goals === undefined && !loadError && (
          <Box role="status" aria-label={t('loading')}>
            {[0, 1].map((row) => (
              <Skeleton key={row} variant="text" sx={{ typography: 'body2' }} />
            ))}
          </Box>
        )}
        {goals && (
          <Box sx={fadeInSx}>
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2 }}>
              {goals.map((goal) => (
                <li key={goal.id}>
                  <MuiLink
                    component={RouterLink}
                    to={`/goals/${goal.id}`}
                    underline="hover"
                    sx={{ ...WRAP_ANYWHERE, fontWeight: 500 }}
                  >
                    {goal.name}
                  </MuiLink>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, my: 0.5 }}>
                    <LinearProgress
                      variant="determinate"
                      value={barValue(goal.progressPercent)}
                      aria-label={t('progressAria', { name: goal.name })}
                      color={goal.targetReached ? 'success' : 'primary'}
                      sx={{ flexGrow: 1, height: 6, borderRadius: 3 }}
                    />
                    <Typography variant="body2">
                      {formatProgress(goal.progressPercent, locale)}%
                    </Typography>
                  </Box>
                  <GoalStatusLine goal={goal} />
                </li>
              ))}
            </Box>
            <MuiLink
              component={RouterLink}
              to="/goals"
              underline="hover"
              sx={{ display: 'inline-block', mt: 2 }}
            >
              {t('viewAll')}
            </MuiLink>
          </Box>
        )}
      </Paper>
    </Box>
  )
}
