import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import {
  Alert,
  Box,
  Button,
  Chip,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Link as MuiLink,
  Paper,
  Skeleton,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInvestmentProduct } from '../../api/investments/investmentProductsQueries'
import type { InvestmentSnapshot } from '../../api/investments/investmentSnapshots'
import {
  useDeleteInvestmentSnapshot,
  useInvestmentSnapshots,
  useRecordInvestmentSnapshot,
  useUpdateInvestmentSnapshot,
} from '../../api/investments/investmentSnapshotsQueries'
import { useInvestmentValueSeries } from '../../api/investments/investmentValueSeriesQueries'
import type { ValueSeriesPoint } from '../../api/investments/investmentValueSeries'
import type { Transfer } from '../../api/transfers/transfers'
import { useTransfers } from '../../api/transfers/transfersQueries'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { DataTableBody } from '../../components/table/DataTableBody'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { PaginationControls } from '../../components/table/PaginationControls'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState, type LoadState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { today } from '../../utils/localDate'
import { TransferForm, type TransferFormPreset } from '../transfers/TransferForm'
import { ValueSeriesChart } from './ValueSeriesChart'

const TRADES_PAGE_SIZE = 10

/** Tighter section padding on phones (F021), matching `AccountDetailPage`. */
const SECTION_PADDING = { xs: 2, sm: 3 }

