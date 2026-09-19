import { useMemo, useState } from 'react'
import {
  Box,
  Button,
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
import { getAccounts } from '../../api/accounts'
import { getCategories } from '../../api/categories'
import { getPaymentMethods } from '../../api/paymentMethods'
import {
  createTransaction,
  deleteTransaction,
  editTransaction,
  getTransactions,
  type Transaction,
  type TransactionFilter,
} from '../../api/transactions'
import { defaultErrorMessage } from '../../api/apiError'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorAlert } from '../../components/ErrorAlert'
import { DataTableBody } from '../../components/DataTableBody'
import { reloadFailed, useAsyncData } from '../../hooks/useAsyncData'
import { usePagedData } from '../../hooks/usePagedData'
import { PaginationControls } from '../../components/PaginationControls'
import { nameLookup } from '../../utils/nameLookup'

const EMPTY_FORM = {
  date: new Date().toISOString().slice(0, 10),
  amount: '',
  categoryId: '',
  accountId: '',
  paymentMethodId: '',
  description: '',
  additionalNotes: '',
}

const PAGE_SIZE = 20

/**
 * Transaction list/table with filter controls (date range, category, account, payment method),
 * plus a create/edit form and delete-with-confirmation (F004 spec). One combined form toggles
 * between create and edit mode - the fields (date/amount/category/account/payment
 * method/description/additional notes) are identical for both, matching F004's
 * plain-in-place-edit semantics (no versioning).
 */
