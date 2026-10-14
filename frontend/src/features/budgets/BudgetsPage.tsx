import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Select,
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
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { MonthPicker } from '../../components/inputs/MonthPicker'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { currentMonth } from '../../utils/localDate'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'
import { BudgetCapField } from './BudgetEditFields'
import { isBudgetCapValid } from './budgetEdit'
import { BudgetVsActualReport } from './BudgetVsActualReport'

/**
 * Budgets screen (F006 spec): a settings-style list of budgeted categories with their current cap
 * plus an add-budget dialog, and a budget-vs-actual view with a month picker and an over-cap visual
 * indicator (bottom half). Editing a cap or adding a new budget always takes effect from the
 * current month forward (PRD S5.6's "effective going forward only") - there's no effective-month
 * picker on either form, matching plan.md's "which month it takes effect from is implicit -
 * now/current month forward". A row can be stopped (issue #61, confirmed through a dialog): it then
 * reads "Stopped" and its edit action becomes "Resume budget" (a normal cap edit); the add-budget
 * picker still excludes it, since the category already has its budget row.
 *
 * Responsive (F021): the header's Add button opens the add-budget fields in a `ResponsiveDialog` at
 * every size (there is no form panel below the table). The cap edit stays inline in the row on
 * tablet/desktop; on mobile the card's Edit opens the same field (`BudgetCapField`) in a full-screen
 * dialog. The budget-vs-actual report below is not table-shaped (progress bars, not rows/columns),
 * so it keeps its own single-column layout and `LoadFailedNotice` rather than migrating to
 * `ResponsiveTable`.
 */