/**
 * Per-product detail view under F008's product screens (F009 spec): the latest value with the
 * `needsSnapshot` badge, Buy/Sell buttons (the transfer form in a dialog, direction and product
 * preset), a snapshot entry form with its history, the product's trade history (transfers filtered
 * by product) and the value/contribution chart with its raw monthly numbers.
 *
 * Responsive (F021): section padding tightens on phones. The header's "Record snapshot" button
 * opens its two fields in a `ResponsiveDialog` (the snapshot history table itself stays a plain
 * table - only two data columns, per the 1-2-column rule). The monthly-values and trades tables (3+
 * columns) become `ResponsiveTable`s (cards below `sm`; trades hide Quantity/Unit price/Taxes on
 * tablet, the record-only trade details, behind the row expander). The Buy/Sell dialog now uses
 * `TransferForm`'s `dialog` mode, like `TransfersPage` - it no longer has an inline-only layout.
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
  const updateSnapshot = useUpdateInvestmentSnapshot()
  const deleteSnapshot = useDeleteInvestmentSnapshot()

  const [snapshotForm, setSnapshotForm] = useState(() => ({ date: today(), balance: '' }))
  const [snapshotDialogOpen, setSnapshotDialogOpen] = useState(false)
  const [recording, setRecording] = useState(false)
  const [editing, setEditing] = useState<SnapshotEdit | null>(null)
  const [savingEdit, setSavingEdit] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<InvestmentSnapshot | null>(null)
  const [deleting, setDeleting] = useState(false)
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

  // While either dialog is open, a save error shows inside it: the page banner sits behind it.
  const anyDialogOpen = tradeDialog !== null || snapshotDialogOpen

  function openSnapshotDialog() {
    setError(null)
    setSnapshotForm({ date: today(), balance: '' })
    setSnapshotDialogOpen(true)
  }

  function closeSnapshotDialog() {
    setSnapshotDialogOpen(false)
    setError(null)
  }

  async function handleRecordSnapshot() {
    if (!product) return
    const balance = Number(snapshotForm.balance)
    if (snapshotForm.date === '' || snapshotForm.balance === '' || !(balance >= 0)) return
    setError(null)
    setRecording(true)
    try {
      // A same-day entry replaces the earlier one; the refetch after the write reorders the list.
      await recordSnapshot.mutateAsync({ productId: product.id, date: snapshotForm.date, balance })
      setSnapshotDialogOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setRecording(false)
    }
  }

  async function handleSaveEdit() {
    if (!product || !editing) return
    const balance = Number(editing.balance)
    if (editing.date === '' || editing.balance === '' || !(balance >= 0)) return
    setError(null)
    setSavingEdit(true)
    try {
      await updateSnapshot.mutateAsync({
        productId: product.id,
        snapshotId: editing.id,
        date: editing.date,
        balance,
      })
      setEditing(null)
    } catch (err) {
      setError(defaultErrorMessage(err, { 404: 'Snapshot not found.' }))
    } finally {
      setSavingEdit(false)
    }
  }

  async function handleConfirmDelete() {
    if (!product || !deleteTarget) return
    setError(null)
    setDeleting(true)
    try {
      await deleteSnapshot.mutateAsync({ productId: product.id, snapshotId: deleteTarget.id })
    } catch (err) {
      setError(defaultErrorMessage(err, { 404: 'Snapshot not found.' }))
    } finally {
      setDeleting(false)
      setDeleteTarget(null)
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

  const monthlyColumns: ResponsiveColumn<ValueSeriesPoint>[] = [
    { key: 'month', header: 'Month', role: 'primary', render: (p) => p.month },
    {
      key: 'value',
      header: 'Value',
      align: 'right',
      render: (p) => (p.value === null ? '-' : p.value.toFixed(2)),
    },
    {
      key: 'contributed',
      header: 'Contributed',
      align: 'right',
      render: (p) => p.contributed.toFixed(2),
    },
    ...(hasUnits
      ? ([
          {
            key: 'units',
            header: 'Units',
            align: 'right',
            render: (p: ValueSeriesPoint) => (p.units === null ? '-' : String(p.units)),
          },
        ] satisfies ResponsiveColumn<ValueSeriesPoint>[])
      : []),
  ]

  const tradeColumns: ResponsiveColumn<Transfer>[] = [
    { key: 'date', header: 'Date', role: 'secondary', render: (t) => t.date },
    { key: 'type', header: 'Type', render: (t) => tradeDirection(t) },
    { key: 'amount', header: 'Amount', align: 'right', render: (t) => t.amount.toFixed(2) },
    {
      key: 'quantity',
      header: 'Quantity',
      align: 'right',
      tabletPriority: 'low',
      render: (t) => (t.quantity === null ? '-' : String(t.quantity)),
    },
    {
      key: 'unitPrice',
      header: 'Unit price',
      align: 'right',
      tabletPriority: 'low',
      render: (t) => (t.unitPrice === null ? '-' : String(t.unitPrice)),
    },
    {
      key: 'taxes',
      header: 'Taxes',
      align: 'right',
      tabletPriority: 'low',
      render: (t) => (t.taxes === null ? '-' : t.taxes.toFixed(2)),
    },
    { key: 'description', header: 'Description', role: 'primary', render: (t) => t.description },
  ]

  // The card leads with the description and derived direction, then the record-only trade details.
  function renderTradeCard(trade: Transfer) {
    return (
      <Paper variant="outlined" sx={{ p: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
          <Typography variant="subtitle1" component="div" sx={{ overflowWrap: 'anywhere' }}>
            {trade.description}
          </Typography>
          <Chip label={tradeDirection(trade)} size="small" />
        </Box>
        <Typography variant="body2" color="text.secondary">
          {trade.date} · Amount {trade.amount.toFixed(2)}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Quantity {trade.quantity === null ? '-' : String(trade.quantity)} · Unit price{' '}
          {trade.unitPrice === null ? '-' : String(trade.unitPrice)} · Taxes{' '}
          {trade.taxes === null ? '-' : trade.taxes.toFixed(2)}
        </Typography>
      </Paper>
    )
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
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
            <Typography variant="h4" component="h1" sx={{ overflowWrap: 'anywhere' }}>
              {product.name}
            </Typography>
            {product.closed ? <Chip label="Closed" /> : <Chip label="Open" color="success" />}
            {product.needsSnapshot && (
              <Tooltip title="A buy or sell is newer than the latest snapshot, so the value shown may be out of date. Record a snapshot to refresh it.">
                <Chip label="Needs snapshot" color="warning" />
              </Tooltip>
            )}
          </Box>
          <Typography color="text.secondary" sx={{ mb: 3, overflowWrap: 'anywhere' }}>
            {account ? `${account.name} · ` : ''}
            {product.closed ? `Closed ${product.closedDate}` : 'Open'}
          </Typography>

          <ErrorAlert message={anyDialogOpen ? null : error} onDismiss={() => setError(null)} />

          <Paper variant="outlined" sx={{ p: SECTION_PADDING, mb: 3, maxWidth: 520 }}>
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

          <Paper variant="outlined" sx={{ p: SECTION_PADDING, mb: 3 }}>
            <Typography variant="h6" gutterBottom>
              Value over time
            </Typography>
            {seriesState.loading ? (
              <Skeleton variant="rounded" height={200} />
            ) : (
              <>
                <ValueSeriesChart points={points} />
                <Box sx={{ mt: 2 }}>
                  <ResponsiveTable
                    aria-label="Monthly values"
                    columns={monthlyColumns}
                    rows={points}
                    getRowKey={(p) => p.month}
                    state={seriesState}
                    onRetry={seriesState.reload}
                    emptyMessage="No data for this period."
                  />
                </Box>
              </>
            )}
          </Paper>

          <Paper variant="outlined" sx={{ p: SECTION_PADDING, mb: 3 }}>
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 2,
                mb: 2,
              }}
            >
              <Typography variant="h6">Snapshots</Typography>
              <Button variant="contained" onClick={openSnapshotDialog}>
                Record snapshot
              </Button>
            </Box>
            <SnapshotHistory
              snapshots={snapshots}
              state={snapshotsState}
              onRetry={snapshotsState.reload}
              editing={editing}
              onEditingChange={setEditing}
              onSaveEdit={() => void handleSaveEdit()}
              savingEdit={savingEdit}
              onDelete={setDeleteTarget}
            />
          </Paper>

          <Paper variant="outlined" sx={{ p: SECTION_PADDING }}>
            <Typography variant="h6" gutterBottom>
              Trades
            </Typography>
            <ResponsiveTable
              aria-label="Trades"
              columns={tradeColumns}
              rows={trades}
              getRowKey={(t) => t.id}
              state={combineLoadState(tradesState, accountsState)}
              onRetry={() => {
                tradesState.reload()
                accountsState.reload()
              }}
              renderCard={renderTradeCard}
              emptyMessage="No trades yet."
            />
            <PaginationControls pageInfo={pageInfo} onPageChange={setTradesPage} />
          </Paper>
        </Box>
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete snapshot?"
        body={`Delete the ${deleteTarget?.date ?? ''} snapshot? The product's value falls back to its previous snapshot.`}
        confirmLabel="Delete"
        loading={deleting}
        onConfirm={() => void handleConfirmDelete()}
        onCancel={() => setDeleteTarget(null)}
      />

      <ResponsiveDialog
        open={snapshotDialogOpen}
        onClose={recording ? undefined : closeSnapshotDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Record snapshot</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <TextField
                label="Snapshot date"
                type="date"
                value={snapshotForm.date}
                onChange={(e) => setSnapshotForm((prev) => ({ ...prev, date: e.target.value }))}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label="Balance"
                type="number"
                value={snapshotForm.balance}
                onChange={(e) => setSnapshotForm((prev) => ({ ...prev, balance: e.target.value }))}
                helperText="0 records a liquidated position. A second entry for the same day replaces the first."
                slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
              />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeSnapshotDialog} disabled={recording}>
            Cancel
          </Button>
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
        </DialogActions>
      </ResponsiveDialog>

      <ResponsiveDialog
        open={tradeDialog !== null}
        onClose={() => setTradeDialog(null)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          {tradeDialog?.direction === 'sell' ? 'Sell' : 'Buy'} {product?.name}
        </DialogTitle>
        {tradeDialog && accounts && (
          <TransferForm
            key={dialogKey}
            dialog
            banner={<ErrorAlert message={error} onDismiss={() => setError(null)} />}
            accounts={accounts}
            preset={tradeDialog}
            onSaved={handleTradeSaved}
            onError={setError}
            onCancel={() => setTradeDialog(null)}
          />
        )}
      </ResponsiveDialog>
    </Box>
  )
}

interface SnapshotEdit {
  id: string
  date: string
  balance: string
}

interface SnapshotHistoryProps {
  snapshots: InvestmentSnapshot[] | undefined
  state: LoadState
  onRetry: () => void
  editing: SnapshotEdit | null
  onEditingChange: (edit: SnapshotEdit | null) => void
  onSaveEdit: () => void
  savingEdit: boolean
  onDelete: (snapshot: InvestmentSnapshot) => void
}

/**
 * The product's snapshots, most recent first, each editable (date and balance) or deletable. Two
 * data columns (Date, Balance), so it stays a plain table at every size (F021's 1-2-column rule) -
 * only the "Record snapshot" add form above became a dialog.
 */
