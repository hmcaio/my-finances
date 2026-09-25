import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogContent,
  DialogTitle,
  Link as MuiLink,
  Paper,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useAccounts } from '../../api/accountsQueries'
import { defaultErrorMessage } from '../../api/apiError'
import { useInvestmentProduct } from '../../api/investmentProductsQueries'
import type { InvestmentSnapshot } from '../../api/investmentSnapshots'
import {
  useInvestmentSnapshots,
  useRecordInvestmentSnapshot,
} from '../../api/investmentSnapshotsQueries'
import { useInvestmentValueSeries } from '../../api/investmentValueSeriesQueries'
import type { Transfer } from '../../api/transfers'
import { useTransfers } from '../../api/transfersQueries'
import { DataTableBody } from '../../components/DataTableBody'
import { ErrorAlert } from '../../components/ErrorAlert'
import { fadeInSx } from '../../components/fadeIn'
import { PaginationControls } from '../../components/PaginationControls'
import { combineLoadState, useQueryState, type LoadState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { today } from '../../utils/localDate'
import { TransferForm, type TransferFormPreset } from '../transfers/TransferForm'
import { ValueSeriesChart } from './ValueSeriesChart'

const TRADES_PAGE_SIZE = 10

/**
 * Per-product detail view under F008's product screens (F009 spec): the latest value with the
 * `needsSnapshot` badge, Buy/Sell buttons (the transfer form in a dialog, direction and product
 * preset), a snapshot entry form with its history, the product's trade history (transfers filtered
 * by product) and the value/contribution chart with its raw monthly numbers.
 */
export function InvestmentProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const productId = id ?? ''
  const [error, setError] = useState<string | null>(null)

  const productQuery = useInvestmentProduct(id)
  const product = productQuery.data
  const productState = useQueryState(productQuery, undefined, (err) =>
    defaultErrorMessage(err, { 404: 'Investment product not found.' }),
  )
  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const snapshotsQuery = useInvestmentSnapshots(productId)
  const snapshots = snapshotsQuery.data
  const snapshotsState = useQueryState(snapshotsQuery, setError)
  const seriesQuery = useInvestmentValueSeries({ productId })
  const series = seriesQuery.data
  const seriesState = useQueryState(seriesQuery, setError)
  const [tradesPage, setTradesPage] = useState(0)
  const tradesQuery = useTransfers({ investmentProductId: productId }, tradesPage, TRADES_PAGE_SIZE)
  const trades = tradesQuery.data?.content
  const pageInfo = tradesQuery.data
    ? { number: tradesQuery.data.page.number, totalPages: tradesQuery.data.page.totalPages }
    : null
  const tradesState = useQueryState(tradesQuery, setError)
  const recordSnapshot = useRecordInvestmentSnapshot()

  const [snapshotForm, setSnapshotForm] = useState(() => ({ date: today(), balance: '' }))
  const [recording, setRecording] = useState(false)
  const [tradeDialog, setTradeDialog] = useState<TransferFormPreset | null>(null)
  // The dialog form remounts fresh for each Buy/Sell click.
  const [dialogKey, setDialogKey] = useState(0)

  const { loading, loadError } = combineLoadState(productState)
  const showSkeleton = useDelayedFlag(loading)
  const account = accounts?.find((a) => a.id === product?.accountId)
  const accountClosed = account?.closed ?? false
  const canTrade =
    product !== undefined && !product.closed && !accountClosed && accounts !== undefined

  const points = series?.[0]?.points ?? []
  const hasUnits = points.some((p) => p.units !== null)

  async function handleRecordSnapshot() {
    if (!product) return
    const balance = Number(snapshotForm.balance)
    if (snapshotForm.date === '' || snapshotForm.balance === '' || !(balance >= 0)) return
    setError(null)
    setRecording(true)
    try {
      // A same-day entry replaces the earlier one; the refetch after the write reorders the list.
      await recordSnapshot.mutateAsync({ productId: product.id, date: snapshotForm.date, balance })
      setSnapshotForm((prev) => ({ ...prev, balance: '' }))
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setRecording(false)
    }
  }

  function openTrade(direction: 'buy' | 'sell') {
    if (!product) return
    setError(null)
    setDialogKey((n) => n + 1)
    setTradeDialog({
      direction,
      investmentAccountId: product.accountId,
      productId: product.id,
      productName: product.name,
    })
  }

  function handleTradeSaved() {
    setTradeDialog(null)
    setTradesPage(0)
  }

  // Direction is derived, never stored: a transfer into the product's own account is a buy.
  function tradeDirection(trade: Transfer): string {
    return trade.toAccountId === product?.accountId ? 'Buy' : 'Sell'
  }

  return (
    <Box sx={{ py: 4 }}>
      <MuiLink
        component={RouterLink}
        to={product ? `/accounts/${product.accountId}` : '/accounts'}
        underline="hover"
      >
        &larr; Back to account
      </MuiLink>

      {loadError && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {loadError}
        </Alert>
      )}
      {showSkeleton && (
        <Box role="status" aria-label="Loading product" sx={{ mt: 2 }}>
          <Skeleton variant="text" width={280} sx={{ typography: 'h4' }} />
          <Skeleton variant="rounded" height={160} sx={{ mt: 2 }} />
        </Box>
      )}

      {product && (
        <Box sx={fadeInSx}>
          <Box
            sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2, mb: 1, flexWrap: 'wrap' }}
          >
            <Typography variant="h4" component="h1">
              {product.name}
            </Typography>
            {product.closed ? <Chip label="Closed" /> : <Chip label="Open" color="success" />}
            {product.needsSnapshot && (
              <Tooltip title="A buy or sell is newer than the latest snapshot, so the value shown may be out of date. Record a snapshot to refresh it.">
                <Chip label="Needs snapshot" color="warning" />
              </Tooltip>
            )}
          </Box>
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            {account ? `${account.name} · ` : ''}
            {product.closed ? `Closed ${product.closedDate}` : 'Open'}
          </Typography>

          <ErrorAlert message={tradeDialog ? null : error} onDismiss={() => setError(null)} />

          <Paper variant="outlined" sx={{ p: 3, mb: 3, maxWidth: 520 }}>
            <Typography variant="overline" color="text.secondary">
              Current value
            </Typography>
            <Typography variant="h3" sx={{ mb: 1 }}>
              {product.latestSnapshot ? product.latestSnapshot.balance.toFixed(2) : '-'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {product.latestSnapshot
                ? `Latest snapshot ${product.latestSnapshot.date}`
                : 'No snapshot yet: record one below.'}
            </Typography>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <Button variant="contained" disabled={!canTrade} onClick={() => openTrade('buy')}>
                Buy
              </Button>
              <Button variant="outlined" disabled={!canTrade} onClick={() => openTrade('sell')}>
                Sell
              </Button>
            </Box>
          </Paper>

          <Paper variant="outlined" sx={{ p: 3, mb: 3 }}>
            <Typography variant="h6" gutterBottom>
              Value over time
            </Typography>
            {seriesState.loading ? (
              <Skeleton variant="rounded" height={200} />
            ) : (
              <>
                <ValueSeriesChart points={points} />
                <TableContainer sx={{ mt: 2 }}>
                  <Table size="small" aria-label="Monthly values">
                    <TableHead>
                      <TableRow>
                        <TableCell>Month</TableCell>
                        <TableCell align="right">Value</TableCell>
                        <TableCell align="right">Contributed</TableCell>
                        {hasUnits && <TableCell align="right">Units</TableCell>}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {points.map((p) => (
                        <TableRow key={p.month}>
                          <TableCell>{p.month}</TableCell>
                          <TableCell align="right">
                            {p.value === null ? '-' : p.value.toFixed(2)}
                          </TableCell>
                          <TableCell align="right">{p.contributed.toFixed(2)}</TableCell>
                          {hasUnits && (
                            <TableCell align="right">
                              {p.units === null ? '-' : String(p.units)}
                            </TableCell>
                          )}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </>
            )}
          </Paper>

          <Paper variant="outlined" sx={{ p: 3, mb: 3 }}>
            <Typography variant="h6" gutterBottom>
              Snapshots
            </Typography>
            <Box
              role="group"
              aria-label="Record snapshot"
              sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap', mb: 2 }}
            >
              <TextField
                label="Snapshot date"
                type="date"
                size="small"
                value={snapshotForm.date}
                onChange={(e) => setSnapshotForm((prev) => ({ ...prev, date: e.target.value }))}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label="Balance"
                type="number"
                size="small"
                value={snapshotForm.balance}
                onChange={(e) => setSnapshotForm((prev) => ({ ...prev, balance: e.target.value }))}
                helperText="0 records a liquidated position. A second entry for the same day replaces the first."
                slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
                sx={{ minWidth: 240 }}
              />
              <Button
                variant="contained"
                disabled={
                  recording ||
                  snapshotForm.date === '' ||
                  snapshotForm.balance === '' ||
                  !(Number(snapshotForm.balance) >= 0)
                }
                onClick={() => void handleRecordSnapshot()}
              >
                Record snapshot
              </Button>
            </Box>
            <SnapshotHistory
              snapshots={snapshots}
              state={snapshotsState}
              onRetry={snapshotsState.reload}
            />
          </Paper>

          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Trades
            </Typography>
            <TableContainer>
              <Table size="small" aria-label="Trades">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell align="right">Amount</TableCell>
                    <TableCell align="right">Quantity</TableCell>
                    <TableCell align="right">Unit price</TableCell>
                    <TableCell align="right">Taxes</TableCell>
                    <TableCell>Description</TableCell>
                  </TableRow>
                </TableHead>
                <DataTableBody
                  state={combineLoadState(tradesState, accountsState)}
                  onRetry={() => {
                    tradesState.reload()
                    accountsState.reload()
                  }}
                  columns={7}
                >
                  {trades?.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7} align="center">
                        <Typography color="text.secondary">No trades yet.</Typography>
                      </TableCell>
                    </TableRow>
                  )}
                  {trades?.map((trade) => (
                    <TableRow key={trade.id}>
                      <TableCell>{trade.date}</TableCell>
                      <TableCell>{tradeDirection(trade)}</TableCell>
                      <TableCell align="right">{trade.amount.toFixed(2)}</TableCell>
                      <TableCell align="right">
                        {trade.quantity === null ? '-' : String(trade.quantity)}
                      </TableCell>
                      <TableCell align="right">
                        {trade.unitPrice === null ? '-' : String(trade.unitPrice)}
                      </TableCell>
                      <TableCell align="right">
                        {trade.taxes === null ? '-' : trade.taxes.toFixed(2)}
                      </TableCell>
                      <TableCell>{trade.description}</TableCell>
                    </TableRow>
                  ))}
                </DataTableBody>
              </Table>
            </TableContainer>
            <PaginationControls pageInfo={pageInfo} onPageChange={setTradesPage} />
          </Paper>
        </Box>
      )}

      <Dialog
        open={tradeDialog !== null}
        onClose={() => setTradeDialog(null)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          {tradeDialog?.direction === 'sell' ? 'Sell' : 'Buy'} {product?.name}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            {tradeDialog && accounts && (
              <TransferForm
                key={dialogKey}
                accounts={accounts}
                preset={tradeDialog}
                onSaved={handleTradeSaved}
                onError={setError}
                onCancel={() => setTradeDialog(null)}
              />
            )}
          </Box>
        </DialogContent>
      </Dialog>
    </Box>
  )
}

interface SnapshotHistoryProps {
  snapshots: InvestmentSnapshot[] | undefined
  state: LoadState
  onRetry: () => void
}

/** The product's snapshots, most recent first. */
function SnapshotHistory({ snapshots, state, onRetry }: SnapshotHistoryProps) {
  return (
    <TableContainer>
      <Table size="small" aria-label="Snapshot history">
        <TableHead>
          <TableRow>
            <TableCell>Date</TableCell>
            <TableCell align="right">Balance</TableCell>
          </TableRow>
        </TableHead>
        <DataTableBody state={state} onRetry={onRetry} columns={2}>
          {snapshots?.length === 0 && (
            <TableRow>
              <TableCell colSpan={2} align="center">
                <Typography color="text.secondary">No snapshots yet.</Typography>
              </TableCell>
            </TableRow>
          )}
          {snapshots?.map((snapshot) => (
            <TableRow key={snapshot.id}>
              <TableCell>{snapshot.date}</TableCell>
              <TableCell align="right">{snapshot.balance.toFixed(2)}</TableCell>
            </TableRow>
          ))}
        </DataTableBody>
      </Table>
    </TableContainer>
  )
}
