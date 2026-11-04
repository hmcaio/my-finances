import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
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
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { AddFormDialog } from '../../components/feedback/AddFormDialog'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { ResponsiveTable } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useAction } from '../../hooks/useAction'
import { useAddDialog } from '../../hooks/useAddDialog'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { useInlineEdit } from '../../hooks/useInlineEdit'
import { useLocale } from '../../hooks/locale/useLocale'
import { translateSeedName } from '../../i18n/seedNames'
import { currentMonth } from '../../utils/localDate'
import { nameLookup } from '../../utils/nameLookup'
import { PendingOccurrencesWidget } from './PendingOccurrencesWidget'
import { RecurringTemplateCreateFields } from './RecurringTemplateCreateFields'
import { RecurringTemplateEditFields } from './RecurringTemplateEditFields'
import {
  buildRecurringTemplateColumns,
  renderRecurringTemplateCard,
} from './recurringTemplateColumns'
import { RecurringTemplateRowActions } from './RecurringTemplateRowActions'
import {
  EMPTY_RECURRING_TEMPLATE_CREATE_FORM,
  isRecurringTemplateEditValid,
} from './recurringTemplateEdit'
import { PageContainer } from '../../components/layout/PageContainer'

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
  const { t } = useTranslation(['recurringTemplates', 'common'])
  const { locale } = useLocale()
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

  const addDialog = useAddDialog(EMPTY_RECURRING_TEMPLATE_CREATE_FORM)
  const createForm = addDialog.draft
  const setCreateForm = addDialog.setDraft
  const createAction = useAction(setError)

  const edit = useInlineEdit({ amount: '', dayOfMonth: '' })
  const { editingId, cancel: cancelEditCap } = edit
  const editAmount = edit.draft.amount
  const editDayOfMonth = edit.draft.dayOfMonth
  const capAction = useAction(setError)

  const toggleAction = useAction(setError)
  const [togglingId, setTogglingId] = useState<string | null>(null)

  const categoryName = useMemo(
    () => nameLookup(categories ?? [], (c) => translateSeedName('categories', c.name, t)),
    [categories, t],
  )
  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])
  // An investment account takes no recurring templates (money moves through transfers, F008), so
  // the form never offers one.
  const templateAccounts = useMemo(
    () => (accounts ?? []).filter((a) => a.type !== 'INVESTMENT'),
    [accounts],
  )
  // The fuel category takes no recurring templates either (a fill-up's per-occurrence details -
  // vehicle, liters, price, odometer - have nowhere to live on a template, F024/issue #92), so the
  // form never offers it.
  const templateCategories = useMemo(
    () => (categories ?? []).filter((c) => !c.fuelCategory),
    [categories],
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
    addDialog.close()
    setError(null)
  }

  async function handleCreate() {
    if (!isCreateFormValid()) return
    const created = await createAction.run(() =>
      createMutation.mutateAsync({
        categoryId: createForm.categoryId,
        accountId: createForm.accountId,
        description: createForm.description.trim(),
        amount: Number(createForm.amount),
        dayOfMonth: Number(createForm.dayOfMonth),
        effectiveFrom: currentMonth(),
      }),
    )
    if (created) addDialog.close()
  }

  function startEditCap(template: RecurringTemplate) {
    edit.start(template.id, {
      amount: template.currentAmount !== null ? String(template.currentAmount) : '',
      dayOfMonth: template.currentDayOfMonth !== null ? String(template.currentDayOfMonth) : '',
    })
  }

  async function saveEditCap(id: string) {
    if (!isRecurringTemplateEditValid(editAmount, editDayOfMonth)) return
    const saved = await capAction.run(() =>
      capMutation.mutateAsync({
        id,
        amount: Number(editAmount),
        dayOfMonth: Number(editDayOfMonth),
        effectiveFrom: currentMonth(),
      }),
    )
    if (saved) cancelEditCap()
  }

  async function toggleActive(template: RecurringTemplate) {
    setTogglingId(template.id)
    await toggleAction.run(() =>
      template.active
        ? stopMutation.mutateAsync(template.id)
        : reactivateMutation.mutateAsync(template.id),
    )
    setTogglingId(null)
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

  const display = { t, locale, categoryName, accountName }
  const columns = buildRecurringTemplateColumns({
    ...display,
    inlineEdit:
      editingId !== null && !isMobile
        ? {
            rowId: editingId,
            amount: editAmount,
            dayOfMonth: editDayOfMonth,
            onAmountChange: (amount) => edit.setDraft((d) => ({ ...d, amount })),
            onDayOfMonthChange: (dayOfMonth) => edit.setDraft((d) => ({ ...d, dayOfMonth })),
          }
        : null,
  })

  function rowActions(template: RecurringTemplate) {
    return (
      <RecurringTemplateRowActions
        template={template}
        editingInline={editingId === template.id && !isMobile}
        saving={capAction.pending}
        toggling={togglingId === template.id}
        onStartEdit={startEditCap}
        onSave={(id) => void saveEditCap(id)}
        onCancel={cancelEditCap}
        onToggle={(row) => void toggleActive(row)}
      />
    )
  }

  return (
    <PageContainer>
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
          {t('recurringTemplates:title')}
        </Typography>
        <Button variant="contained" onClick={addDialog.show}>
          {t('recurringTemplates:addButton')}
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('recurringTemplates:description')}
      </Typography>

      {/* While a dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert
        message={addDialog.isOpen || editDialogOpen ? null : error}
        onDismiss={() => setError(null)}
      />

      <Box sx={{ mb: 3 }}>
        <ResponsiveTable
          aria-label={t('recurringTemplates:aria')}
          columns={columns}
          rows={templates}
          getRowKey={(template) => template.id}
          state={tableState}
          onRetry={retry}
          actions={rowActions}
          renderCard={(template, actions) =>
            renderRecurringTemplateCard(display, template, actions)
          }
          emptyMessage={t('recurringTemplates:emptyList')}
        />
      </Box>

      <AddFormDialog
        open={addDialog.isOpen}
        title={t('recurringTemplates:addDialogTitle')}
        error={error}
        onDismissError={() => setError(null)}
        busy={createAction.pending}
        canSubmit={isCreateFormValid()}
        onSubmit={() => void handleCreate()}
        onCancel={closeAddDialog}
      >
        <RecurringTemplateCreateFields
          form={createForm}
          setForm={setCreateForm}
          categories={templateCategories}
          accounts={templateAccounts}
        />
      </AddFormDialog>

      <ResponsiveDialog
        open={editDialogOpen}
        onClose={capAction.pending ? undefined : cancelEditCap}
      >
        <DialogTitle>{t('recurringTemplates:editDialogTitle')}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid columns={1}>
              <RecurringTemplateEditFields
                amount={editAmount}
                dayOfMonth={editDayOfMonth}
                onAmountChange={(amount) => edit.setDraft((d) => ({ ...d, amount }))}
                onDayOfMonthChange={(dayOfMonth) => edit.setDraft((d) => ({ ...d, dayOfMonth }))}
              />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={cancelEditCap} disabled={capAction.pending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={
              capAction.pending || !isRecurringTemplateEditValid(editAmount, editDayOfMonth)
            }
            onClick={() => editingId && void saveEditCap(editingId)}
          >
            {t('recurringTemplates:saveCapAction')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <PendingOccurrencesWidget />
    </PageContainer>
  )
}
