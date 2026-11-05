import { useTranslation } from 'react-i18next'
import { Box, Button, Chip, Tooltip, Typography } from '@mui/material'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import { useLocale } from '../../providers/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { PageSection } from '../../components/layout/PageSection'

interface CurrentValueCardProps {
  holding: InvestmentHolding
  /** Name of the holding's account, shown beside the heading; omitted while accounts load. */
  accountName: string | undefined
  canTrade: boolean
  onTrade: (direction: 'buy' | 'sell') => void
}

/** The selected holding's status, latest snapshot value and the Buy/Sell entry points. */
export function CurrentValueCard({
  holding,
  accountName,
  canTrade,
  onTrade,
}: CurrentValueCardProps) {
  const { t } = useTranslation('investmentProducts')
  const { locale } = useLocale()
  return (
    <PageSection>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <Typography variant="overline" color="text.secondary">
          {t('detail.currentValue')} {accountName ? `(${accountName})` : ''}
        </Typography>
        {holding.closed ? (
          <Chip label={t('detail.statusClosed')} size="small" />
        ) : (
          <Chip label={t('detail.statusOpen')} size="small" color="success" />
        )}
        {holding.needsSnapshot && (
          <Tooltip title={t('detail.needsSnapshotTooltip')}>
            <Chip label={t('detail.needsSnapshotChip')} size="small" color="warning" />
          </Tooltip>
        )}
      </Box>
      <Typography variant="h3" sx={{ mb: 1, mt: 1 }}>
        {holding.latestSnapshot ? formatMoney(holding.latestSnapshot.balance, locale) : '-'}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {holding.latestSnapshot
          ? t('detail.latestSnapshotLine', { date: holding.latestSnapshot.date })
          : t('detail.noSnapshotYet')}
      </Typography>
      <Box sx={{ display: 'flex', gap: 2 }}>
        <Button variant="contained" disabled={!canTrade} onClick={() => onTrade('buy')}>
          {t('detail.buyButton')}
        </Button>
        <Button variant="outlined" disabled={!canTrade} onClick={() => onTrade('sell')}>
          {t('detail.sellButton')}
        </Button>
      </Box>
    </PageSection>
  )
}
