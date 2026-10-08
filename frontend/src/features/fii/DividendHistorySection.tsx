import { useState } from 'react'
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

interface DividendHistorySectionProps {
  fiiProducts: InvestmentProduct[]
}

/**
 * Dividend history (F026 spec): ticker/date-range filters, the matching dividend transactions,
 * and totals grouped by ticker and by month.
 */
export function DividendHistorySection({ fiiProducts }: DividendHistorySectionProps) {
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
        Dividend history
      </Typography>

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
        <Select
          size="small"
          displayEmpty
          value={productId}
          onChange={(e) => setProductId(e.target.value)}
          aria-label="Ticker"
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All tickers</MenuItem>
          {fiiProducts.map((product) => (
            <MenuItem key={product.id} value={product.id}>
              {product.ticker ?? product.name}
            </MenuItem>
          ))}
        </Select>
        <TextField
          label="From"
          size="small"
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          label="To"
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
              <TableCell>Date</TableCell>
              <TableCell>Ticker</TableCell>
              <TableCell align="right">Amount</TableCell>
            </TableRow>
          </TableHead>
          <DataTableBody state={dividendsState} onRetry={retry} columns={3}>
            {dividends?.length === 0 && (
              <TableRow>
                <TableCell colSpan={3} align="center">
                  <Typography color="text.secondary">No dividends recorded yet.</Typography>
                </TableCell>
              </TableRow>
            )}
            {dividends?.map((dividend) => (
              <TableRow key={dividend.transactionId}>
                <TableCell>{dividend.date}</TableCell>
                <TableCell>{dividend.ticker ?? '-'}</TableCell>
                <TableCell align="right">{dividend.amount.toFixed(2)}</TableCell>
              </TableRow>
            ))}
          </DataTableBody>
        </Table>
      </TableContainer>

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Totals by ticker
          </Typography>
          <Table size="small">
            <TableBody>
              {totalsByTicker.map((t) => (
                <TableRow key={t.productId ?? t.ticker ?? 'unknown'}>
                  <TableCell>{t.ticker ?? '-'}</TableCell>
                  <TableCell align="right">{t.amount.toFixed(2)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Totals by month
          </Typography>
          <Table size="small">
            <TableBody>
              {totalsByMonth.map((t) => (
                <TableRow key={t.month}>
                  <TableCell>{t.month}</TableCell>
                  <TableCell align="right">{t.amount.toFixed(2)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      </Box>
    </Paper>
  )
}
