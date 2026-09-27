import { useMemo, useState, type ReactNode } from 'react'
import {
  Box,
  Button,
  Chip,
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
import PauseIcon from '@mui/icons-material/Pause'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { useCategories } from '../../api/categories/categoriesQueries'
import type { RecurringTemplate } from '../../api/recurringTemplates/recurringTemplates'
import {
  useCreateRecurringTemplate,
  useReactivateRecurringTemplate,
  useRecurringTemplates,
  useSetRecurringTemplateCap,
  useStopRecurringTemplate,
} from '../../api/recurringTemplates/recurringTemplatesQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { currentMonth } from '../../utils/localDate'
import { nameLookup } from '../../utils/nameLookup'
import { PendingOccurrencesWidget } from './PendingOccurrencesWidget'
import {
  RecurringTemplateAmountField,
  RecurringTemplateDayOfMonthField,
  RecurringTemplateEditFields,
} from './RecurringTemplateEditFields'
import { isRecurringTemplateEditValid } from './recurringTemplateEdit'

const EMPTY_CREATE_FORM = {
  categoryId: '',
  accountId: '',
  description: '',
  amount: '',
  dayOfMonth: '1',
}

/**
 * Recurring templates settings screen (F007 spec): a list of templates (description, category,
 * account, current amount/day-of-month, active/inactive) with inline amount/day edit and a
 * stop/reactivate toggle, plus a create dialog - all following F006's `BudgetsPage` layout
 * conventions. The "upcoming recurring bills" widget ({@link PendingOccurrencesWidget}) is
 * embedded below; F012's dashboard embeds the same component rather than duplicating it.
 *
 * Responsive (F021): the header's Add button opens the create fields in a `ResponsiveDialog` at
 * every size (there is no form panel below the table). The amount/day-of-month edit stays inline in
 * the row on tablet/desktop; on mobile the card's Edit opens the same fields
 * (`RecurringTemplateEditFields`) in a full-screen dialog. Tablet hides only Category
 * (`tabletPriority: 'low'`, behind the row expander) - Description already identifies the bill and
 * Account/Amount/Day/Status are what's needed to manage it; Category matters more for the Budgets
 * page's own report.
 */
export function RecurringTemplatesPage() {
  const [error, setError] = useState<string | null>(null)
  const isMobile = useIsMobile()
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const accountsQuery = useAccounts()
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const templatesQuery = useRecurringTemplates()
  const templates = templatesQuery.data
  const templatesState = useQueryState(templatesQuery, setError)
  const createMutation = useCreateRecurringTemplate()
  const capMutation = useSetRecurringTemplateCap()
  const stopMutation = useStopRecurringTemplate()
  const reactivateMutation = useReactivateRecurringTemplate()

  const [createForm, setCreateForm] = useState(EMPTY_CREATE_FORM)
  const [creating, setCreating] = useState(false)
  const [addDialogOpen, setAddDialogOpen] = useState(false)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editAmount, setEditAmount] = useState('')
  const [editDayOfMonth, setEditDayOfMonth] = useState('')
  const [savingCap, setSavingCap] = useState(false)

  const [togglingId, setTogglingId] = useState<string | null>(null)

  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])
  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])
  // An investment account takes no recurring templates (money moves through transfers, F008), so
  // the form never offers one.
  const templateAccounts = useMemo(
    () => (accounts ?? []).filter((a) => a.type !== 'INVESTMENT'),
    [accounts],
  )

  function isCreateFormValid() {
    return (
      createForm.categoryId !== '' &&
      createForm.accountId !== '' &&
      createForm.description.trim() !== '' &&
      isRecurringTemplateEditValid(createForm.amount, createForm.dayOfMonth)
    )
  }

  function closeAddDialog() {
    setAddDialogOpen(false)
    setError(null)
  }

  async function handleCreate() {
    if (!isCreateFormValid()) return
    setError(null)
    setCreating(true)
    try {
      await createMutation.mutateAsync({
        categoryId: createForm.categoryId,
        accountId: createForm.accountId,
        description: createForm.description.trim(),
        amount: Number(createForm.amount),
        dayOfMonth: Number(createForm.dayOfMonth),
        effectiveFrom: currentMonth(),
      })
      setCreateForm(EMPTY_CREATE_FORM)
      setAddDialogOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setCreating(false)
    }
  }

  function startEditCap(template: RecurringTemplate) {
    setEditingId(template.id)
    setEditAmount(template.currentAmount !== null ? String(template.currentAmount) : '')
    setEditDayOfMonth(template.currentDayOfMonth !== null ? String(template.currentDayOfMonth) : '')
  }

  function cancelEditCap() {
    setEditingId(null)
    setEditAmount('')
    setEditDayOfMonth('')
  }

  async function saveEditCap(id: string) {
    if (!isRecurringTemplateEditValid(editAmount, editDayOfMonth)) return
    setError(null)
    setSavingCap(true)
    try {
      await capMutation.mutateAsync({
        id,
        amount: Number(editAmount),
        dayOfMonth: Number(editDayOfMonth),
        effectiveFrom: currentMonth(),
      })
      cancelEditCap()
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setSavingCap(false)
    }
  }

  async function toggleActive(template: RecurringTemplate) {
    setError(null)
    setTogglingId(template.id)
    try {
      if (template.active) await stopMutation.mutateAsync(template.id)
      else await reactivateMutation.mutateAsync(template.id)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setTogglingId(null)
    }
  }

  // One load state for the table plus the lookup lists behind its name columns: rows show only
  // once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(categoriesState, accountsState, templatesState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  // On mobile the edit happens in a dialog opened from the card, so the card stays plain text.
  const editDialogOpen = isMobile && editingId !== null

  const columns: ResponsiveColumn<RecurringTemplate>[] = [
    { key: 'description', header: 'Description', render: (t) => t.description },
    {
      key: 'category',
      header: 'Category',
      tabletPriority: 'low',
      render: (t) => categoryName(t.categoryId),
    },
    { key: 'account', header: 'Account', render: (t) => accountName(t.accountId) },
    {
      key: 'amount',
      header: 'Amount',
      align: 'right',
      render: (t) =>
        editingId === t.id && !isMobile ? (
          <RecurringTemplateAmountField
            value={editAmount}
            onChange={setEditAmount}
            autoFocus
            sx={{ width: 110 }}
          />
        ) : t.currentAmount !== null ? (
          t.currentAmount.toFixed(2)
        ) : (
          <Typography color="text.secondary" component="span">
            No cap yet
          </Typography>
        ),
    },
    {
      key: 'dayOfMonth',
      header: 'Day of month',
      align: 'right',
      render: (t) =>
        editingId === t.id && !isMobile ? (
          <RecurringTemplateDayOfMonthField
            value={editDayOfMonth}
            onChange={setEditDayOfMonth}
            sx={{ width: 80 }}
          />
        ) : (
          (t.currentDayOfMonth ?? '—')
        ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (t) => (
        <Chip
          size="small"
          label={t.active ? 'Active' : 'Stopped'}
          color={t.active ? 'success' : 'default'}
        />
      ),
    },
  ]

  function rowActions(template: RecurringTemplate) {
    const editingInline = editingId === template.id && !isMobile
    return (
      <>
        <InlineEditActions
          editing={editingInline}
          onEdit={() => startEditCap(template)}
          onSave={() => void saveEditCap(template.id)}
          onCancel={cancelEditCap}
          editLabel="Edit amount and day"
          saveLabel="Save cap"
          saving={savingCap}
        />
        {!editingInline && (
          <IconButton
            size="small"
            aria-label={template.active ? 'Stop' : 'Reactivate'}
            disabled={togglingId === template.id}
            onClick={() => void toggleActive(template)}
          >
            {template.active ? <PauseIcon fontSize="small" /> : <PlayArrowIcon fontSize="small" />}
          </IconButton>
        )}
      </>
    )
  }

  // The card leads with what/how much, like Transactions: description and status up top, then
  // category/account, then amount and day of month.
  function renderCard(template: RecurringTemplate, actions: ReactNode) {
    return (
      <Paper variant="outlined" sx={{ p: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
          <Typography variant="subtitle1" component="div" sx={{ overflowWrap: 'anywhere' }}>
            {template.description}
          </Typography>
          <Chip
            size="small"
            label={template.active ? 'Active' : 'Stopped'}
            color={template.active ? 'success' : 'default'}
          />
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {categoryName(template.categoryId)} · {accountName(template.accountId)}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {template.currentAmount !== null ? template.currentAmount.toFixed(2) : 'No cap yet'} · Day{' '}
          {template.currentDayOfMonth ?? '—'}
        </Typography>
        {actions && (
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 1 }}>{actions}</Box>
        )}
      </Paper>
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
          Recurring Templates
        </Typography>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          Add recurring template
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Versioned recurring bills/income. Editing the amount or day-of-month always takes effect
        from the current month forward - it never rewrites past pending or confirmed occurrences.
      </Typography>

      {/* While a dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert
        message={addDialogOpen || editDialogOpen ? null : error}
        onDismiss={() => setError(null)}
      />

      <Box sx={{ mb: 3 }}>
        <ResponsiveTable
          aria-label="Recurring templates"
          columns={columns}
          rows={templates}
          getRowKey={(template) => template.id}
          state={tableState}
          onRetry={retry}
          actions={rowActions}
          renderCard={renderCard}
          emptyMessage="No recurring templates yet."
        />
      </Box>

      <ResponsiveDialog open={addDialogOpen} onClose={closeAddDialog}>
        <DialogTitle>Add recurring template</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <Select
                size="small"
                displayEmpty
                value={createForm.categoryId}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, categoryId: e.target.value }))}
                aria-label="Category"
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
                value={createForm.accountId}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, accountId: e.target.value }))}
                aria-label="Account"
              >
                <MenuItem value="" disabled>
                  Account
                </MenuItem>
                {templateAccounts.map((a) => (
                  <MenuItem key={a.id} value={a.id}>
                    {a.name}
                  </MenuItem>
                ))}
              </Select>
              <TextField
                label="Description"
                size="small"
                value={createForm.description}
                onChange={(e) =>
                  setCreateForm((prev) => ({ ...prev, description: e.target.value }))
                }
                slotProps={{ htmlInput: { maxLength: 150 } }}
                sx={{ gridColumn: '1 / -1' }}
              />
              <TextField
                label="Amount"
                type="number"
                size="small"
                value={createForm.amount}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, amount: e.target.value }))}
                slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
              />
              <TextField
                label="Day of month"
                type="number"
                size="small"
                value={createForm.dayOfMonth}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, dayOfMonth: e.target.value }))}
                slotProps={{ htmlInput: { min: 1, max: 31 } }}
              />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeAddDialog} disabled={creating}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={creating || !isCreateFormValid()}
            onClick={() => void handleCreate()}
          >
            Add
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ResponsiveDialog open={editDialogOpen} onClose={savingCap ? undefined : cancelEditCap}>
        <DialogTitle>Edit amount and day</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid columns={1}>
              <RecurringTemplateEditFields
                amount={editAmount}
                dayOfMonth={editDayOfMonth}
                onAmountChange={setEditAmount}
                onDayOfMonthChange={setEditDayOfMonth}
              />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={cancelEditCap} disabled={savingCap}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={savingCap || !isRecurringTemplateEditValid(editAmount, editDayOfMonth)}
            onClick={() => editingId && void saveEditCap(editingId)}
          >
            Save cap
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <PendingOccurrencesWidget />
    </Box>
  )
}
