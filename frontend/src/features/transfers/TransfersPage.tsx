import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Box,
  Button,
  Chip,
  DialogTitle,
  IconButton,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useInvestmentProducts } from '../../api/investments/investmentProductsQueries'
import type { Transfer, TransferFilter } from '../../api/transfers/transfers'
import { useDeleteTransfer, useTransfer, useTransfers } from '../../api/transfers/transfersQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { ResponsiveFilterBar } from '../../components/layout/ResponsiveFilterBar'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { PaginationControls } from '../../components/table/PaginationControls'
import { nameLookup } from '../../utils/nameLookup'
import { TransferForm } from './TransferForm'

const PAGE_SIZE = 20

/**
 * Transfer list/table with filter controls (date range, account), plus a create/edit form and
 * delete-with-confirmation (F005 spec) - same shape as F004's `TransactionsPage`, minus the
 * category/payment-method dimensions a Transfer doesn't have (PRD S5.5: never "categorized"). The
 * form is `TransferForm`, which also handles buys and sells of investment products (F009): a
 * tagged transfer shows a Buy/Sell chip with its product's name here.
 *
 * Responsive (F021): at every size the header's Add button and each row's/card's Edit open the
 * form in a `ResponsiveDialog` (full screen below `sm`); there is no form panel below the table.
 *
 * A `?focus={transferId}` query param (F027: `InvestmentProductDetailPage`'s "View confirmation"
 * link) opens that transfer's edit dialog directly, regardless of the list's current page/filters.
 */
