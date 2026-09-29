import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Paper,
  Select,
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
import { useVehicles } from '../../api/vehicles/vehiclesQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { ResponsiveFilterBar } from '../../components/layout/ResponsiveFilterBar'
import { PaginationControls } from '../../components/table/PaginationControls'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { nameLookup } from '../../utils/nameLookup'
import { TransactionFormFields } from './TransactionFormFields'
import {
  emptyTransactionForm,
  isTransactionFormValid,
  type TransactionFormValues,
} from './transactionForm'

const PAGE_SIZE = 20

/**
 * Transaction list/table with filter controls (date range, category, account, payment method),
 * plus a create/edit form and delete-with-confirmation (F004 spec). One combined form toggles
 * between create and edit mode - the fields (date/amount/category/account/payment
 * method/description/additional notes) are identical for both, matching F004's
 * plain-in-place-edit semantics (no versioning).
 *
 * Responsive (F021): at every size the header's Add button and each row's/card's Edit open the
 * fields (`TransactionFormFields`) in a `ResponsiveDialog` (full screen below `sm`).
 */
export function TransactionsPage() {
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<TransactionFilter>({})
  const [error, setError] = useState<string | null>(null)
  const location = useLocation()
  const navigate = useNavigate()

  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const paymentMethodsQuery = usePaymentMethods()
  const paymentMethods = paymentMethodsQuery.data
  const paymentMethodsState = useQueryState(paymentMethodsQuery, setError)
  const vehiclesQuery = useVehicles()
  const vehicles = vehiclesQuery.data
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

  // F024: the Fuel page's "add fuel transaction" action arrives here with the fuel category
  // pre-selected via navigation state, so the dialog opens straight into an add form for it - read
  // once, in the initial-state lazy initializers below, rather than in an effect (setting state
  // synchronously inside an effect triggers a cascading render, React Compiler's lint rule).
  const initialPresetCategoryId = (location.state as { presetCategoryId?: string } | null)
    ?.presetCategoryId

  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState(() => emptyTransactionForm(initialPresetCategoryId ?? ''))
  const [saving, setSaving] = useState(false)
  const [formDialogOpen, setFormDialogOpen] = useState(() => Boolean(initialPresetCategoryId))

  const [deleteTarget, setDeleteTarget] = useState<Transaction | null>(null)
  const [deleting, setDeleting] = useState(false)

  function updateFilter(patch: Partial<TransactionFilter>) {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }

  function clearFilters() {
    setFilters({})
    setPage(0)
  }

  const activeFilterCount = Object.values(filters).filter(Boolean).length

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

  function updateForm(patch: Partial<TransactionFormValues>) {
    setForm((prev) => ({ ...prev, ...patch }))
  }

  function startAdd(presetCategoryId = '') {
    setEditingId(null)
    setForm(emptyTransactionForm(presetCategoryId))
    setFormDialogOpen(true)
  }

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
      vehicleId: transaction.vehicleId ?? '',
      fuelType: transaction.fuelType ?? '',
      liters: transaction.liters !== null ? String(transaction.liters) : '',
      pricePerLiter: transaction.pricePerLiter !== null ? String(transaction.pricePerLiter) : '',
      kmSinceLastFill:
        transaction.kmSinceLastFill !== null ? String(transaction.kmSinceLastFill) : '',
      odometer: transaction.odometer !== null ? String(transaction.odometer) : '',
    })
    setFormDialogOpen(true)
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(emptyTransactionForm())
    setFormDialogOpen(false)
  }

  // Clears the navigation state right after consuming it, so a later back-navigation to this page
  // doesn't reopen the dialog. `navigate` itself (no `setState`) is a legitimate effect - only the
  // initial React state above needs to be synchronous.
  useEffect(() => {
    if (initialPresetCategoryId) {
      navigate(location.pathname, { replace: true, state: null })
    }
    // Runs once on mount only: the whole point is to consume the location state this page
    // mounted with, not to react to `navigate`'s own update of it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const isFuelCategorySelected = categories?.find((c) => c.id === form.categoryId)?.fuelCategory

  async function handleSubmit() {
    if (!isTransactionFormValid(form, categories)) return
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
      ...(isFuelCategorySelected
        ? {
            vehicleId: form.vehicleId,
            fuelType: form.fuelType as
              'ETANOL' | 'ETANOL_ADITIVADO' | 'GASOLINA' | 'GASOLINA_ADITIVADA',
            liters: Number(form.liters),
            pricePerLiter: Number(form.pricePerLiter),
            kmSinceLastFill: form.kmSinceLastFill ? Number(form.kmSinceLastFill) : undefined,
            odometer: form.odometer ? Number(form.odometer) : undefined,
          }
        : {}),
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

  const columns: ResponsiveColumn<Transaction>[] = [
    { key: 'date', header: 'Date', render: (t) => t.date },
    { key: 'category', header: 'Category', render: (t) => categoryName(t.categoryId) },
    { key: 'account', header: 'Account', render: (t) => accountName(t.accountId) },
    {
      key: 'paymentMethod',
      header: 'Payment Method',
      render: (t) => paymentMethodName(t.paymentMethodId),
      tabletPriority: 'low',
    },
    {
      key: 'amount',
      header: 'Amount',
      align: 'right',
      render: (t) => `${t.type === 'EXPENSE' ? '-' : '+'}${t.amount.toFixed(2)}`,
    },
    { key: 'description', header: 'Description', render: (t) => t.description },
  ]

  function rowActions(transaction: Transaction) {
    return (
      <>
        <IconButton size="small" aria-label="Edit" onClick={() => startEdit(transaction)}>
          <EditIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" aria-label="Delete" onClick={() => setDeleteTarget(transaction)}>
          <DeleteIcon fontSize="small" />
        </IconButton>
      </>
    )
  }

  // The card is ordered for a phone: what and how much first, then when and where.
  function renderCard(transaction: Transaction, actions: ReactNode) {
    const expense = transaction.type === 'EXPENSE'
    return (
      <Paper variant="outlined" sx={{ p: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
          <Typography variant="subtitle1" component="div" sx={{ overflowWrap: 'anywhere' }}>
            {transaction.description}
          </Typography>
          <Typography
            variant="subtitle1"
            component="div"
            sx={{
              flexShrink: 0,
              fontWeight: 600,
              color: expense ? 'error.main' : 'success.main',
            }}
          >
            {expense ? '-' : '+'}
            {transaction.amount.toFixed(2)}
          </Typography>
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {transaction.date} · {categoryName(transaction.categoryId)}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {accountName(transaction.accountId)} · {paymentMethodName(transaction.paymentMethodId)}
        </Typography>
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 0.5 }}>{actions}</Box>
      </Paper>
    )
  }

  const submitLabel = editingId ? 'Save changes' : 'Add'
  const submitButton = (
    <Button
      variant="contained"
      disabled={saving || !isTransactionFormValid(form, categories)}
      onClick={() => void handleSubmit()}
    >
      {submitLabel}
    </Button>
  )

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
          Transactions
        </Typography>
        <Button variant="contained" onClick={() => startAdd()}>
          Add transaction
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Every income and expense entry, filterable by date range, category, account, and payment
        method.
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
      </ResponsiveFilterBar>

      <Box sx={{ mb: 2 }}>
        <ResponsiveTable
          aria-label="Transactions"
          columns={columns}
          rows={transactions}
          getRowKey={(t) => t.id}
          state={tableState}
          onRetry={retry}
          actions={rowActions}
          renderCard={renderCard}
          emptyMessage="No transactions found."
        />
      </Box>

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} sx={{ mt: 0, mb: 3 }} />

      <ResponsiveDialog open={formDialogOpen} onClose={saving ? undefined : cancelEdit}>
        <DialogTitle>{editingId ? 'Edit transaction' : 'Add transaction'}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <TransactionFormFields
                form={form}
                onChange={updateForm}
                categories={categories}
                accounts={openAccounts}
                paymentMethods={paymentMethods}
                vehicles={vehicles}
              />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={cancelEdit} disabled={saving}>
            Cancel
          </Button>
          {submitButton}
        </DialogActions>
      </ResponsiveDialog>

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
