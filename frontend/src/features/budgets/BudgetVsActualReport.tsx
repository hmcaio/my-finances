import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, LinearProgress, Paper, Skeleton, Typography } from '@mui/material'
import { useBudgetReport } from '../../api/budgets/budgetsQueries'
import { useCategories } from '../../api/categories/categoriesQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'

interface BudgetVsActualReportProps {
  /** The month to report on, `YYYY-MM`. */
  month: string
}

/**
 * Budget-vs-actual bars for every budgeted category for `month` (F006 spec), used by the budgets
 * page (with its month picker) and embedded by F012's dashboard (current month). Fetches its own
 * data; an over-cap category is flagged in red.
 */
export function BudgetVsActualReport({ month }: BudgetVsActualReportProps) {
  const { t } = useTranslation(['budgets', 'common'])
  const { locale } = useLocale()
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery)
  const reportQuery = useBudgetReport(month)
  const report = reportQuery.data
  const reportState = useQueryState(reportQuery)

  // The report also names categories, so it waits for that list too (no raw ids in the lines).
  const view = combineLoadState(categoriesState, reportState)
  const showSkeleton = useDelayedFlag(view.loading)
  const categoryName = useMemo(
    () => nameLookup(categories ?? [], (c) => translateSeedName('categories', c.name, t)),
    [categories, t],
  )

  return (
    <Paper variant="outlined" sx={{ p: 2 }} aria-busy={view.loading}>
      {showSkeleton && <BudgetReportSkeleton />}
      {view.loadError && <LoadFailedNotice message={view.loadError} onRetry={view.reload} />}
      {report !== undefined && categories !== undefined && (
        <Box sx={fadeInSx}>
          {report.length === 0 && (
            <Typography color="text.secondary">{t('budgets:report.emptyMonth')}</Typography>
          )}
          {report.map((line) => {
            const overCap = line.cap !== null && line.actual > line.cap
            const progress =
              line.cap !== null && line.cap > 0 ? Math.min(100, (line.actual / line.cap) * 100) : 0
            return (
              <Box key={line.categoryId} sx={{ mb: 2 }}>
                <Box
                  sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    justifyContent: 'space-between',
                    gap: 1,
                    mb: 0.5,
                  }}
                >
                  <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                    {categoryName(line.categoryId)}
                  </Typography>
                  <Typography
                    variant="body2"
                    color={overCap ? 'error' : 'text.secondary'}
                    sx={{ fontWeight: overCap ? 'bold' : undefined, overflowWrap: 'anywhere' }}
                  >
                    {t('budgets:report.lineAmounts', {
                      actual: formatMoney(line.actual, locale),
                      cap:
                        line.cap !== null
                          ? formatMoney(line.cap, locale)
                          : t('budgets:report.noCap'),
                    })}
                    {overCap && t('budgets:report.overBudgetSuffix')}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={progress}
                  color={overCap ? 'error' : 'primary'}
                  aria-label={t('budgets:report.usageAria', {
                    category: categoryName(line.categoryId),
                  })}
                />
              </Box>
            )
          })}
        </Box>
      )}
    </Paper>
  )
}

/** Placeholder for the budget-vs-actual report, sized like three report lines. */
function BudgetReportSkeleton() {
  const { t } = useTranslation('budgets')
  return (
    <Box role="status" aria-label={t('report.loadingAria')}>
      {[0, 1, 2].map((line) => (
        <Box key={line} sx={{ mb: 2 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
            <Skeleton variant="text" width="25%" sx={{ typography: 'body2' }} />
            <Skeleton variant="text" width="15%" sx={{ typography: 'body2' }} />
          </Box>
          <Skeleton variant="rounded" height={4} />
        </Box>
      ))}
    </Box>
  )
}
