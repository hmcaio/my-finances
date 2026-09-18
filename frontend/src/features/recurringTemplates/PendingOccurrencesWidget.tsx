import { useEffect, useMemo, useState } from 'react'
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
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
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CloseIcon from '@mui/icons-material/Close'
import { getAccounts, type Account } from '../../api/accounts'
import { getCategories, type Category } from '../../api/categories'
import { getPaymentMethods, type PaymentMethod } from '../../api/paymentMethods'
import {
  confirmPendingRecurringOccurrence,
  dismissPendingRecurringOccurrence,
  getPendingRecurringOccurrences,
  getRecurringTemplates,
  type PendingRecurringOccurrence,
  type RecurringTemplate,
} from '../../api/recurringTemplates'
import { defaultErrorMessage } from '../../api/apiError'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorAlert } from '../../components/ErrorAlert'
import { LoadingTableRow } from '../../components/LoadingTableRow'
import { nameLookup } from '../../utils/nameLookup'

/**
 * "Upcoming recurring bills" widget (F007 spec, embedded on F012's future dashboard - not built
 * yet, so {@link RecurringTemplatesPage} embeds it directly for now). Entirely self-contained
 * (loads its own reference data) so it can be dropped onto any page without props, same
 * embeddable-component spirit as F004/F005's `AccountTransactionList`/`AccountTransferList` -
 * those take an `accountId`; this one has nothing to scope by, so it takes nothing.
 *
 * Confirming opens a pre-filled, fully overridable form (PRD S5.7/S6.5): amount/date/account
 * default from the occurrence/template, and payment method - which the template has no default
 * for - is always required.
 */
export function PendingOccurrencesWidget() {
  const [templates, setTemplates] = useState<RecurringTemplate[] | null>(null)
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [accounts, setAccounts] = useState<Account[] | null>(null)
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[] | null>(null)
  const [pending, setPending] = useState<PendingRecurringOccurrence[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [confirmTarget, setConfirmTarget] = useState<PendingRecurringOccurrence | null>(null)
  const [confirmAmount, setConfirmAmount] = useState('')
  const [confirmDate, setConfirmDate] = useState('')
  const [confirmAccountId, setConfirmAccountId] = useState('')
  const [confirmPaymentMethodId, setConfirmPaymentMethodId] = useState('')
  const [confirming, setConfirming] = useState(false)

  const [dismissTarget, setDismissTarget] = useState<PendingRecurringOccurrence | null>(null)
  const [dismissing, setDismissing] = useState(false)

  useEffect(() => {
    getRecurringTemplates()
      .then(setTemplates)
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
    getCategories()
      .then(setCategories)
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
    getAccounts(false)
      .then(setAccounts)
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
    getPaymentMethods()
      .then(setPaymentMethods)
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
    getPendingRecurringOccurrences()
      .then(setPending)
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
  }, [])

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
      await confirmPendingRecurringOccurrence(confirmTarget.id, {
        amount: Number(confirmAmount),
        date: confirmDate,
        accountId: confirmAccountId,
        paymentMethodId: confirmPaymentMethodId,
      })
      setPending((prev) => prev?.filter((o) => o.id !== confirmTarget.id) ?? null)
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
      await dismissPendingRecurringOccurrence(dismissTarget.id)
      setPending((prev) => prev?.filter((o) => o.id !== dismissTarget.id) ?? null)
      setDismissTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setDismissing(false)
    }
  }

  return (
    <Box>
      <Typography variant="h5" component="h2" gutterBottom>
        Upcoming recurring bills
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined">
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Due date</TableCell>
                <TableCell>Description</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Account</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {pending === null && <LoadingTableRow colSpan={6} variant="text" />}
              {pending?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    <Typography color="text.secondary">Nothing pending right now.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {pending?.map((occurrence) => {
                const template = templateById(occurrence.templateId)
                return (
                  <TableRow key={occurrence.id}>
                    <TableCell>{occurrence.dueDate}</TableCell>
                    <TableCell>{template?.description ?? '—'}</TableCell>
                    <TableCell>{template ? categoryName(template.categoryId) : '—'}</TableCell>
                    <TableCell>{template ? accountName(template.accountId) : '—'}</TableCell>
                    <TableCell align="right">{occurrence.amount.toFixed(2)}</TableCell>
                    <TableCell align="right">
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
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Dialog open={confirmTarget !== null} onClose={closeConfirm} fullWidth maxWidth="sm">
        <DialogTitle>Confirm recurring occurrence</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
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
      </Dialog>

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
