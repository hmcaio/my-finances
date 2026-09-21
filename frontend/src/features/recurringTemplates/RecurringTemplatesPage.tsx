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
import PauseIcon from '@mui/icons-material/Pause'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import { getAccounts } from '../../api/accounts'
import { getCategories } from '../../api/categories'
import {
  createRecurringTemplate,
  getRecurringTemplates,
  reactivateRecurringTemplate,
  setRecurringTemplateCap,
  stopRecurringTemplate,
  type RecurringTemplate,
} from '../../api/recurringTemplates'
import { defaultErrorMessage } from '../../api/apiError'
import { ErrorAlert } from '../../components/ErrorAlert'
import { InlineEditActions } from '../../components/InlineEditActions'
import { DataTableBody } from '../../components/DataTableBody'
import { combineLoadState, useAsyncData } from '../../hooks/useAsyncData'
import { currentMonth } from '../../utils/localDate'
import { nameLookup } from '../../utils/nameLookup'
import { PendingOccurrencesWidget } from './PendingOccurrencesWidget'

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
 * stop/reactivate toggle, plus a create form - all following F006's `BudgetsPage` layout
 * conventions. The "upcoming recurring bills" widget ({@link PendingOccurrencesWidget}) is
 * embedded below; F012's real dashboard (not built yet) will embed the same component rather than
 * duplicating it.
 */
export function RecurringTemplatesPage() {
  const [error, setError] = useState<string | null>(null)
  const { data: categories, ...categoriesState } = useAsyncData(getCategories, [], {
    onError: setError,
  })
  const { data: accounts, ...accountsState } = useAsyncData(() => getAccounts(false), [], {
    onError: setError,
  })
  const {
    data: templates,
    setData: setTemplates,
    ...templatesState
  } = useAsyncData(getRecurringTemplates, [], { onError: setError })

  const [createForm, setCreateForm] = useState(EMPTY_CREATE_FORM)
  const [creating, setCreating] = useState(false)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editAmount, setEditAmount] = useState('')
  const [editDayOfMonth, setEditDayOfMonth] = useState('')
  const [savingCap, setSavingCap] = useState(false)

  const [togglingId, setTogglingId] = useState<string | null>(null)

  // PendingOccurrencesWidget fetches its own data once on mount and takes no props (deliberately,
  // so F012's dashboard can embed it as-is) - remounting it via this key is how this page tells it
  // to refetch after a cap edit changes what an already-generated pending occurrence should show,
  // or after creating a new template that catch-up may immediately generate a pending occurrence
  // for (effectiveFrom defaults to the current month, whose day-of-month may already have passed).
  const [pendingRefreshKey, setPendingRefreshKey] = useState(0)

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
      Number(createForm.amount) > 0 &&
      Number(createForm.dayOfMonth) >= 1 &&
      Number(createForm.dayOfMonth) <= 31
    )
  }

  async function handleCreate() {
    if (!isCreateFormValid()) return
    setError(null)
    setCreating(true)
    try {
      const created = await createRecurringTemplate({
        categoryId: createForm.categoryId,
        accountId: createForm.accountId,
        description: createForm.description.trim(),
        amount: Number(createForm.amount),
        dayOfMonth: Number(createForm.dayOfMonth),
        effectiveFrom: currentMonth(),
      })
      setTemplates((prev) => (prev ? [...prev, created] : [created]))
      setCreateForm(EMPTY_CREATE_FORM)
      setPendingRefreshKey((key) => key + 1)
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

  function isEditCapValid() {
    return Number(editAmount) > 0 && Number(editDayOfMonth) >= 1 && Number(editDayOfMonth) <= 31
  }

  async function saveEditCap(id: string) {
    if (!isEditCapValid()) return
    setError(null)
    setSavingCap(true)
    try {
      const updated = await setRecurringTemplateCap(id, {
        amount: Number(editAmount),
        dayOfMonth: Number(editDayOfMonth),
        effectiveFrom: currentMonth(),
      })
      setTemplates((prev) => prev?.map((t) => (t.id === id ? updated : t)) ?? null)
      setPendingRefreshKey((key) => key + 1)
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
      const updated = template.active
        ? await stopRecurringTemplate(template.id)
        : await reactivateRecurringTemplate(template.id)
      setTemplates((prev) => prev?.map((t) => (t.id === template.id ? updated : t)) ?? null)
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

  return (
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Recurring Templates
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Versioned recurring bills/income. Editing the amount or day-of-month always takes effect
        from the current month forward - it never rewrites past pending or confirmed occurrences.
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Description</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Account</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell align="right">Day of month</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={tableState} onRetry={retry} columns={7} actionsColumn>
              {templates?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} align="center">
                    <Typography color="text.secondary">No recurring templates yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {templates?.map((template) => (
                <TableRow key={template.id}>
                  <TableCell>{template.description}</TableCell>
                  <TableCell>{categoryName(template.categoryId)}</TableCell>
                  <TableCell>{accountName(template.accountId)}</TableCell>
                  <TableCell align="right">
                    {editingId === template.id ? (
                      <TextField
                        size="small"
                        type="number"
                        label="Amount"
                        value={editAmount}
                        onChange={(e) => setEditAmount(e.target.value)}
                        slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
                        autoFocus
                        sx={{ width: 110 }}
                      />
                    ) : template.currentAmount !== null ? (
                      template.currentAmount.toFixed(2)
                    ) : (
                      <Typography color="text.secondary" component="span">
                        No cap yet
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {editingId === template.id ? (
                      <TextField
                        size="small"
                        type="number"
                        label="Day"
                        value={editDayOfMonth}
                        onChange={(e) => setEditDayOfMonth(e.target.value)}
                        slotProps={{ htmlInput: { min: 1, max: 31 } }}
                        sx={{ width: 80 }}
                      />
                    ) : (
                      (template.currentDayOfMonth ?? '—')
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={template.active ? 'Active' : 'Stopped'}
                      color={template.active ? 'success' : 'default'}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <InlineEditActions
                      editing={editingId === template.id}
                      onEdit={() => startEditCap(template)}
                      onSave={() => void saveEditCap(template.id)}
                      onCancel={cancelEditCap}
                      editLabel="Edit amount and day"
                      saveLabel="Save cap"
                      saving={savingCap}
                    />
                    {editingId !== template.id && (
                      <IconButton
                        size="small"
                        aria-label={template.active ? 'Stop' : 'Reactivate'}
                        disabled={togglingId === template.id}
                        onClick={() => void toggleActive(template)}
                      >
                        {template.active ? (
                          <PauseIcon fontSize="small" />
                        ) : (
                          <PlayArrowIcon fontSize="small" />
                        )}
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, mb: 4, maxWidth: 720 }}>
        <Typography variant="subtitle1" gutterBottom>
          Add recurring template
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <Select
            size="small"
            displayEmpty
            value={createForm.categoryId}
            onChange={(e) => setCreateForm((prev) => ({ ...prev, categoryId: e.target.value }))}
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
            value={createForm.accountId}
            onChange={(e) => setCreateForm((prev) => ({ ...prev, accountId: e.target.value }))}
            aria-label="Account"
            sx={{ minWidth: 160 }}
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
            onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
            slotProps={{ htmlInput: { maxLength: 150 } }}
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
            sx={{ width: 130 }}
          />
          <Button
            variant="contained"
            disabled={creating || !isCreateFormValid()}
            onClick={() => void handleCreate()}
          >
            Add
          </Button>
        </Box>
      </Paper>

      <PendingOccurrencesWidget key={pendingRefreshKey} />
    </Box>
  )
}
