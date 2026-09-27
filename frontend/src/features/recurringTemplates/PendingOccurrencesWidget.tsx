import { useMemo, useState, type ReactNode } from 'react'
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
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CloseIcon from '@mui/icons-material/Close'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useCategories } from '../../api/categories/categoriesQueries'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import type { PendingRecurringOccurrence } from '../../api/recurringTemplates/recurringTemplates'
import {
  useConfirmPendingOccurrence,
  useDismissPendingOccurrence,
  usePendingRecurringOccurrences,
  useRecurringTemplates,
} from '../../api/recurringTemplates/recurringTemplatesQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { nameLookup } from '../../utils/nameLookup'

/**
 * "Upcoming recurring bills" widget (F007 spec, embedded on F012's dashboard and on {@link
 * RecurringTemplatesPage}). Entirely self-contained
 * (loads its own reference data) so it can be dropped onto any page without props, same
 * embeddable-component spirit as F004/F005's `AccountTransactionList`/`AccountTransferList` -
 * those take an `accountId`; this one has nothing to scope by, so it takes nothing.
 *
 * Confirming opens a pre-filled, fully overridable form (PRD S5.7/S6.5): amount/date/account
 * default from the occurrence/template, and payment method - which the template has no default
 * for - is always required.
 *
 * Responsive (F021): this is a genuine data table (due date, description, category, account,
 * amount, each row with confirm/dismiss actions) rather than a list-with-add-form, so it gets the
 * same `ResponsiveTable` treatment as any 3+ column list (cards below `sm`); tablet hides only
 * Category, matching the recurring-templates settings table's own choice. The confirm form moves
 * into a `ResponsiveDialog` (full screen below `sm`) with its fields in a `FormGrid`.
 */
