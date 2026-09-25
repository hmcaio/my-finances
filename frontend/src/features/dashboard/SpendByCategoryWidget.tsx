import { useMemo } from 'react'
import { Box, LinearProgress, Skeleton, Typography } from '@mui/material'
import { useCategories } from '../../api/categoriesQueries'
import { useSpendByCategory } from '../../api/transactionsQueries'
import { fadeInSx } from '../../components/fadeIn'
import { LoadFailedNotice } from '../../components/LoadFailedNotice'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { currentMonth } from '../../utils/localDate'
import { nameLookup } from '../../utils/nameLookup'

/**
 * "Spend by category" for the current month (F012 spec): expense totals per category, largest
 * first, each with a bar relative to the biggest one. A prop-less widget that fetches its own data
 * on mount (frontend `CLAUDE.md`, "Pages that embed other pages' widgets"). Unlike the budget
 * report it lists every category with spending, budgeted or not.
 */
export function SpendByCategoryWidget() {
  const spendQuery = useSpendByCategory(currentMonth())
  const spend = spendQuery.data
  const spendState = useQueryState(spendQuery)
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery)
  // Names are needed to render a row, so both fetches gate it (no raw ids on screen).
  const state = combineLoadState(categoriesState, spendState)
  const showSkeleton = useDelayedFlag(state.loading)
  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])

  if (state.loadError) return <LoadFailedNotice message={state.loadError} onRetry={state.reload} />
  if (spend === undefined || categories === undefined) {
    return showSkeleton ? (
      <Box role="status" aria-label="Loading spend by category">
        {[0, 1, 2].map((row) => (
          <Skeleton key={row} variant="text" sx={{ typography: 'body2' }} />
        ))}
      </Box>
    ) : null
  }
  if (spend.length === 0) {
    return <Typography color="text.secondary">Nothing spent this month yet.</Typography>
  }

  const max = Math.max(...spend.map((row) => row.total))
  const total = spend.reduce((sum, row) => sum + row.total, 0)
  return (
    <Box sx={fadeInSx}>
      {spend.map((row) => (
        <Box key={row.categoryId} sx={{ mb: 1.5 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
            <Typography variant="body2">{categoryName(row.categoryId)}</Typography>
            <Typography variant="body2" color="text.secondary">
              {row.total.toFixed(2)}
            </Typography>
          </Box>
          <LinearProgress
            variant="determinate"
            value={max > 0 ? (row.total / max) * 100 : 0}
            aria-label={`${categoryName(row.categoryId)} spend`}
          />
        </Box>
      ))}
      <Typography variant="body2" sx={{ fontWeight: 'bold', textAlign: 'right' }}>
        Total {total.toFixed(2)}
      </Typography>
    </Box>
  )
}