export function BudgetsPage() {
  const { t } = useTranslation(['budgets', 'common'])
  const { locale } = useLocale()
  const [error, setError] = useState<string | null>(null)
  const isMobile = useIsMobile()
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
  const [addDialogOpen, setAddDialogOpen] = useState(false)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingCap, setEditingCap] = useState('')
  const [savingCap, setSavingCap] = useState(false)

  const [stopTarget, setStopTarget] = useState<Budget | null>(null)
  const [stopping, setStopping] = useState(false)

  const [reportMonth, setReportMonth] = useState(currentMonth())

  const categoryName = useMemo(
    () => nameLookup(categories ?? [], (c) => translateSeedName('categories', c.name, t)),
    [categories, t],
  )

  const unbudgetedExpenseCategories = useMemo(() => {
    const budgetedCategoryIds = new Set((budgets ?? []).map((b) => b.categoryId))
    return (categories ?? []).filter((c) => c.type === 'EXPENSE' && !budgetedCategoryIds.has(c.id))
  }, [categories, budgets])

  function closeAddDialog() {
    setAddDialogOpen(false)
    setError(null)
  }

  async function handleAdd() {
    if (!newCategoryId || !isBudgetCapValid(newCap)) return
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
      setAddDialogOpen(false)
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
    if (!isBudgetCapValid(editingCap)) return
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

  // On mobile the edit happens in a dialog opened from the card, so the card stays plain text.
  const editDialogOpen = isMobile && editingId !== null
  const editingBudget = budgets?.find((b) => b.id === editingId) ?? null

  const columns: ResponsiveColumn<Budget>[] = [
    {
      key: 'category',
      header: t('budgets:columns.category'),
      role: 'primary',
      render: (budget) => categoryName(budget.categoryId),
    },
    {
      key: 'cap',
      header: t('budgets:columns.currentCap'),
      align: 'right',
      render: (budget) =>
        editingId === budget.id && !isMobile ? (
          <BudgetCapField value={editingCap} onChange={setEditingCap} autoFocus />
        ) : budget.stopped ? (
          <Typography color="text.secondary" component="span">
            {t('budgets:stopped')}
          </Typography>
        ) : budget.currentCap !== null ? (
          formatMoney(budget.currentCap, locale)
        ) : (
          <Typography color="text.secondary" component="span">
            {t('budgets:noCapYet')}
          </Typography>
        ),
    },
    {
      key: 'effectiveFrom',
      header: t('budgets:columns.effectiveFrom'),
      render: (budget) => budget.currentCapEffectiveFrom ?? '—',
    },
  ]

  function rowActions(budget: Budget) {
    const editingInline = editingId === budget.id && !isMobile
    return (
      <>
        <InlineEditActions
          editing={editingInline}
          onEdit={() => startEditCap(budget)}
          onSave={() => void saveEditCap(budget.id)}
          onCancel={cancelEditCap}
          editLabel={budget.stopped ? t('budgets:resumeBudgetAction') : t('budgets:editCapAction')}
          saveLabel={budget.stopped ? t('budgets:resumeWithCapAction') : t('budgets:saveCapAction')}
          saving={savingCap}
        />
        {!budget.stopped && !editingInline && (
          <IconButton
            size="small"
            aria-label={t('budgets:stopBudgetAria')}
            onClick={() => setStopTarget(budget)}
          >
            <StopCircleOutlinedIcon fontSize="small" />
          </IconButton>
        )}
      </>
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
          {t('budgets:title')}
        </Typography>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          {t('budgets:addButton')}
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('budgets:description')}
      </Typography>

      {/* While a dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert
        message={addDialogOpen || editDialogOpen ? null : error}
        onDismiss={() => setError(null)}
      />

      <Box sx={{ mb: 3 }}>
        <ResponsiveTable
          aria-label={t('budgets:title')}
          columns={columns}
          rows={budgets}
          getRowKey={(budget) => budget.id}
          state={tableState}
          onRetry={retry}
          actions={rowActions}
          emptyMessage={t('budgets:emptyList')}
        />
      </Box>

      <ResponsiveDialog open={addDialogOpen} onClose={closeAddDialog}>
        <DialogTitle>{t('budgets:addDialogTitle')}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <Select
                size="small"
                displayEmpty
                value={newCategoryId}
                onChange={(e) => setNewCategoryId(e.target.value)}
                aria-label={t('budgets:categoryLabel')}
              >
                <MenuItem value="" disabled>
                  {t('budgets:categoryLabel')}
                </MenuItem>
                {unbudgetedExpenseCategories.map((c) => (
                  <MenuItem key={c.id} value={c.id}>
                    {translateSeedName('categories', c.name, t)}
                  </MenuItem>
                ))}
              </Select>
              <TextField
                label={t('budgets:monthlyCapLabel')}
                type="number"
                size="small"
                value={newCap}
                onChange={(e) => setNewCap(e.target.value)}
                slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
              />
            </FormGrid>
            {unbudgetedExpenseCategories.length === 0 && categories !== undefined && (
              <Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>
                {t('budgets:everyExpenseBudgeted')}
              </Typography>
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeAddDialog} disabled={adding}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={adding || !newCategoryId || !isBudgetCapValid(newCap)}
            onClick={() => void handleAdd()}
          >
            {t('common:actions.add')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ResponsiveDialog open={editDialogOpen} onClose={savingCap ? undefined : cancelEditCap}>
        <DialogTitle>
          {editingBudget?.stopped
            ? t('budgets:resumeBudgetAction')
            : t('budgets:editBudgetCapTitle')}
          {editingBudget ? ` — ${categoryName(editingBudget.categoryId)}` : ''}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid columns={1}>
              <BudgetCapField value={editingCap} onChange={setEditingCap} autoFocus />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={cancelEditCap} disabled={savingCap}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={savingCap || !isBudgetCapValid(editingCap)}
            onClick={() => editingId && void saveEditCap(editingId)}
          >
            {editingBudget?.stopped ? t('budgets:resumeWithCapAction') : t('budgets:saveCapAction')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ConfirmDialog
        open={stopTarget !== null}
        title={t('budgets:stop.title', {
          category: stopTarget ? categoryName(stopTarget.categoryId) : '',
        })}
        body={t('budgets:stop.body')}
        confirmLabel={t('budgets:stop.confirmLabel')}
        loading={stopping}
        onConfirm={() => void handleStop()}
        onCancel={() => setStopTarget(null)}
      />

      <Typography variant="h5" component="h2" gutterBottom>
        {t('budgets:reportTitle')}
      </Typography>
      <Box sx={{ mb: 2 }}>
        <MonthPicker
          label={t('budgets:monthLabel')}
          value={reportMonth}
          onChange={setReportMonth}
        />
      </Box>

      <BudgetVsActualReport month={reportMonth} />
    </Box>
  )
}
