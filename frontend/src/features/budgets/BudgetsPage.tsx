import { useMemo, useState } from 'react'
import {
  Box,
  Button,
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
import { getCategories } from '../../api/categories'
import { createBudget, getBudgets, setBudgetCap, type Budget } from '../../api/budgets'
import { defaultErrorMessage } from '../../api/apiError'
import { ErrorAlert } from '../../components/ErrorAlert'
import { InlineEditActions } from '../../components/InlineEditActions'
import { DataTableBody } from '../../components/DataTableBody'
import { combineLoadState, useAsyncData } from '../../hooks/useAsyncData'
import { currentMonth } from '../../utils/localDate'
import { nameLookup } from '../../utils/nameLookup'
import { BudgetVsActualReport } from './BudgetVsActualReport'

/**
 * Budgets screen (F006 spec): a settings-style list of budgeted categories with their current cap
 * plus an add-budget form (top half), and a budget-vs-actual view with a month picker and an
 * over-cap visual indicator (bottom half). Editing a cap or adding a new budget always takes
 * effect from the current month forward (PRD S5.6's "effective going forward only") - there's no
 * effective-month picker on either form, matching plan.md's "which month it takes effect from is
 * implicit - now/current month forward".
 */
export function BudgetsPage() {
  const [error, setError] = useState<string | null>(null)
  const { data: categories, ...categoriesState } = useAsyncData(getCategories, [], {
    onError: setError,
  })
  const {
    data: budgets,
    setData: setBudgets,
    ...budgetsState
  } = useAsyncData(getBudgets, [], { onError: setError })

  const [newCategoryId, setNewCategoryId] = useState('')
  const [newCap, setNewCap] = useState('')
  const [adding, setAdding] = useState(false)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingCap, setEditingCap] = useState('')
  const [savingCap, setSavingCap] = useState(false)

  const [reportMonth, setReportMonth] = useState(currentMonth())
  // Bumped after a budget/cap change so the (self-fetching) report remounts and refetches.
  const [reportReloadKey, setReportReloadKey] = useState(0)

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
      const created = await createBudget({
        categoryId: newCategoryId,
        monthlyCap: Number(newCap),
        effectiveFrom: currentMonth(),
      })
      setBudgets((prev) => [...(prev ?? []), created])
      setNewCategoryId('')
      setNewCap('')
      if (reportMonth >= currentMonth()) setReportReloadKey((k) => k + 1)
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
      const updated = await setBudgetCap(id, {
        monthlyCap: Number(editingCap),
        effectiveFrom: currentMonth(),
      })
      setBudgets((prev) => prev?.map((b) => (b.id === id ? updated : b)) ?? null)
      cancelEditCap()
      if (reportMonth >= currentMonth()) setReportReloadKey((k) => k + 1)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setSavingCap(false)
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
                      editLabel="Edit cap"
                      saveLabel="Save cap"
                      saving={savingCap}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

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

      <BudgetVsActualReport month={reportMonth} reloadKey={reportReloadKey} />
    </Box>
  )
}