export function PendingOccurrencesWidget() {
  const [error, setError] = useState<string | null>(null)
  const templatesQuery = useRecurringTemplates()
  const templates = templatesQuery.data
  const templatesState = useQueryState(templatesQuery, setError)
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const accountsQuery = useAccounts()
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const paymentMethodsQuery = usePaymentMethods()
  const paymentMethods = paymentMethodsQuery.data
  const paymentMethodsState = useQueryState(paymentMethodsQuery, setError)
  const pendingQuery = usePendingRecurringOccurrences()
  const pending = pendingQuery.data
  const pendingState = useQueryState(pendingQuery, setError)
  const confirmMutation = useConfirmPendingOccurrence()
  const dismissMutation = useDismissPendingOccurrence()

  const [confirmTarget, setConfirmTarget] = useState<PendingRecurringOccurrence | null>(null)
  const [confirmAmount, setConfirmAmount] = useState('')
  const [confirmDate, setConfirmDate] = useState('')
  const [confirmAccountId, setConfirmAccountId] = useState('')
  const [confirmPaymentMethodId, setConfirmPaymentMethodId] = useState('')
  const [confirming, setConfirming] = useState(false)

  const [dismissTarget, setDismissTarget] = useState<PendingRecurringOccurrence | null>(null)
  const [dismissing, setDismissing] = useState(false)

  const templateById = useMemo(() => {
    const map = new Map((templates ?? []).map((t) => [t.id, t]))
    return (id: string) => map.get(id)
  }, [templates])
  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])
  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])

  function openConfirm(occurrence: PendingRecurringOccurrence) {
    const template = templateById(occurrence.templateId)
    setConfirmTarget(occurrence)
    setConfirmAmount(String(occurrence.amount))
    setConfirmDate(occurrence.dueDate)
    setConfirmAccountId(template?.accountId ?? '')
    setConfirmPaymentMethodId('')
  }

  function closeConfirm() {
    setConfirmTarget(null)
  }

  function isConfirmValid() {
    return (
      Number(confirmAmount) > 0 &&
      confirmDate !== '' &&
      confirmAccountId !== '' &&
      confirmPaymentMethodId !== ''
    )
  }

  async function handleConfirm() {
    if (!confirmTarget || !isConfirmValid()) return
    setError(null)
    setConfirming(true)
    try {
      await confirmMutation.mutateAsync({
        id: confirmTarget.id,
        amount: Number(confirmAmount),
        date: confirmDate,
        accountId: confirmAccountId,
        paymentMethodId: confirmPaymentMethodId,
      })
      closeConfirm()
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setConfirming(false)
    }
  }

  async function handleDismiss() {
    if (!dismissTarget) return
    setError(null)
    setDismissing(true)
    try {
      await dismissMutation.mutateAsync(dismissTarget.id)
      setDismissTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setDismissing(false)
    }
  }

  // One load state for the table plus the lookup lists behind its name columns: rows show only
  // once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(
    templatesState,
    categoriesState,
    accountsState,
    paymentMethodsState,
    pendingState,
  )
  function retry() {
    setError(null)
    tableState.reload()
  }

  const columns: ResponsiveColumn<PendingRecurringOccurrence>[] = [
    { key: 'dueDate', header: 'Due date', render: (o) => o.dueDate },
    {
      key: 'description',
      header: 'Description',
      render: (o) => templateById(o.templateId)?.description ?? '—',
    },
    {
      key: 'category',
      header: 'Category',
      tabletPriority: 'low',
      render: (o) => {
        const template = templateById(o.templateId)
        return template ? categoryName(template.categoryId) : '—'
      },
    },
    {
      key: 'account',
      header: 'Account',
      render: (o) => {
        const template = templateById(o.templateId)
        return template ? accountName(template.accountId) : '—'
      },
    },
    { key: 'amount', header: 'Amount', align: 'right', render: (o) => o.amount.toFixed(2) },
  ]

  function rowActions(occurrence: PendingRecurringOccurrence) {
    return (
      <>
        <IconButton
          size="small"
          aria-label="Confirm occurrence"
          onClick={() => openConfirm(occurrence)}
        >
          <CheckCircleIcon fontSize="small" />
        </IconButton>
        <IconButton
          size="small"
          aria-label="Dismiss occurrence"
          onClick={() => setDismissTarget(occurrence)}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
      </>
    )
  }

  function renderCard(occurrence: PendingRecurringOccurrence, actions: ReactNode) {
    const template = templateById(occurrence.templateId)
    return (
      <Paper variant="outlined" sx={{ p: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
          <Typography variant="subtitle1" component="div" sx={{ overflowWrap: 'anywhere' }}>
            {template?.description ?? '—'}
          </Typography>
          <Typography variant="subtitle1" component="div" sx={{ flexShrink: 0, fontWeight: 600 }}>
            {occurrence.amount.toFixed(2)}
          </Typography>
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {occurrence.dueDate} · {template ? categoryName(template.categoryId) : '—'}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {template ? accountName(template.accountId) : '—'}
        </Typography>
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 0.5 }}>{actions}</Box>
      </Paper>
    )
  }

  return (
    <Box>
      <Typography variant="h5" component="h2" gutterBottom>
        Upcoming recurring bills
      </Typography>

      <ErrorAlert
        message={confirmTarget !== null ? null : error}
        onDismiss={() => setError(null)}
      />

      <ResponsiveTable
        aria-label="Upcoming recurring bills"
        columns={columns}
        rows={pending}
        getRowKey={(occurrence) => occurrence.id}
        state={tableState}
        onRetry={retry}
        actions={rowActions}
        renderCard={renderCard}
        emptyMessage="Nothing pending right now."
      />

      <ResponsiveDialog
        open={confirmTarget !== null}
        onClose={confirming ? undefined : closeConfirm}
      >
        <DialogTitle>Confirm recurring occurrence</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <TextField
                label="Amount"
                type="number"
                size="small"
                value={confirmAmount}
                onChange={(e) => setConfirmAmount(e.target.value)}
                slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
              />
              <TextField
                label="Date"
                type="date"
                size="small"
                value={confirmDate}
                onChange={(e) => setConfirmDate(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <Select
                size="small"
                displayEmpty
                value={confirmAccountId}
                onChange={(e) => setConfirmAccountId(e.target.value)}
                aria-label="Account"
              >
                <MenuItem value="" disabled>
                  Account
                </MenuItem>
                {accounts?.map((a) => (
                  <MenuItem key={a.id} value={a.id}>
                    {a.name}
                  </MenuItem>
                ))}
              </Select>
              <Select
                size="small"
                displayEmpty
                value={confirmPaymentMethodId}
                onChange={(e) => setConfirmPaymentMethodId(e.target.value)}
                aria-label="Payment Method"
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
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeConfirm} disabled={confirming}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={confirming || !isConfirmValid()}
            onClick={() => void handleConfirm()}
          >
            Confirm
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ConfirmDialog
        open={dismissTarget !== null}
        title="Dismiss this occurrence?"
        body="No transaction will be created for it. This can't be undone, but the next catch-up run won't regenerate it either."
        confirmLabel="Dismiss"
        loading={dismissing}
        onConfirm={() => void handleDismiss()}
        onCancel={() => setDismissTarget(null)}
      />
    </Box>
  )
}
