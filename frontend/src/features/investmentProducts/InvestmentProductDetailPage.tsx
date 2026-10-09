import { useMemo, useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
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
  MenuItem,
  Paper,
  Select,
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
import EditIcon from '@mui/icons-material/Edit'
import LockIcon from '@mui/icons-material/Lock'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInvestmentCategories } from '../../api/investments/investmentCategoriesQueries'
import { useInvestmentSegments } from '../../api/investments/investmentSegmentsQueries'
import type { InvestmentHolding } from '../../api/investments/investmentHoldings'
import {
  useCloseInvestmentHolding,
  useCreateInvestmentHolding,
  useDeleteInvestmentHolding,
  useEditInvestmentHoldingNotes,
  useInvestmentHoldingsByProduct,
} from '../../api/investments/investmentHoldingsQueries'
import {
  useDeleteInvestmentProduct,
  useEditInvestmentProduct,
  useInvestmentProduct,
} from '../../api/investments/investmentProductsQueries'
import type { InvestmentSnapshot } from '../../api/investments/investmentSnapshots'
import {
  useDeleteInvestmentSnapshot,
  useInvestmentSnapshots,
  useRecordInvestmentSnapshot,
  useUpdateInvestmentSnapshot,
} from '../../api/investments/investmentSnapshotsQueries'
import { useInvestmentValueSeries } from '../../api/investments/investmentValueSeriesQueries'
import type { ValueSeriesPoint } from '../../api/investments/investmentValueSeries'
import type { TradeConfirmationLineRecord } from '../../api/investments/tradeConfirmationLines'
import { useTradeConfirmationLinesByProduct } from '../../api/investments/tradeConfirmationLinesQueries'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { DataTableBody } from '../../components/table/DataTableBody'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState, type LoadState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { nameLookup } from '../../utils/nameLookup'
import { today } from '../../utils/localDate'
import { TransferForm, type TransferFormPreset } from '../transfers/TransferForm'
import { InvestmentProductForm, type InvestmentProductFormValues } from './InvestmentProductForm'
import { ValueSeriesChart } from './ValueSeriesChart'

/** Tighter section padding on phones (F021), matching `AccountDetailPage`. */
const SECTION_PADDING = { xs: 2, sm: 3 }

/**
 * Per-product detail view (F008 spec, made holding-aware by F022/ADR 0020): the product's taxonomy
 * (with Edit/Delete - delete only once it has zero holdings), a "Holdings" panel listing every
 * account this product is held in (each with its own close/delete/notes), a holding picker when
 * there is more than one, and - for the selected holding - the current value, Buy/Sell buttons, the
 * snapshot entry form/history, the product's trade history and the value/contribution chart. This
 * is the minimum needed to keep the page usable with multiple holdings; F023 turns it into the full
 * multi-holding management page.
 *
 * Responsive (F021): section padding tightens on phones. Dialogs use `ResponsiveDialog`; the
 * snapshot history and holdings tables stay plain tables (the 1-2/3-column rule); the trades table
 * becomes a `ResponsiveTable`.
 */