export function TransactionsPage() {
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<TransactionFilter>({})
  const [error, setError] = useState<string | null>(null)

  const { data: categories, ...categoriesState } = useAsyncData(getCategories, [], {
    onError: setError,
  })
  const { data: accounts, ...accountsState } = useAsyncData(() => getAccounts(true), [], {
    onError: setError,
  })
  const { data: paymentMethods, ...paymentMethodsState } = useAsyncData(getPaymentMethods, [], {
    onError: setError,
  })
  const {
    items: transactions,
    setItems: setTransactions,
    pageInfo,
    ...transactionsState
  } = usePagedData(() => getTransactions(filters, page, PAGE_SIZE), [filters, page], {
    onError: setError,
  })

  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<Transaction | null>(null)
  const [deleting, setDeleting] = useState(false)

  function updateFilter(patch: Partial<TransactionFilter>) {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }

  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])
  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])
  const paymentMethodName = useMemo(
    () => nameLookup(paymentMethods ?? [], (p) => p.name),
    [paymentMethods],
  )
  const openAccounts = useMemo(() => (accounts ?? []).filter((a) => !a.closed), [accounts])

  function startEdit(transaction: Transaction) {
    setEditingId(transaction.id)
    setForm({
      date: transaction.date,
      amount: String(transaction.amount),
      categoryId: transaction.categoryId,
      accountId: transaction.accountId,
      paymentMethodId: transaction.paymentMethodId,
      description: transaction.description,
      additionalNotes: transaction.additionalNotes ?? '',
    })
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(EMPTY_FORM)
  }

  function isFormValid() {
    return (
      form.date !== '' &&
      Number(form.amount) > 0 &&
      form.categoryId !== '' &&
      form.accountId !== '' &&
      form.paymentMethodId !== '' &&
      form.description.trim() !== ''
    )
  }

  async function handleSubmit() {
    if (!isFormValid()) return
    setError(null)
    setSaving(true)
    const request = {
      date: form.date,
      amount: Number(form.amount),
      categoryId: form.categoryId,
      accountId: form.accountId,
      paymentMethodId: form.paymentMethodId,
      description: form.description.trim(),
      additionalNotes: form.additionalNotes.trim() || undefined,
    }
    try {
      if (editingId) {
        const updated = await editTransaction(editingId, request)
        setTransactions((prev) => prev.map((t) => (t.id === updated.id ? updated : t)))
      } else {
        const created = await createTransaction(request)
        setTransactions((prev) => [created, ...prev])
      }
      cancelEdit()
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    setError(null)
    setDeleting(true)
    try {
      await deleteTransaction(deleteTarget.id)
      setTransactions((prev) => prev.filter((t) => t.id !== deleteTarget.id))
      setDeleteTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setDeleting(false)
    }
  }

  // Clears the stale banner and retries whichever fetches failed - the table's own and the
  // lookup lists behind its name columns - so names don't stay as raw ids after a retry.
  function retry() {
    setError(null)
    reloadFailed(categoriesState, accountsState, paymentMethodsState, transactionsState)
  }

  return (
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Transactions
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Every income and expense entry, filterable by date range, category, account, and payment
        method.
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
            value={filters.categoryId ?? ''}
            onChange={(e) => updateFilter({ categoryId: e.target.value || undefined })}
            aria-label="Category filter"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="">All categories</MenuItem>
            {categories?.map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {c.name}
              </MenuItem>
            ))}
          </Select>
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
          <Select
            size="small"
            displayEmpty
            value={filters.paymentMethodId ?? ''}
            onChange={(e) => updateFilter({ paymentMethodId: e.target.value || undefined })}
            aria-label="Payment method filter"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="">All payment methods</MenuItem>
            {paymentMethods?.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.name}
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
                <TableCell>Category</TableCell>
                <TableCell>Account</TableCell>
                <TableCell>Payment Method</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell>Description</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={transactionsState} onRetry={retry} columns={7} actionsColumn>
              {transactions?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} align="center">
                    <Typography color="text.secondary">No transactions found.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {transactions?.map((transaction) => (
                <TableRow key={transaction.id}>
                  <TableCell>{transaction.date}</TableCell>
                  <TableCell>{categoryName(transaction.categoryId)}</TableCell>
                  <TableCell>{accountName(transaction.accountId)}</TableCell>
                  <TableCell>{paymentMethodName(transaction.paymentMethodId)}</TableCell>
                  <TableCell align="right">
                    {transaction.type === 'EXPENSE' ? '-' : '+'}
                    {transaction.amount.toFixed(2)}
                  </TableCell>
                  <TableCell>{transaction.description}</TableCell>
                  <TableCell align="right">
                    <IconButton
                      size="small"
                      aria-label="Edit"
                      onClick={() => startEdit(transaction)}
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      aria-label="Delete"
                      onClick={() => setDeleteTarget(transaction)}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} sx={{ mt: 0, mb: 3 }} />

      <Paper variant="outlined" sx={{ p: 2, maxWidth: 720 }}>
        <Typography variant="subtitle1" gutterBottom>
          {editingId ? 'Edit transaction' : 'Add transaction'}
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
          <TextField
            label="Amount"
            type="number"
            size="small"
            value={form.amount}
            onChange={(e) => setForm((prev) => ({ ...prev, amount: e.target.value }))}
            slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
          />
          <Select
            size="small"
            displayEmpty
            value={form.categoryId}
            onChange={(e) => setForm((prev) => ({ ...prev, categoryId: e.target.value }))}
            aria-label="Category"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="" disabled>
              Category
            </MenuItem>
            {categories?.map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {c.name}
              </MenuItem>
            ))}
          </Select>
          <Select
            size="small"
            displayEmpty
            value={form.accountId}
            onChange={(e) => setForm((prev) => ({ ...prev, accountId: e.target.value }))}
            aria-label="Account"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="" disabled>
              Account
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
            value={form.paymentMethodId}
            onChange={(e) => setForm((prev) => ({ ...prev, paymentMethodId: e.target.value }))}
            aria-label="Payment Method"
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="" disabled>
              Payment Method
            </MenuItem>
            {paymentMethods?.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.name}
              </MenuItem>
            ))}
          </Select>
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

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete this transaction?"
        body="This permanently removes the transaction and cannot be undone."
        confirmLabel="Delete transaction"
        loading={deleting}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  )
}
