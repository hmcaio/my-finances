import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  IconButton,
  LinearProgress,
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
import EditIcon from '@mui/icons-material/Edit'
import CheckIcon from '@mui/icons-material/Check'
import CloseIcon from '@mui/icons-material/Close'
import { getCategories, type Category } from '../../api/categories'
import {
  createBudget,
  getBudgetReport,
  getBudgets,
  setBudgetCap,
  type Budget,
  type BudgetReportLine,
} from '../../api/budgets'
import { ApiError } from '../../api/apiError'
import { nameLookup } from '../transactions/nameLookup'

/** `YYYY-MM` for the current real-world month - the implicit "now" every cap edit/new budget
 * takes effect from (PRD S5.6: "effective going forward only"). */
function currentMonth(): string {
  return new Date().toISOString().slice(0, 7)
}

/**
 * Budgets screen (F006 spec): a settings-style list of budgeted categories with their current cap
 * plus an add-budget form (top half), and a budget-vs-actual view with a month picker and an
 * over-cap visual indicator (bottom half). Editing a cap or adding a new budget always takes
 * effect from the current month forward (PRD S5.6's "effective going forward only") - there's no
 * effective-month picker on either form, matching plan.md's "which month it takes effect from is
 * implicit - now/current month forward".
 */
export function BudgetsPage() {
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [budgets, setBudgets] = useState<Budget[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [newCategoryId, setNewCategoryId] = useState('')
  const [newCap, setNewCap] = useState('')
  const [adding, setAdding] = useState(false)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingCap, setEditingCap] = useState('')
  const [savingCap, setSavingCap] = useState(false)

  const [reportMonth, setReportMonth] = useState(currentMonth())
  const [report, setReport] = useState<BudgetReportLine[] | null>(null)

  function loadBudgets() {
    getBudgets()
      .then(setBudgets)
      .catch((err: unknown) => setError(errorMessage(err)))
  }

  useEffect(() => {
    getCategories()
      .then(setCategories)
      .catch((err: unknown) => setError(errorMessage(err)))
    loadBudgets()
  }, [])

  useEffect(() => {
    getBudgetReport(reportMonth)
      .then(setReport)
      .catch((err: unknown) => setError(errorMessage(err)))
  }, [reportMonth])

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
    } catch (err) {
      setError(errorMessage(err))
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
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSavingCap(false)
    }
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

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

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
            <TableBody>
              {budgets === null && (
                <TableRow>
                  <TableCell colSpan={4} align="center">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
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
                    {editingId === budget.id ? (
                      <>
                        <IconButton
                          size="small"
                          aria-label="Save cap"
                          disabled={savingCap}
                          onClick={() => void saveEditCap(budget.id)}
                        >
                          <CheckIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          aria-label="Cancel"
                          disabled={savingCap}
                          onClick={cancelEditCap}
                        >
                          <CloseIcon fontSize="small" />
                        </IconButton>
                      </>
                    ) : (
                      <IconButton
                        size="small"
                        aria-label="Edit cap"
                        onClick={() => startEditCap(budget)}
                      >
                        <EditIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
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

      <Paper variant="outlined" sx={{ p: 2 }}>
        {report === null && <Typography color="text.secondary">Loading…</Typography>}
        {report?.length === 0 && (
          <Typography color="text.secondary">No budgeted categories yet.</Typography>
        )}
        {report?.map((line) => {
          const overCap = line.cap !== null && line.actual > line.cap
          const progress =
            line.cap !== null && line.cap > 0 ? Math.min(100, (line.actual / line.cap) * 100) : 0
          return (
            <Box key={line.categoryId} sx={{ mb: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                <Typography variant="body2">{categoryName(line.categoryId)}</Typography>
                <Typography
                  variant="body2"
                  color={overCap ? 'error' : 'text.secondary'}
                  sx={{ fontWeight: overCap ? 'bold' : undefined }}
                >
                  {line.actual.toFixed(2)} / {line.cap !== null ? line.cap.toFixed(2) : 'no cap'}
                  {overCap && ' — over budget'}
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={progress}
                color={overCap ? 'error' : 'primary'}
                aria-label={`${categoryName(line.categoryId)} budget usage`}
              />
            </Box>
          )
        })}
      </Paper>
    </Box>
  )
}

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong.'
}