export function InvestmentProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const productId = id ?? ''
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  const productQuery = useInvestmentProduct(id)
  const product = productQuery.data
  const productState = useQueryState(productQuery, undefined, (err) =>
    defaultErrorMessage(err, { 404: 'Investment product not found.' }),
  )
  const categoriesQuery = useInvestmentCategories()
  const categories = categoriesQuery.data
  const segmentsQuery = useInvestmentSegments()
  const segments = segmentsQuery.data
  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  useQueryState(accountsQuery, setError)
  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])

  const holdingsQuery = useInvestmentHoldingsByProduct(id)
  const holdings = holdingsQuery.data
  const holdingsState = useQueryState(holdingsQuery, setError)
  const [selectedHoldingId, setSelectedHoldingId] = useState<string>('')
  const selectedHolding =
    holdings?.find((h) => h.id === selectedHoldingId) ?? holdings?.[0] ?? undefined
  const holdingAccountIds = new Set((holdings ?? []).map((h) => h.accountId))

  const snapshotsQuery = useInvestmentSnapshots(selectedHolding?.id)
  const snapshots = snapshotsQuery.data
  const snapshotsState = useQueryState(snapshotsQuery, setError)
  const seriesQuery = useInvestmentValueSeries({ productId })
  const series = seriesQuery.data
  const seriesState = useQueryState(seriesQuery, setError)
  const tradesQuery = useTradeConfirmationLinesByProduct(productId)
  // transferId alone isn't unique across rows (partial fills put more than one line under the
  // same confirmation), so each row gets a synthetic client-side key.
  const trades = tradesQuery.data?.map((t, i) => ({ ...t, rowKey: `${t.transferId}-${i}` }))
  const tradesState = useQueryState(tradesQuery, setError)
  const recordSnapshot = useRecordInvestmentSnapshot()
  const updateSnapshot = useUpdateInvestmentSnapshot()
  const deleteSnapshot = useDeleteInvestmentSnapshot()
  const editProduct = useEditInvestmentProduct()
  const deleteProduct = useDeleteInvestmentProduct()
  const createHolding = useCreateInvestmentHolding()
  const closeHolding = useCloseInvestmentHolding()
  const deleteHolding = useDeleteInvestmentHolding()
  const editHoldingNotes = useEditInvestmentHoldingNotes()

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

  const [editProductOpen, setEditProductOpen] = useState(false)
  const [savingProduct, setSavingProduct] = useState(false)
  const [deleteProductConfirm, setDeleteProductConfirm] = useState(false)
  const [deletingProduct, setDeletingProduct] = useState(false)
  const [addHoldingOpen, setAddHoldingOpen] = useState(false)
  const [addHoldingAccountId, setAddHoldingAccountId] = useState('')
  const [addHoldingNotes, setAddHoldingNotes] = useState('')
  const [addingHolding, setAddingHolding] = useState(false)
  const [closeHoldingTarget, setCloseHoldingTarget] = useState<InvestmentHolding | null>(null)
  const [confirmingHolding, setConfirmingHolding] = useState(false)
  const [deleteHoldingTarget, setDeleteHoldingTarget] = useState<InvestmentHolding | null>(null)
  const [notesEdit, setNotesEdit] = useState<{ id: string; value: string } | null>(null)
  const [savingNotes, setSavingNotes] = useState(false)

  const { loading, loadError } = combineLoadState(productState)
  const showSkeleton = useDelayedFlag(loading)
  const selectedAccount = accounts?.find((a) => a.id === selectedHolding?.accountId)
  const selectedAccountClosed = selectedAccount?.closed ?? false
  const canTrade =
    selectedHolding !== undefined &&
    !selectedHolding.closed &&
    !selectedAccountClosed &&
    accounts !== undefined

  const points = series?.[0]?.points ?? []
  const hasUnits = points.some((p) => p.units !== null)

  // While any dialog is open, a save error shows inside it: the page banner sits behind it.
  const anyDialogOpen =
    tradeDialog !== null || snapshotDialogOpen || editProductOpen || addHoldingOpen

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
    if (!selectedHolding) return
    const balance = Number(snapshotForm.balance)
    if (snapshotForm.date === '' || snapshotForm.balance === '' || !(balance >= 0)) return
    setError(null)
    setRecording(true)
    try {
      // A same-day entry replaces the earlier one; the refetch after the write reorders the list.
      await recordSnapshot.mutateAsync({
        holdingId: selectedHolding.id,
        date: snapshotForm.date,
        balance,
      })
      setSnapshotDialogOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setRecording(false)
    }
  }

  async function handleSaveEdit() {
    if (!selectedHolding || !editing) return
    const balance = Number(editing.balance)
    if (editing.date === '' || editing.balance === '' || !(balance >= 0)) return
    setError(null)
    setSavingEdit(true)
    try {
      await updateSnapshot.mutateAsync({
        holdingId: selectedHolding.id,
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
    if (!selectedHolding || !deleteTarget) return
    setError(null)
    setDeleting(true)
    try {
      await deleteSnapshot.mutateAsync({
        holdingId: selectedHolding.id,
        snapshotId: deleteTarget.id,
      })
    } catch (err) {
      setError(defaultErrorMessage(err, { 404: 'Snapshot not found.' }))
    } finally {
      setDeleting(false)
      setDeleteTarget(null)
    }
  }

  function openTrade(direction: 'buy' | 'sell') {
    if (!selectedHolding || !product) return
    setError(null)
    setDialogKey((n) => n + 1)
    setTradeDialog({
      direction,
      investmentAccountId: selectedHolding.accountId,
      productId: product.id,
      productName: product.name,
    })
  }

  function handleTradeSaved() {
    setTradeDialog(null)
  }

  async function handleEditProduct(values: InvestmentProductFormValues) {
    if (!product) return
    setError(null)
    setSavingProduct(true)
    try {
      await editProduct.mutateAsync({
        id: product.id,
        investmentCategoryId: values.categoryId,
        investmentSubcategoryId: values.subcategoryId || undefined,
        name: values.name,
        additionalNotes: values.additionalNotes || undefined,
        ticker: values.ticker || undefined,
        segmentId: values.segmentId || undefined,
      })
      setEditProductOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setSavingProduct(false)
    }
  }

  async function handleDeleteProduct() {
    if (!product) return
    setError(null)
    setDeletingProduct(true)
    try {
      await deleteProduct.mutateAsync(product.id)
      navigate('/accounts')
    } catch (err) {
      setError(defaultErrorMessage(err))
      setDeleteProductConfirm(false)
    } finally {
      setDeletingProduct(false)
    }
  }

  function openAddHolding() {
    setError(null)
    setAddHoldingAccountId('')
    setAddHoldingNotes('')
    setAddHoldingOpen(true)
  }

  async function handleAddHolding() {
    if (!product || addHoldingAccountId === '') return
    setError(null)
    setAddingHolding(true)
    try {
      const created = await createHolding.mutateAsync({
        productId: product.id,
        accountId: addHoldingAccountId,
        additionalNotes: addHoldingNotes.trim() || undefined,
      })
      setSelectedHoldingId(created.id)
      setAddHoldingOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAddingHolding(false)
    }
  }

  async function confirmCloseHolding() {
    if (!closeHoldingTarget) return
    setError(null)
    setConfirmingHolding(true)
    try {
      await closeHolding.mutateAsync(closeHoldingTarget.id)
      setCloseHoldingTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setConfirmingHolding(false)
    }
  }

  async function confirmDeleteHolding() {
    if (!deleteHoldingTarget) return
    setError(null)
    setConfirmingHolding(true)
    try {
      await deleteHolding.mutateAsync(deleteHoldingTarget.id)
      if (selectedHoldingId === deleteHoldingTarget.id) setSelectedHoldingId('')
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setConfirmingHolding(false)
      setDeleteHoldingTarget(null)
    }
  }

  async function handleSaveNotes() {
    if (!notesEdit) return
    setError(null)
    setSavingNotes(true)
    try {
      await editHoldingNotes.mutateAsync({
        id: notesEdit.id,
        additionalNotes: notesEdit.value || undefined,
      })
      setNotesEdit(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setSavingNotes(false)
    }
  }

  const availableAccountsForNewHolding = (accounts ?? []).filter(
    (a) => a.type === 'INVESTMENT' && !a.closed && !holdingAccountIds.has(a.id),
  )

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

  // F027 (ADR 0024): one row per matching line (not per confirmation); Taxes dropped - a
  // confirmation's taxes cover its whole settlement, not one product. Each row links back to its
  // parent confirmation so the real total taxes stay reachable.
  type TradeRow = TradeConfirmationLineRecord & { rowKey: string }
  const tradeColumns: ResponsiveColumn<TradeRow>[] = [
    { key: 'date', header: 'Date', role: 'secondary', render: (t) => t.date },
    { key: 'side', header: 'Side', render: (t) => (t.side === 'BUY' ? 'Buy' : 'Sell') },
    {
      key: 'quantity',
      header: 'Quantity',
      align: 'right',
      render: (t) => String(t.quantity),
    },
    {
      key: 'unitPrice',
      header: 'Unit price',
      align: 'right',
      tabletPriority: 'low',
      render: (t) => String(t.unitPrice),
    },
    {
      key: 'resultingBalance',
      header: 'Resulting balance',
      align: 'right',
      tabletPriority: 'low',
      render: (t) => (t.resultingBalance === null ? '-' : t.resultingBalance.toFixed(2)),
    },
    {
      key: 'confirmation',
      header: 'Confirmation',
      role: 'primary',
      render: (t) => (
        <MuiLink component={RouterLink} to={`/transfers?focus=${t.transferId}`} underline="hover">
          View confirmation
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
          <Chip label={trade.side === 'BUY' ? 'Buy' : 'Sell'} size="small" />
        </Box>
        <Typography variant="body2" color="text.secondary">
          Quantity {trade.quantity} · Unit price {trade.unitPrice}
          {trade.resultingBalance !== null &&
            ` · Resulting balance ${trade.resultingBalance.toFixed(2)}`}
        </Typography>
        <MuiLink
          component={RouterLink}
          to={`/transfers?focus=${trade.transferId}`}
          underline="hover"
        >
          View confirmation
        </MuiLink>
      </Paper>
    )
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <MuiLink
        component={RouterLink}
        to={selectedHolding ? `/accounts/${selectedHolding.accountId}` : '/accounts'}
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
            <IconButton
              size="small"
              aria-label="Edit product"
              onClick={() => setEditProductOpen(true)}
            >
              <EditIcon fontSize="small" />
            </IconButton>
            {(holdings?.length ?? 0) === 0 && (
              <IconButton
                size="small"
                aria-label="Delete product"
                onClick={() => setDeleteProductConfirm(true)}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            )}
          </Box>
          {product.additionalNotes && (
            <Typography color="text.secondary" sx={{ mb: 2, overflowWrap: 'anywhere' }}>
              {product.additionalNotes}
            </Typography>
          )}

          <ErrorAlert message={anyDialogOpen ? null : error} onDismiss={() => setError(null)} />

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
              <Typography variant="h6">Holdings</Typography>
              <Button
                variant="outlined"
                size="small"
                disabled={availableAccountsForNewHolding.length === 0}
                onClick={openAddHolding}
              >
                Add holding
              </Button>
            </Box>
            <HoldingsTable
              holdings={holdings}
              state={holdingsState}
              accountName={accountName}
              selectedId={selectedHolding?.id}
              onSelect={setSelectedHoldingId}
              onEditNotes={(h) => setNotesEdit({ id: h.id, value: h.additionalNotes ?? '' })}
              onClose={setCloseHoldingTarget}
              onDelete={setDeleteHoldingTarget}
            />
          </Paper>

          {selectedHolding && (
            <>
              <Paper variant="outlined" sx={{ p: SECTION_PADDING, mb: 3, maxWidth: 520 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                  <Typography variant="overline" color="text.secondary">
                    Current value {selectedAccount ? `(${selectedAccount.name})` : ''}
                  </Typography>
                  {selectedHolding.closed ? (
                    <Chip label="Closed" size="small" />
                  ) : (
                    <Chip label="Open" size="small" color="success" />
                  )}
                  {selectedHolding.needsSnapshot && (
                    <Tooltip title="A buy or sell is newer than the latest snapshot, so the value shown may be out of date.">
                      <Chip label="Needs snapshot" size="small" color="warning" />
                    </Tooltip>
                  )}
                </Box>
                <Typography variant="h3" sx={{ mb: 1, mt: 1 }}>
                  {selectedHolding.latestSnapshot
                    ? selectedHolding.latestSnapshot.balance.toFixed(2)
                    : '-'}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  {selectedHolding.latestSnapshot
                    ? `Latest snapshot ${selectedHolding.latestSnapshot.date}`
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
            </>
          )}

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

          <Paper variant="outlined" sx={{ p: SECTION_PADDING }}>
            <Typography variant="h6" gutterBottom>
              Trades
            </Typography>
            <ResponsiveTable
              aria-label="Trades"
              columns={tradeColumns}
              rows={trades}
              getRowKey={(t) => t.rowKey}
              state={tradesState}
              onRetry={tradesState.reload}
              renderCard={renderTradeCard}
              emptyMessage="No trades yet."
            />
          </Paper>
        </Box>
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete snapshot?"
        body={`Delete the ${deleteTarget?.date ?? ''} snapshot? The holding's value falls back to its previous snapshot.`}
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

      <ResponsiveDialog
        open={editProductOpen}
        onClose={savingProduct ? undefined : () => setEditProductOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Edit product</DialogTitle>
        {product && categories && (
          <InvestmentProductForm
            dialog
            banner={<ErrorAlert message={error} onDismiss={() => setError(null)} />}
            categories={categories}
            segments={segments ?? []}
            initial={{
              name: product.name,
              categoryId: product.investmentCategoryId,
              subcategoryId: product.investmentSubcategoryId ?? '',
              additionalNotes: product.additionalNotes ?? '',
              ticker: product.ticker ?? '',
              segmentId: product.segmentId ?? '',
            }}
            submitLabel="Save"
            submitting={savingProduct}
            onSubmit={(values) => void handleEditProduct(values)}
            onCancel={() => setEditProductOpen(false)}
          />
        )}
      </ResponsiveDialog>

      <ConfirmDialog
        open={deleteProductConfirm}
        title={`Delete ${product?.name}?`}
        body="The product has zero holdings, so it can be deleted for good. This cannot be undone."
        confirmLabel="Delete product"
        loading={deletingProduct}
        onConfirm={() => void handleDeleteProduct()}
        onCancel={() => setDeleteProductConfirm(false)}
      />

      <ResponsiveDialog
        open={addHoldingOpen}
        onClose={addingHolding ? undefined : () => setAddHoldingOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Add holding</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <Select
                size="small"
                displayEmpty
                value={addHoldingAccountId}
                onChange={(e) => setAddHoldingAccountId(e.target.value)}
                aria-label="Account"
              >
                <MenuItem value="" disabled>
                  Account
                </MenuItem>
                {availableAccountsForNewHolding.map((a) => (
                  <MenuItem key={a.id} value={a.id}>
                    {a.name}
                  </MenuItem>
                ))}
              </Select>
              <TextField
                label="Additional notes"
                size="small"
                multiline
                value={addHoldingNotes}
                onChange={(e) => setAddHoldingNotes(e.target.value)}
                slotProps={{ htmlInput: { maxLength: 500 } }}
              />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddHoldingOpen(false)} disabled={addingHolding}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={addingHolding || addHoldingAccountId === ''}
            onClick={() => void handleAddHolding()}
          >
            Add holding
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ResponsiveDialog
        open={notesEdit !== null}
        onClose={savingNotes ? undefined : () => setNotesEdit(null)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Edit holding notes</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <TextField
              label="Additional notes"
              size="small"
              fullWidth
              multiline
              value={notesEdit?.value ?? ''}
              onChange={(e) =>
                setNotesEdit((prev) => (prev ? { ...prev, value: e.target.value } : prev))
              }
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setNotesEdit(null)} disabled={savingNotes}>
            Cancel
          </Button>
          <Button variant="contained" disabled={savingNotes} onClick={() => void handleSaveNotes()}>
            Save
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ConfirmDialog
        open={closeHoldingTarget !== null}
        title={`Close this holding at ${closeHoldingTarget ? accountName(closeHoldingTarget.accountId) : ''}?`}
        body="Closing a holding is not reversible through this app - there is no reopen action. It stays listed and keeps its history. A holding that still has value can't be closed: record a zero snapshot (or sell the entire position) first."
        confirmLabel="Close holding"
        loading={confirmingHolding}
        onConfirm={() => void confirmCloseHolding()}
        onCancel={() => setCloseHoldingTarget(null)}
      />

      <ConfirmDialog
        open={deleteHoldingTarget !== null}
        title={`Delete this holding at ${deleteHoldingTarget ? accountName(deleteHoldingTarget.accountId) : ''}?`}
        body="The holding has no history, so it can be deleted for good. This cannot be undone."
        confirmLabel="Delete holding"
        loading={confirmingHolding}
        onConfirm={() => void confirmDeleteHolding()}
        onCancel={() => setDeleteHoldingTarget(null)}
      />
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
 * The selected holding's snapshots, most recent first, each editable (date and balance) or
 * deletable. Two data columns (Date, Balance), so it stays a plain table at every size (F021's
 * 1-2-column rule) - only the "Record snapshot" add form above became a dialog.
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

interface HoldingsTableProps {
  holdings: InvestmentHolding[] | undefined
  state: LoadState
  accountName: (id: string) => string
  selectedId: string | undefined
  onSelect: (id: string) => void
  onEditNotes: (holding: InvestmentHolding) => void
  onClose: (holding: InvestmentHolding) => void
  onDelete: (holding: InvestmentHolding) => void
}

/** Every account this product is held in - the F022 holding-management panel. */
function HoldingsTable({
  holdings,
  state,
  accountName,
  selectedId,
  onSelect,
  onEditNotes,
  onClose,
  onDelete,
}: HoldingsTableProps) {
  return (
    <TableContainer>
      <Table size="small" aria-label="Holdings">
        <TableHead>
          <TableRow>
            <TableCell>Account</TableCell>
            <TableCell>Status</TableCell>
            <TableCell align="right">Latest value</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <DataTableBody state={state} onRetry={state.reload} columns={4} actionsColumn>
          {holdings?.length === 0 && (
            <TableRow>
              <TableCell colSpan={4} align="center">
                <Typography color="text.secondary">No holdings yet.</Typography>
              </TableCell>
            </TableRow>
          )}
          {holdings?.map((holding) => (
            <TableRow
              key={holding.id}
              selected={holding.id === selectedId}
              hover
              onClick={() => onSelect(holding.id)}
              sx={{ cursor: 'pointer' }}
            >
              <TableCell>{accountName(holding.accountId)}</TableCell>
              <TableCell>
                {holding.closed ? (
                  <Chip label="Closed" size="small" />
                ) : (
                  <Chip label="Open" size="small" color="success" />
                )}
              </TableCell>
              <TableCell align="right">
                {holding.latestSnapshot ? holding.latestSnapshot.balance.toFixed(2) : '-'}
              </TableCell>
              <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                <IconButton
                  size="small"
                  aria-label="Edit notes"
                  onClick={() => onEditNotes(holding)}
                >
                  <EditIcon fontSize="small" />
                </IconButton>
                <IconButton
                  size="small"
                  aria-label="Close holding"
                  disabled={holding.closed}
                  onClick={() => onClose(holding)}
                >
                  <LockIcon fontSize="small" />
                </IconButton>
                {!holding.hasHistory && (
                  <IconButton
                    size="small"
                    aria-label="Delete holding"
                    onClick={() => onDelete(holding)}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                )}
              </TableCell>
            </TableRow>
          ))}
        </DataTableBody>
      </Table>
    </TableContainer>
  )
}
