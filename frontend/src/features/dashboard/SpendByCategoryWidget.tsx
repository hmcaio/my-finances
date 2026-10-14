import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, LinearProgress, Skeleton, Typography } from '@mui/material'
import { useCategories } from '../../api/categories/categoriesQueries'
import { useSpendByCategory } from '../../api/transactions/transactionsQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { translateSeedName } from '../../i18n/seedNames'
import { currentMonth } from '../../utils/localDate'
import { nameLookup } from '../../utils/nameLookup'

/**
 * "Spend by category" for the current month (F012 spec): expense totals per category, largest
 * first, each with a bar relative to the biggest one. A prop-less widget that fetches its own data
 * on mount (frontend `CLAUDE.md`, "Pages that embed other pages' widgets"). Unlike the budget
 * report it lists every category with spending, budgeted or not.
 */
export function SpendByCategoryWidget() {
  const { t } = useTranslation('dashboard')
  const { locale } = useLocale()
  const spendQuery = useSpendByCategory(currentMonth())
  const spend = spendQuery.data
  const spendState = useQueryState(spendQuery)
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery)
  // Names are needed to render a row, so both fetches gate it (no raw ids on screen).
  const state = combineLoadState(categoriesState, spendState)
  const showSkeleton = useDelayedFlag(state.loading)
  const categoryName = useMemo(
    () => nameLookup(categories ?? [], (c) => translateSeedName('categories', c.name, t)),
    [categories, t],
  )

  if (state.loadError) return <LoadFailedNotice message={state.loadError} onRetry={state.reload} />
  if (spend === undefined || categories === undefined) {
    return showSkeleton ? (
      <Box role="status" aria-label={t('spendByCategory.loadingAria')}>
        {[0, 1, 2].map((row) => (
          <Skeleton key={row} variant="text" sx={{ typography: 'body2' }} />
        ))}
      </Box>
    ) : null
  }
  if (spend.length === 0) {
    return <Typography color="text.secondary">{t('spendByCategory.emptyList')}</Typography>
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
              {formatMoney(row.total, locale)}
            </Typography>
          </Box>
          <LinearProgress
            variant="determinate"
            value={max > 0 ? (row.total / max) * 100 : 0}
            aria-label={`${categoryName(row.categoryId)}${t('spendByCategory.spendAriaSuffix')}`}
          />
        </Box>
      ))}
      <Typography variant="body2" sx={{ fontWeight: 'bold', textAlign: 'right' }}>
        {t('spendByCategory.totalLabel', { total: formatMoney(total, locale) })}
      </Typography>
    </Box>
  )
}
