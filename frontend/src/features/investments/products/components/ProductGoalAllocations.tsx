import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import { Box, Chip, Typography } from '@mui/material'
import { useProductGoalAllocations } from '../../../../api/goals/goalsQueries'
import { formatNumber } from '../../../../i18n/format'
import { useLocale } from '../../../../providers/locale/useLocale'

/**
 * The goals a product is allocated to (PRD S6.17, F039), read-only: one chip per non-archived
 * goal, "Car · 60%", linking to the goal where the split is edited. Renders nothing while loading,
 * on a failed read or when no goal uses the product, since it is a hint and not a section the page
 * depends on.
 */
export function ProductGoalAllocations({ productId }: { productId: string }) {
  const { t } = useTranslation('investmentProducts')
  const { locale } = useLocale()
  const allocations = useProductGoalAllocations(productId).data

  if (!allocations || allocations.length === 0) return null
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', mb: 2 }}>
      <Typography color="text.secondary">{t('detail.allocatedTo')}</Typography>
      {allocations.map((allocation) => (
        <Chip
          key={allocation.goalId}
          component={RouterLink}
          to={`/goals/${allocation.goalId}`}
          clickable
          size="small"
          label={t('detail.goalChip', {
            name: allocation.goalName,
            percent: formatNumber(
              allocation.percentage,
              locale,
              Number.isInteger(allocation.percentage) ? 0 : 2,
            ),
          })}
        />
      ))}
    </Box>
  )
}
