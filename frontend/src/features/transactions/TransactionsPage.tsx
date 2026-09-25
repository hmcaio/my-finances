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
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useCategories } from '../../api/categories/categoriesQueries'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import type { Transaction, TransactionFilter } from '../../api/transactions/transactions'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useEditTransaction,
  useTransactions,
} from '../../api/transactions/transactionsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { DataTableBody } from '../../components/table/DataTableBody'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { PaginationControls } from '../../components/table/PaginationControls'
import { today } from '../../utils/localDate'
import { nameLookup } from '../../utils/nameLookup'

/** A fresh form, built per use so its date is today's local date rather than the date the page
 * module was first loaded. */
function emptyForm() {
  return {
    date: today(),
    amount: '',
    categoryId: '',
    accountId: '',
    paymentMethodId: '',
    description: '',
    additionalNotes: '',
  }
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

  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const paymentMethodsQuery = usePaymentMethods()
  const paymentMethods = paymentMethodsQuery.data
  const paymentMethodsState = useQueryState(paymentMethodsQuery, setError)
  const transactionsQuery = useTransactions(filters, page, PAGE_SIZE)
  const transactions = transactionsQuery.data?.content
  const pageInfo = transactionsQuery.data
    ? {
        number: transactionsQuery.data.page.number,
        totalPages: transactionsQuery.data.page.totalPages,
      }
    : null
  const transactionsState = useQueryState(transactionsQuery, setError)
  const createMutation = useCreateTransaction()
  const editMutation = useEditTransaction()
  const deleteMutation = useDeleteTransaction()

  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState(emptyForm)
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
  // An investment account takes no transactions (money moves through transfers, F008), so the
  // form never offers one.
  const openAccounts = useMemo(
    () => (accounts ?? []).filter((a) => !a.closed && a.type !== 'INVESTMENT'),
    [accounts],
  )

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
    setForm(emptyForm())
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
        await editMutation.mutateAsync({ id: editingId, ...request })
      } else {
        await createMutation.mutateAsync(request)
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
      await deleteMutation.mutateAsync(deleteTarget.id)
      setDeleteTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setDeleting(false)
    }
  }

  // One load state for the table plus the lookup lists behind its name columns: rows show only
  // once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(
    categoriesState,
    accountsState,
    paymentMethodsState,
    transactionsState,
  )
  function retry() {
    setError(null)
    tableState.reload()
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
            <DataTableBody state={tableState} onRetry={retry} columns={7} actionsColumn>
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
