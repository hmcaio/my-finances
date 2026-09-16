import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
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
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import { getAccounts, type Account } from '../../api/accounts'
import {
  createTransfer,
  deleteTransfer,
  editTransfer,
  getTransfers,
  type Transfer,
  type TransferFilter,
} from '../../api/transfers'
import { ApiError } from '../../api/apiError'
import { nameLookup } from '../transactions/nameLookup'

const EMPTY_FORM = {
  date: new Date().toISOString().slice(0, 10),
  amount: '',
  fromAccountId: '',
  toAccountId: '',
  description: '',
  additionalNotes: '',
}

const PAGE_SIZE = 20

/**
 * Transfer list/table with filter controls (date range, account), plus a create/edit form and
 * delete-with-confirmation (F005 spec) - same shape as F004's `TransactionsPage`, minus the
 * category/payment-method dimensions a Transfer doesn't have (PRD S5.5: never "categorized"). The
 * create/edit form's "To" picker excludes whichever account is currently selected as "From" (F005
 * spec: "can't pick the same account twice"), so the same-account case is prevented client-side
 * rather than relying on the backend's 400 (`SameAccountTransferException`).
 */
export function TransfersPage() {
  const [accounts, setAccounts] = useState<Account[] | null>(null)

  const [transfers, setTransfers] = useState<Transfer[] | null>(null)
  const [pageInfo, setPageInfo] = useState<{ number: number; totalPages: number } | null>(null)
  const [page, setPage] = useState(0)

  const [filters, setFilters] = useState<TransferFilter>({})
  const [error, setError] = useState<string | null>(null)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<Transfer | null>(null)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    getAccounts(true)
      .then(setAccounts)
      .catch((err: unknown) => setError(errorMessage(err)))
  }, [])

  function load(activeFilters: TransferFilter, activePage: number) {
    getTransfers(activeFilters, activePage, PAGE_SIZE)
      .then((result) => {
        setTransfers(result.content)
        setPageInfo({ number: result.page.number, totalPages: result.page.totalPages })
      })
      .catch((err: unknown) => setError(errorMessage(err)))
  }

  useEffect(() => {
    load(filters, page)
  }, [filters, page])

  function updateFilter(patch: Partial<TransferFilter>) {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }

  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])
  const openAccounts = useMemo(() => (accounts ?? []).filter((a) => !a.closed), [accounts])
  const toAccountOptions = useMemo(
    () => openAccounts.filter((a) => a.id !== form.fromAccountId),
    [openAccounts, form.fromAccountId],
  )

  function startEdit(transfer: Transfer) {
    setEditingId(transfer.id)
    setForm({
      date: transfer.date,
      amount: String(transfer.amount),
      fromAccountId: transfer.fromAccountId,
      toAccountId: transfer.toAccountId,
      description: transfer.description,
      additionalNotes: transfer.additionalNotes ?? '',
    })
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(EMPTY_FORM)
  }

  function setFromAccountId(fromAccountId: string) {
    setForm((prev) => ({
      ...prev,
      fromAccountId,
      // Clear "To" if it now collides with the newly-picked "From" (F005 spec: can't pick the
      // same account twice on both sides).
      toAccountId: prev.toAccountId === fromAccountId ? '' : prev.toAccountId,
    }))
  }

  function isFormValid() {
    return (
      form.date !== '' &&
      Number(form.amount) > 0 &&
      form.fromAccountId !== '' &&
      form.toAccountId !== '' &&
      form.fromAccountId !== form.toAccountId &&
      form.description.trim() !== ''
    )
  }

  async function handleSubmit() {
    if (!isFormValid()) return
    setError(null)
    setSaving(true)
    const request = {
      date: form.date,
      fromAccountId: form.fromAccountId,
      toAccountId: form.toAccountId,
      amount: Number(form.amount),
      description: form.description.trim(),
      additionalNotes: form.additionalNotes.trim() || undefined,
    }
    try {
      if (editingId) {
        const updated = await editTransfer(editingId, request)
        setTransfers((prev) => prev?.map((t) => (t.id === updated.id ? updated : t)) ?? null)
      } else {
        const created = await createTransfer(request)
        setTransfers((prev) => (prev ? [created, ...prev] : [created]))
      }
      cancelEdit()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    setError(null)
    setDeleting(true)
    try {
      await deleteTransfer(deleteTarget.id)
      setTransfers((prev) => prev?.filter((t) => t.id !== deleteTarget.id) ?? null)
      setDeleteTarget(null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setDeleting(false)
    }
  }

  return (
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Transfers
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Money moved between your own accounts - most commonly paying a credit card statement from
        checking. Transfers are never categorized and don't count toward budgets.
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

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
            <TableBody>
              {transfers === null && (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {transfers?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    <Typography color="text.secondary">No transfers found.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {transfers?.map((transfer) => (
                <TableRow key={transfer.id}>
                  <TableCell>{transfer.date}</TableCell>
                  <TableCell>{accountName(transfer.fromAccountId)}</TableCell>
                  <TableCell>{accountName(transfer.toAccountId)}</TableCell>
                  <TableCell align="right">{transfer.amount.toFixed(2)}</TableCell>
                  <TableCell>{transfer.description}</TableCell>
                  <TableCell align="right">
                    <IconButton size="small" aria-label="Edit" onClick={() => startEdit(transfer)}>
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
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {pageInfo && pageInfo.totalPages > 1 && (
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 3 }}>
          <Button
            size="small"
            disabled={pageInfo.number <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </Button>
          <Typography variant="body2">
            Page {pageInfo.number + 1} of {pageInfo.totalPages}
          </Typography>
          <Button
            size="small"
            disabled={pageInfo.number + 1 >= pageInfo.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </Box>
      )}

      <Paper variant="outlined" sx={{ p: 2, maxWidth: 720 }}>
        <Typography variant="subtitle1" gutterBottom>
          {editingId ? 'Edit transfer' : 'Add transfer'}
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <TextField
            label="Date"
            type="date"
            size="small"
            value={form.date}
            onChange={(e) => setForm((prev) => ({ ...prev, date: e.target.value }))}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <Select
            size="small"
            displayEmpty
            value={form.fromAccountId}
            onChange={(e) => setFromAccountId(e.target.value)}
            aria-label="From Account"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="" disabled>
              From Account
            </MenuItem>
            {openAccounts.map((a) => (
              <MenuItem key={a.id} value={a.id}>
                {a.name}
              </MenuItem>
            ))}
          </Select>
          <Select
            size="small"
            displayEmpty
            value={form.toAccountId}
            onChange={(e) => setForm((prev) => ({ ...prev, toAccountId: e.target.value }))}
            aria-label="To Account"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="" disabled>
              To Account
            </MenuItem>
            {toAccountOptions.map((a) => (
              <MenuItem key={a.id} value={a.id}>
                {a.name}
              </MenuItem>
            ))}
          </Select>
          <TextField
            label="Amount"
            type="number"
            size="small"
            value={form.amount}
            onChange={(e) => setForm((prev) => ({ ...prev, amount: e.target.value }))}
            slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
          />
          <TextField
            label="Description"
            size="small"
            required
            value={form.description}
            onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
            slotProps={{ htmlInput: { maxLength: 150 } }}
          />
          <TextField
            label="Additional Notes"
            size="small"
            value={form.additionalNotes}
            onChange={(e) => setForm((prev) => ({ ...prev, additionalNotes: e.target.value }))}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          <Button
            variant="contained"
            disabled={saving || !isFormValid()}
            onClick={() => void handleSubmit()}
          >
            {editingId ? 'Save changes' : 'Add'}
          </Button>
          {editingId && (
            <Button onClick={cancelEdit} disabled={saving}>
              Cancel
            </Button>
          )}
        </Box>
      </Paper>

      <Dialog open={deleteTarget !== null} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Delete this transfer?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            This permanently removes the transfer and cannot be undone.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)} disabled={deleting}>
            Cancel
          </Button>
          <Button onClick={() => void confirmDelete()} color="error" disabled={deleting} autoFocus>
            Delete transfer
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong.'
}