function SnapshotHistory({
  snapshots,
  state,
  onRetry,
  editing,
  onEditingChange,
  onSaveEdit,
  savingEdit,
  onDelete,
}: SnapshotHistoryProps) {
  return (
    <TableContainer>
      <Table size="small" aria-label="Snapshot history">
        <TableHead>
          <TableRow>
            <TableCell>Date</TableCell>
            <TableCell align="right">Balance</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <DataTableBody state={state} onRetry={onRetry} columns={3} actionsColumn>
          {snapshots?.length === 0 && (
            <TableRow>
              <TableCell colSpan={3} align="center">
                <Typography color="text.secondary">No snapshots yet.</Typography>
              </TableCell>
            </TableRow>
          )}
          {snapshots?.map((snapshot) => {
            const edit = editing?.id === snapshot.id ? editing : null
            return (
              <TableRow key={snapshot.id}>
                <TableCell>
                  {edit ? (
                    <TextField
                      size="small"
                      type="date"
                      value={edit.date}
                      onChange={(e) => onEditingChange({ ...edit, date: e.target.value })}
                      slotProps={{ htmlInput: { 'aria-label': 'Edit snapshot date' } }}
                    />
                  ) : (
                    snapshot.date
                  )}
                </TableCell>
                <TableCell align="right">
                  {edit ? (
                    <TextField
                      size="small"
                      type="number"
                      value={edit.balance}
                      onChange={(e) => onEditingChange({ ...edit, balance: e.target.value })}
                      slotProps={{
                        htmlInput: {
                          'aria-label': 'Edit snapshot balance',
                          step: '0.01',
                          min: '0',
                        },
                      }}
                    />
                  ) : (
                    snapshot.balance.toFixed(2)
                  )}
                </TableCell>
                <TableCell align="right">
                  <InlineEditActions
                    editing={edit !== null}
                    onEdit={() =>
                      onEditingChange({
                        id: snapshot.id,
                        date: snapshot.date,
                        balance: String(snapshot.balance),
                      })
                    }
                    onSave={onSaveEdit}
                    onCancel={() => onEditingChange(null)}
                    editLabel="Edit snapshot"
                    saveLabel="Save snapshot"
                    saving={savingEdit}
                  />
                  {!edit && (
                    <IconButton
                      size="small"
                      aria-label="Delete snapshot"
                      onClick={() => onDelete(snapshot)}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  )}
                </TableCell>
              </TableRow>
            )
          })}
        </DataTableBody>
      </Table>
    </TableContainer>
  )
}