export function TransfersPage() {
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<TransferFilter>({})
  const [error, setError] = useState<string | null>(null)

  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const productsQuery = useInvestmentProducts()
  const products = productsQuery.data
  const productsState = useQueryState(productsQuery, setError)
  const transfersQuery = useTransfers(filters, page, PAGE_SIZE)
  const transfers = transfersQuery.data?.content
  const pageInfo = transfersQuery.data
    ? { number: transfersQuery.data.page.number, totalPages: transfersQuery.data.page.totalPages }
    : null
  const transfersState = useQueryState(transfersQuery, setError)
  const deleteMutation = useDeleteTransfer()

  const [editing, setEditing] = useState<Transfer | null>(null)
  const [formDialogOpen, setFormDialogOpen] = useState(false)
  // Bumped on each open so the form mounts with fresh state (today's date, no leftovers).
  const [formKey, setFormKey] = useState(0)

  const [deleteTarget, setDeleteTarget] = useState<Transfer | null>(null)
  const [deleting, setDeleting] = useState(false)

  function updateFilter(patch: Partial<TransferFilter>) {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }

  function clearFilters() {
    setFilters({})
    setPage(0)
  }

  const activeFilterCount = Object.values(filters).filter(Boolean).length

  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])
  const productsById = useMemo(() => new Map((products ?? []).map((p) => [p.id, p])), [products])

  function startAdd() {
    setEditing(null)
    setFormKey((n) => n + 1)
    setFormDialogOpen(true)
  }

  function startEdit(transfer: Transfer) {
    setEditing(transfer)
    setFormKey((n) => n + 1)
    setFormDialogOpen(true)
  }

  // `InvestmentProductDetailPage`'s "View confirmation" link (F027) arrives here as
  // `?focus={transferId}` rather than a row click, so the confirmation must open regardless of the
  // list's current page/filters - fetched directly instead of searched for in `transfers`. Cleared
  // from the URL once consumed so a later back-navigation doesn't reopen it.
  const [searchParams, setSearchParams] = useSearchParams()
  const focusId = searchParams.get('focus')
  const focusQuery = useTransfer(focusId)
  useEffect(() => {
    if (focusQuery.data) {
      // Deliberately a one-time reaction to an external trigger (the URL's `focus` param resolving
      // via its fetch), not state derivable from props/render - the dialog must stay open (and
      // `editing` must stay set) after the param is cleared below, so it can't be computed inline.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      startEdit(focusQuery.data)
      setSearchParams({}, { replace: true })
    } else if (focusQuery.isError) {
      setError(
        defaultErrorMessage(focusQuery.error, {
          404: 'The linked transfer could not be found - it may have been deleted.',
        }),
      )
      setSearchParams({}, { replace: true })
    }
    // Reacts only to the focused-transfer fetch settling, not to every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusQuery.data, focusQuery.isError, focusQuery.error])

  function closeForm() {
    setEditing(null)
    setFormDialogOpen(false)
    setError(null)
  }

  // The saved row shows up through the refetch that follows every successful write.
  function handleSaved() {
    setEditing(null)
    setFormDialogOpen(false)
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    setError(null)
    setDeleting(true)
    try {
      await deleteMutation.mutateAsync(deleteTarget.id)
      setDeleteTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setDeleting(false)
    }
  }

  /** A "Buy X"/"Sell Y" label per line of a trade confirmation (F027, ADR 0024 - superseding the
   * single-product chip): collapses past a handful so a many-line confirmation doesn't blow out
   * the row. */
  const MAX_TRADE_CHIPS = 3
  function tradeLabels(transfer: Transfer): string[] {
    const lines = transfer.tradeConfirmation?.lines ?? []
    const labels = lines.map((line) => {
      const product = productsById.get(line.productId)
      return `${line.side === 'BUY' ? 'Buy' : 'Sell'} ${product?.name ?? '…'}`
    })
    if (labels.length <= MAX_TRADE_CHIPS) return labels
    return [...labels.slice(0, MAX_TRADE_CHIPS), `+${labels.length - MAX_TRADE_CHIPS} more`]
  }

  // One load state for the table plus the lookup lists behind its name columns: rows show only
  // once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(accountsState, productsState, transfersState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  const columns: ResponsiveColumn<Transfer>[] = [
    { key: 'date', header: 'Date', role: 'secondary', render: (t) => t.date },
    { key: 'from', header: 'From', render: (t) => accountName(t.fromAccountId) },
    { key: 'to', header: 'To', render: (t) => accountName(t.toAccountId) },
    { key: 'amount', header: 'Amount', align: 'right', render: (t) => t.amount.toFixed(2) },
    {
      key: 'description',
      header: 'Description',
      role: 'primary',
      render: (t) => (
        <>
          {t.description}
          {tradeLabels(t).map((label, i) => (
            <Chip key={i} label={label} size="small" sx={{ ml: 1, mt: 0.5 }} />
          ))}
        </>
      ),
    },
  ]

  function rowActions(transfer: Transfer) {
    return (
      <>
        <IconButton size="small" aria-label="Edit" onClick={() => startEdit(transfer)}>
          <EditIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" aria-label="Delete" onClick={() => setDeleteTarget(transfer)}>
          <DeleteIcon fontSize="small" />
        </IconButton>
      </>
    )
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          mb: 1,
        }}
      >
        <Typography variant="h4" component="h1">
          Transfers
        </Typography>
        <Button variant="contained" onClick={startAdd}>
          Add transfer
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Money moved between your own accounts - most commonly paying a credit card statement from
        checking, or buying into and selling out of an investment product. Transfers are never
        categorized and don't count toward budgets.
      </Typography>

      {/* While the dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert message={formDialogOpen ? null : error} onDismiss={() => setError(null)} />

      <ResponsiveFilterBar activeCount={activeFilterCount} onClear={clearFilters}>
        <TextField
          label="From"
          type="date"
          size="small"
          value={filters.dateFrom ?? ''}
          onChange={(e) => updateFilter({ dateFrom: e.target.value || undefined })}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          label="To"
          type="date"
          size="small"
          value={filters.dateTo ?? ''}
          onChange={(e) => updateFilter({ dateTo: e.target.value || undefined })}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <Select
          size="small"
          displayEmpty
          value={filters.accountId ?? ''}
          onChange={(e) => updateFilter({ accountId: e.target.value || undefined })}
          aria-label="Account filter"
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All accounts</MenuItem>
          {accounts?.map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
        </Select>
      </ResponsiveFilterBar>

      <Box sx={{ mb: 2 }}>
        <ResponsiveTable
          aria-label="Transfers"
          columns={columns}
          rows={transfers}
          getRowKey={(t) => t.id}
          state={tableState}
          onRetry={retry}
          actions={rowActions}
          emptyMessage="No transfers found."
        />
      </Box>

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} sx={{ mt: 0, mb: 3 }} />

      <ResponsiveDialog open={formDialogOpen} onClose={closeForm} fullWidth maxWidth="md">
        <DialogTitle>{editing ? 'Edit transfer' : 'Add transfer'}</DialogTitle>
        <TransferForm
          key={formKey}
          dialog
          banner={<ErrorAlert message={error} onDismiss={() => setError(null)} />}
          accounts={accounts ?? []}
          editing={editing}
          onSaved={handleSaved}
          onError={setError}
          onCancel={closeForm}
        />
      </ResponsiveDialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete this transfer?"
        body="This permanently removes the transfer and cannot be undone."
        confirmLabel="Delete transfer"
        loading={deleting}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  )
}
