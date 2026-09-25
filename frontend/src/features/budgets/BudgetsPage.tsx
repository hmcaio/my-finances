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
import StopCircleOutlinedIcon from '@mui/icons-material/StopCircleOutlined'
import { useCategories } from '../../api/categories/categoriesQueries'
import type { Budget } from '../../api/budgets/budgets'
import {
  useBudgets,
  useCreateBudget,
  useSetBudgetCap,
  useStopBudget,
} from '../../api/budgets/budgetsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { DataTableBody } from '../../components/table/DataTableBody'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { currentMonth } from '../../utils/localDate'
import { nameLookup } from '../../utils/nameLookup'
import { BudgetVsActualReport } from './BudgetVsActualReport'

/**
 * Budgets screen (F006 spec): a settings-style list of budgeted categories with their current cap
 * plus an add-budget form (top half), and a budget-vs-actual view with a month picker and an
 * over-cap visual indicator (bottom half). Editing a cap or adding a new budget always takes
 * effect from the current month forward (PRD S5.6's "effective going forward only") - there's no
 * effective-month picker on either form, matching plan.md's "which month it takes effect from is
 * implicit - now/current month forward". A row can be stopped (issue #61, confirmed through a
 * dialog): it then reads "Stopped" and its edit action becomes "Resume budget" (a normal cap
 * edit); the add-budget picker still excludes it, since the category already has its budget row.
 */
