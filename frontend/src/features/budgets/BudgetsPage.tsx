import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material'
import { useCategories } from '../../api/categories/categoriesQueries'
import type { Budget } from '../../api/budgets/budgets'
import {
  useBudgets,
  useCreateBudget,
  useSetBudgetCap,
  useStopBudget,
} from '../../api/budgets/budgetsQueries'
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { AddFormDialog } from '../../components/feedback/AddFormDialog'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { MonthPicker } from '../../components/inputs/MonthPicker'
import { ResponsiveTable } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useAction } from '../../hooks/useAction'
import { useAddDialog } from '../../hooks/useAddDialog'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { useInlineEdit } from '../../hooks/useInlineEdit'
import { currentMonth } from '../../utils/localDate'
import { useLocale } from '../../hooks/locale/useLocale'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'
import { BudgetCapField } from './BudgetEditFields'
import { buildBudgetColumns } from './budgetColumns'
import { BudgetRowActions } from './BudgetRowActions'
import { isBudgetCapValid } from './budgetEdit'
import { BudgetVsActualReport } from './BudgetVsActualReport'
import { PageContainer } from '../../components/layout/PageContainer'
import { PageHeader } from '../../components/layout/PageHeader'

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

  const addAction = useAction(setError)
  const addDialog = useAddDialog({ categoryId: '', cap: '' })

  const edit = useInlineEdit('')
  const { editingId, draft: editingCap, setDraft: setEditingCap, cancel: cancelEditCap } = edit
  const capAction = useAction(setError)

  const [stopTarget, setStopTarget] = useState<Budget | null>(null)
  const stopAction = useAction(setError)

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
    addDialog.close()
    setError(null)
  }

  async function handleAdd() {
    if (!addDialog.draft.categoryId || !isBudgetCapValid(addDialog.draft.cap)) return
    const added = await addAction.run(() =>
      createMutation.mutateAsync({
        categoryId: addDialog.draft.categoryId,
        monthlyCap: Number(addDialog.draft.cap),
        effectiveFrom: currentMonth(),
      }),
    )
    if (added) addDialog.close()
  }

  function startEditCap(budget: Budget) {
    edit.start(budget.id, budget.currentCap !== null ? String(budget.currentCap) : '')
  }

  async function saveEditCap(id: string) {
    if (!isBudgetCapValid(editingCap)) return
    const saved = await capAction.run(() =>
      capMutation.mutateAsync({
        id,
        monthlyCap: Number(editingCap),
        effectiveFrom: currentMonth(),
      }),
    )
    if (saved) cancelEditCap()
  }

  async function handleStop() {
    if (stopTarget === null) return
    await stopAction.run(() =>
      stopMutation.mutateAsync({ id: stopTarget.id, effectiveFrom: currentMonth() }),
    )
    setStopTarget(null)
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

  const columns = buildBudgetColumns({
    t,
    locale,
    categoryName,
    inlineEdit:
      editingId !== null && !isMobile
        ? { rowId: editingId, cap: editingCap, onCapChange: setEditingCap }
        : null,
  })

  function rowActions(budget: Budget) {
    return (
      <BudgetRowActions
        budget={budget}
        editingInline={editingId === budget.id && !isMobile}
        saving={capAction.pending}
        onStartEdit={startEditCap}
        onSave={(id) => void saveEditCap(id)}
        onCancel={cancelEditCap}
        onStop={setStopTarget}
      />
    )
  }

  return (
    <PageContainer>
      <PageHeader title={t('budgets:title')}>
        <Button variant="contained" onClick={addDialog.show}>
          {t('budgets:addButton')}
        </Button>
      </PageHeader>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('budgets:description')}
      </Typography>

      {/* While a dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert
        message={addDialog.isOpen || editDialogOpen ? null : error}
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

      <AddFormDialog
        open={addDialog.isOpen}
        title={t('budgets:addDialogTitle')}
        error={error}
        onDismissError={() => setError(null)}
        busy={addAction.pending}
        canSubmit={addDialog.draft.categoryId !== '' && isBudgetCapValid(addDialog.draft.cap)}
        onSubmit={() => void handleAdd()}
        onCancel={closeAddDialog}
        afterFields={
          <>
            {unbudgetedExpenseCategories.length === 0 && categories !== undefined && (
              <Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>
                {t('budgets:everyExpenseBudgeted')}
              </Typography>
            )}
          </>
        }
      >
        <Select
          size="small"
          displayEmpty
          value={addDialog.draft.categoryId}
          onChange={(e) => addDialog.setDraft((prev) => ({ ...prev, categoryId: e.target.value }))}
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
          value={addDialog.draft.cap}
          onChange={(e) => addDialog.setDraft((prev) => ({ ...prev, cap: e.target.value }))}
          slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
        />
      </AddFormDialog>

      <ResponsiveDialog
        open={editDialogOpen}
        onClose={capAction.pending ? undefined : cancelEditCap}
      >
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
          <Button onClick={cancelEditCap} disabled={capAction.pending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={capAction.pending || !isBudgetCapValid(editingCap)}
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
        loading={stopAction.pending}
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
    </PageContainer>
  )
}
