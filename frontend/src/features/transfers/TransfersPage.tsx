import { useMemo, useState } from 'react'
import {
  Box,
  Button,
  Chip,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import { useAccounts } from '../../api/accountsQueries'
import { useInvestmentProducts } from '../../api/investmentProductsQueries'
import type { Transfer, TransferFilter } from '../../api/transfers'
import { useDeleteTransfer, useTransfers } from '../../api/transfersQueries'
import { defaultErrorMessage } from '../../api/apiError'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorAlert } from '../../components/ErrorAlert'
import { DataTableBody } from '../../components/DataTableBody'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { PaginationControls } from '../../components/PaginationControls'
import { nameLookup } from '../../utils/nameLookup'
import { TransferForm } from './TransferForm'

const PAGE_SIZE = 20

/**
 * Transfer list/table with filter controls (date range, account), plus a create/edit form and
 * delete-with-confirmation (F005 spec) - same shape as F004's `TransactionsPage`, minus the
 * category/payment-method dimensions a Transfer doesn't have (PRD S5.5: never "categorized"). The
 * form is `TransferForm`, which also handles buys and sells of investment products (F009): a
 * tagged transfer shows a Buy/Sell chip with its product's name here.
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
  // Bumped after each save/cancel so the form remounts with fresh state (today's date, no leftovers).
  const [formKey, setFormKey] = useState(0)

  const [deleteTarget, setDeleteTarget] = useState<Transfer | null>(null)
  const [deleting, setDeleting] = useState(false)

  function updateFilter(patch: Partial<TransferFilter>) {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }

  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])
  const productsById = useMemo(() => new Map((products ?? []).map((p) => [p.id, p])), [products])

  function resetForm() {
    setEditing(null)
    setFormKey((n) => n + 1)
  }

  // The saved row shows up through the refetch that follows every successful write.
  function handleSaved() {
    resetForm()
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

  /** "Buy"/"Sell" plus the product's name for a tagged transfer; direction is derived from the
   * accounts (a transfer into the product's own account is a buy), never stored. */
  function tradeLabel(transfer: Transfer): string | null {
    if (!transfer.investmentProductId) return null
    const product = productsById.get(transfer.investmentProductId)
    if (!product) return null
    return `${transfer.toAccountId === product.accountId ? 'Buy' : 'Sell'} ${product.name}`
  }

  // One load state for the table plus the lookup lists behind its name columns: rows show only
  // once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(accountsState, productsState, transfersState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  return (
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Transfers
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Money moved between your own accounts - most commonly paying a credit card statement from
        checking, or buying into and selling out of an investment product. Transfers are never
        categorized and don't count toward budgets.
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
        <Typography variant="subtitle2" gutterBottom>
          Filters
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
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
          <Button
            size="small"
            onClick={() => {
              setFilters({})
              setPage(0)
            }}
          >
            Clear filters
          </Button>
        </Box>
      </Paper>

      <Paper variant="outlined" sx={{ mb: 2 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>From</TableCell>
                <TableCell>To</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell>Description</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={tableState} onRetry={retry} columns={6} actionsColumn>
              {transfers?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    <Typography color="text.secondary">No transfers found.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {transfers?.map((transfer) => {
                const trade = tradeLabel(transfer)
                return (
                  <TableRow key={transfer.id}>
                    <TableCell>{transfer.date}</TableCell>
                    <TableCell>{accountName(transfer.fromAccountId)}</TableCell>
                    <TableCell>{accountName(transfer.toAccountId)}</TableCell>
                    <TableCell align="right">{transfer.amount.toFixed(2)}</TableCell>
                    <TableCell>
                      {transfer.description}
                      {trade && <Chip label={trade} size="small" sx={{ ml: 1 }} />}
                    </TableCell>
                    <TableCell align="right">
                      <IconButton
                        size="small"
                        aria-label="Edit"
                        onClick={() => {
                          setEditing(transfer)
                          setFormKey((n) => n + 1)
                        }}
                      >
                        <EditIcon fontSize="small" />
                      </IconButton>
                      <IconButton
                        size="small"
                        aria-label="Delete"
                        onClick={() => setDeleteTarget(transfer)}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                )
              })}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} sx={{ mt: 0, mb: 3 }} />

      <Paper variant="outlined" sx={{ p: 2, maxWidth: 900 }}>
        <Typography variant="subtitle1" gutterBottom>
          {editing ? 'Edit transfer' : 'Add transfer'}
        </Typography>
        <TransferForm
          key={formKey}
          accounts={accounts ?? []}
          editing={editing}
          onSaved={handleSaved}
          onError={setError}
          onCancel={editing ? resetForm : undefined}
        />
      </Paper>

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