export function BudgetsPage() {
  const [error, setError] = useState<string | null>(null)
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const budgetsQuery = useBudgets()
  const budgets = budgetsQuery.data
  const budgetsState = useQueryState(budgetsQuery, setError)
  const createMutation = useCreateBudget()
  const capMutation = useSetBudgetCap()
  const stopMutation = useStopBudget()

  const [newCategoryId, setNewCategoryId] = useState('')
  const [newCap, setNewCap] = useState('')
  const [adding, setAdding] = useState(false)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingCap, setEditingCap] = useState('')
  const [savingCap, setSavingCap] = useState(false)

  const [stopTarget, setStopTarget] = useState<Budget | null>(null)
  const [stopping, setStopping] = useState(false)

  const [reportMonth, setReportMonth] = useState(currentMonth())

  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])

  const unbudgetedExpenseCategories = useMemo(() => {
    const budgetedCategoryIds = new Set((budgets ?? []).map((b) => b.categoryId))
    return (categories ?? []).filter((c) => c.type === 'EXPENSE' && !budgetedCategoryIds.has(c.id))
  }, [categories, budgets])

  async function handleAdd() {
    if (!newCategoryId || Number(newCap) <= 0) return
    setError(null)
    setAdding(true)
    try {
      await createMutation.mutateAsync({
        categoryId: newCategoryId,
        monthlyCap: Number(newCap),
        effectiveFrom: currentMonth(),
      })
      setNewCategoryId('')
      setNewCap('')
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  function startEditCap(budget: Budget) {
    setEditingId(budget.id)
    setEditingCap(budget.currentCap !== null ? String(budget.currentCap) : '')
  }

  function cancelEditCap() {
    setEditingId(null)
    setEditingCap('')
  }

  async function saveEditCap(id: string) {
    if (Number(editingCap) <= 0) return
    setError(null)
    setSavingCap(true)
    try {
      await capMutation.mutateAsync({
        id,
        monthlyCap: Number(editingCap),
        effectiveFrom: currentMonth(),
      })
      cancelEditCap()
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setSavingCap(false)
    }
  }

  async function handleStop() {
    if (stopTarget === null) return
    setError(null)
    setStopping(true)
    try {
      await stopMutation.mutateAsync({ id: stopTarget.id, effectiveFrom: currentMonth() })
      setStopTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
      setStopTarget(null)
    } finally {
      setStopping(false)
    }
  }

  // One load state for the table plus the lookup list behind its name column: rows show only once
  // every name can be resolved. Retry clears the stale banner and reloads whatever failed.
  const tableState = combineLoadState(categoriesState, budgetsState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  return (
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Budgets
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Monthly spending caps per expense category. Editing a cap (or adding a new budget) always
        takes effect from the current month forward - prior months keep showing whatever cap was
        actually in effect then.
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Category</TableCell>
                <TableCell align="right">Current cap</TableCell>
                <TableCell>Effective from</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={tableState} onRetry={retry} columns={4} actionsColumn>
              {budgets?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} align="center">
                    <Typography color="text.secondary">No budgets yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {budgets?.map((budget) => (
                <TableRow key={budget.id}>
                  <TableCell>{categoryName(budget.categoryId)}</TableCell>
                  <TableCell align="right">
                    {editingId === budget.id ? (
                      <TextField
                        size="small"
                        type="number"
                        label="Monthly cap"
                        value={editingCap}
                        onChange={(e) => setEditingCap(e.target.value)}
                        slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
                        autoFocus
                      />
                    ) : budget.stopped ? (
                      <Typography color="text.secondary" component="span">
                        Stopped
                      </Typography>
                    ) : budget.currentCap !== null ? (
                      budget.currentCap.toFixed(2)
                    ) : (
                      <Typography color="text.secondary" component="span">
                        No cap yet
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{budget.currentCapEffectiveFrom ?? '—'}</TableCell>
                  <TableCell align="right">
                    <InlineEditActions
                      editing={editingId === budget.id}
                      onEdit={() => startEditCap(budget)}
                      onSave={() => void saveEditCap(budget.id)}
                      onCancel={cancelEditCap}
                      editLabel={budget.stopped ? 'Resume budget' : 'Edit cap'}
                      saveLabel={budget.stopped ? 'Resume with this cap' : 'Save cap'}
                      saving={savingCap}
                    />
                    {!budget.stopped && editingId !== budget.id && (
                      <IconButton
                        size="small"
                        aria-label="Stop budget"
                        onClick={() => setStopTarget(budget)}
                      >
                        <StopCircleOutlinedIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

      <ConfirmDialog
        open={stopTarget !== null}
        title={`Stop budgeting ${stopTarget ? categoryName(stopTarget.categoryId) : ''}?`}
        body={
          <>
            Budgeting stops from the current month: this category no longer appears in the
            budget-vs-actual report from now on. Past months keep the cap they had. You can resume
            it later from the row&apos;s &quot;Resume budget&quot; action; the months in between
            stay without a budget.
          </>
        }
        confirmLabel="Stop budget"
        loading={stopping}
        onConfirm={() => void handleStop()}
        onCancel={() => setStopTarget(null)}
      />

      <Paper variant="outlined" sx={{ p: 2, mb: 4, maxWidth: 560 }}>
        <Typography variant="subtitle1" gutterBottom>
          Add budget
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <Select
            size="small"
            displayEmpty
            value={newCategoryId}
            onChange={(e) => setNewCategoryId(e.target.value)}
            aria-label="Category"
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="" disabled>
              Category
            </MenuItem>
            {unbudgetedExpenseCategories.map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {c.name}
              </MenuItem>
            ))}
          </Select>
          <TextField
            label="Monthly cap"
            type="number"
            size="small"
            value={newCap}
            onChange={(e) => setNewCap(e.target.value)}
            slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
          />
          <Button
            variant="contained"
            disabled={adding || !newCategoryId || Number(newCap) <= 0}
            onClick={() => void handleAdd()}
          >
            Add
          </Button>
        </Box>
        {unbudgetedExpenseCategories.length === 0 && categories !== null && (
          <Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>
            Every expense category already has a budget.
          </Typography>
        )}
      </Paper>

      <Typography variant="h5" component="h2" gutterBottom>
        Budget vs. actual
      </Typography>
      <Box sx={{ mb: 2 }}>
        <TextField
          label="Month"
          type="month"
          size="small"
          value={reportMonth}
          onChange={(e) => setReportMonth(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Box>

      <BudgetVsActualReport month={reportMonth} />
    </Box>
  )
}
