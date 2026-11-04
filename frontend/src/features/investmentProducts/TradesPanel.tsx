import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import { Box, Chip, Link as MuiLink, Paper, Typography } from '@mui/material'
import type { TradeConfirmationLineRecord } from '../../api/investments/tradeConfirmationLines'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import type { LoadState } from '../../hooks/queryState'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { SECTION_PADDING } from './detailLayout'

interface TradesPanelProps {
  lines: TradeConfirmationLineRecord[] | undefined
  state: LoadState
}

type TradeRow = TradeConfirmationLineRecord & { rowKey: string }

/**
 * F027 (ADR 0024): one row per matching line (not per confirmation); Taxes dropped - a
 * confirmation's taxes cover its whole settlement, not one product. Each row links back to its
 * parent confirmation so the real total taxes stay reachable.
 */
export function TradesPanel({ lines, state }: TradesPanelProps) {
  const { t } = useTranslation('investmentProducts')
  const { locale } = useLocale()
  // transferId alone isn't unique across rows (partial fills put more than one line under the
  // same confirmation), so each row gets a synthetic client-side key.
  const trades = lines?.map((line, i) => ({ ...line, rowKey: `${line.transferId}-${i}` }))
  const sideLabel = (side: TradeRow['side']) =>
    side === 'BUY' ? t('detail.sideBuy') : t('detail.sideSell')
  const tradeColumns: ResponsiveColumn<TradeRow>[] = [
    {
      key: 'date',
      header: t('detail.tradeColumns.date'),
      role: 'secondary',
      render: (trade) => trade.date,
    },
    {
      key: 'side',
      header: t('detail.tradeColumns.side'),
      render: (trade) => sideLabel(trade.side),
    },
    {
      key: 'quantity',
      header: t('detail.tradeColumns.quantity'),
      align: 'right',
      render: (trade) => String(trade.quantity),
    },
    {
      key: 'unitPrice',
      header: t('detail.tradeColumns.unitPrice'),
      align: 'right',
      tabletPriority: 'low',
      render: (trade) => String(trade.unitPrice),
    },
    {
      key: 'resultingBalance',
      header: t('detail.tradeColumns.resultingBalance'),
      align: 'right',
      tabletPriority: 'low',
      render: (trade) =>
        trade.resultingBalance === null ? '-' : formatMoney(trade.resultingBalance, locale),
    },
    {
      key: 'confirmation',
      header: t('detail.tradeColumns.confirmation'),
      role: 'primary',
      render: (trade) => (
        <MuiLink component={RouterLink} to={`/ledger?focus=${trade.transferId}`} underline="hover">
          {t('detail.viewConfirmation')}
        </MuiLink>
      ),
    },
  ]

  function renderTradeCard(trade: TradeRow) {
    return (
      <Paper variant="outlined" sx={{ p: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
          <Typography variant="subtitle1" component="div">
            {trade.date}
          </Typography>
          <Chip label={sideLabel(trade.side)} size="small" />
        </Box>
        <Typography variant="body2" color="text.secondary">
          {t('detail.tradeCardLine', {
            quantity: trade.quantity,
            price: trade.unitPrice,
          })}
          {trade.resultingBalance !== null &&
            t('detail.tradeCardResultingBalance', {
              balance: formatMoney(trade.resultingBalance, locale),
            })}
        </Typography>
        <MuiLink component={RouterLink} to={`/ledger?focus=${trade.transferId}`} underline="hover">
          {t('detail.viewConfirmation')}
        </MuiLink>
      </Paper>
    )
  }

  return (
    <Paper variant="outlined" sx={{ p: SECTION_PADDING }}>
      <Typography variant="h6" gutterBottom>
        {t('detail.tradesTitle')}
      </Typography>
      <ResponsiveTable
        aria-label={t('detail.tradesAria')}
        columns={tradeColumns}
        rows={trades}
        getRowKey={(trade) => trade.rowKey}
        state={state}
        onRetry={state.reload}
        renderCard={renderTradeCard}
        emptyMessage={t('detail.emptyTrades')}
      />
    </Paper>
  )
}
