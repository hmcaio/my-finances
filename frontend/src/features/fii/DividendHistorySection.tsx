import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  MenuItem,
  Paper,
  Select,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import {
  useDividendTotalsByMonth,
  useDividendTotalsByTicker,
  useDividends,
} from '../../api/investments/fiiDividendsQueries'
import type { InvestmentProduct } from '../../api/investments/investmentProducts'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'

interface DividendHistorySectionProps {
  fiiProducts: InvestmentProduct[]
}

/**
 * Dividend history (F026 spec): ticker/date-range filters, the matching dividend transactions,
 * and totals grouped by ticker and by month.
 */
export function DividendHistorySection({ fiiProducts }: DividendHistorySectionProps) {
  const { t } = useTranslation('fii')
  const { locale } = useLocale()
  const [productId, setProductId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const filter = { productId: productId || undefined, from: from || undefined, to: to || undefined }
  const dividendsQuery = useDividends(filter)
  const dividends = dividendsQuery.data
  const dividendsState = useQueryState(dividendsQuery)
  const totalsByTickerQuery = useDividendTotalsByTicker({ from: filter.from, to: filter.to })
  const totalsByTicker = totalsByTickerQuery.data ?? []
  const totalsByMonthQuery = useDividendTotalsByMonth({ from: filter.from, to: filter.to })
  const totalsByMonth = totalsByMonthQuery.data ?? []

  function retry() {
    dividendsState.reload()
  }

  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
        {t('dividendHistory.title')}
      </Typography>

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
        <Select
          size="small"
          displayEmpty
          value={productId}
          onChange={(e) => setProductId(e.target.value)}
          aria-label={t('dividendHistory.tickerAria')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">{t('dividendHistory.allTickers')}</MenuItem>
          {fiiProducts.map((product) => (
            <MenuItem key={product.id} value={product.id}>
              {product.ticker ?? product.name}
            </MenuItem>
          ))}
        </Select>
        <TextField
          label={t('dividendHistory.fromLabel')}
          size="small"
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          label={t('dividendHistory.toLabel')}
          size="small"
          type="date"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Box>

      <TableContainer sx={{ mb: 3 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('dividendHistory.columns.date')}</TableCell>
              <TableCell>{t('dividendHistory.columns.ticker')}</TableCell>
              <TableCell align="right">{t('dividendHistory.columns.amount')}</TableCell>
            </TableRow>
          </TableHead>
          <DataTableBody state={dividendsState} onRetry={retry} columns={3}>
            {dividends?.length === 0 && (
              <TableRow>
                <TableCell colSpan={3} align="center">
                  <Typography color="text.secondary">{t('dividendHistory.emptyList')}</Typography>
                </TableCell>
              </TableRow>
            )}
            {dividends?.map((dividend) => (
              <TableRow key={dividend.transactionId}>
                <TableCell>{dividend.date}</TableCell>
                <TableCell>{dividend.ticker ?? '-'}</TableCell>
                <TableCell align="right">{formatMoney(dividend.amount, locale)}</TableCell>
              </TableRow>
            ))}
          </DataTableBody>
        </Table>
      </TableContainer>

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            {t('dividendHistory.totalsByTickerTitle')}
          </Typography>
          <Table size="small">
            <TableBody>
              {totalsByTicker.map((total) => (
                <TableRow key={total.productId ?? total.ticker ?? 'unknown'}>
                  <TableCell>{total.ticker ?? '-'}</TableCell>
                  <TableCell align="right">{formatMoney(total.amount, locale)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            {t('dividendHistory.totalsByMonthTitle')}
          </Typography>
          <Table size="small">
            <TableBody>
              {totalsByMonth.map((total) => (
                <TableRow key={total.month}>
                  <TableCell>{total.month}</TableCell>
                  <TableCell align="right">{formatMoney(total.amount, locale)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      </Box>
    </Paper>
  )
}
