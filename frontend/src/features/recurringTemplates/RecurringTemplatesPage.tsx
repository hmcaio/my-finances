import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
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
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { translateSeedName } from '../../i18n/seedNames'
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

  const [createForm, setCreateForm] = useState(EMPTY_CREATE_FORM)
  const [creating, setCreating] = useState(false)
  const [addDialogOpen, setAddDialogOpen] = useState(false)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editAmount, setEditAmount] = useState('')
  const [editDayOfMonth, setEditDayOfMonth] = useState('')
  const [savingCap, setSavingCap] = useState(false)

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
    {
      key: 'description',
      header: t('recurringTemplates:columns.description'),
      render: (template) => template.description,
    },
    {
      key: 'category',
      header: t('recurringTemplates:columns.category'),
      tabletPriority: 'low',
      render: (template) => categoryName(template.categoryId),
    },
    {
      key: 'account',
      header: t('recurringTemplates:columns.account'),
      render: (template) => accountName(template.accountId),
    },
    {
      key: 'amount',
      header: t('recurringTemplates:columns.amount'),
      align: 'right',
      render: (template) =>
        editingId === template.id && !isMobile ? (
          <RecurringTemplateAmountField
            value={editAmount}
            onChange={setEditAmount}
            autoFocus
            sx={{ width: 110 }}
          />
        ) : template.currentAmount !== null ? (
          formatMoney(template.currentAmount, locale)
        ) : (
          <Typography color="text.secondary" component="span">
            {t('recurringTemplates:noCapYet')}
          </Typography>
        ),
    },
    {
      key: 'dayOfMonth',
      header: t('recurringTemplates:columns.dayOfMonth'),
      align: 'right',
      render: (template) =>
        editingId === template.id && !isMobile ? (
          <RecurringTemplateDayOfMonthField
            value={editDayOfMonth}
            onChange={setEditDayOfMonth}
            sx={{ width: 80 }}
          />
        ) : (
          (template.currentDayOfMonth ?? '—')
        ),
    },
    {
      key: 'status',
      header: t('recurringTemplates:columns.status'),
      render: (template) => (
        <Chip
          size="small"
          label={
            template.active
              ? t('recurringTemplates:statusActive')
              : t('recurringTemplates:statusStopped')
          }
          color={template.active ? 'success' : 'default'}
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
          editLabel={t('recurringTemplates:editAmountDayAction')}
          saveLabel={t('recurringTemplates:saveCapAction')}
          saving={savingCap}
        />
        {!editingInline && (
          <IconButton
            size="small"
            aria-label={
              template.active
                ? t('recurringTemplates:stopAria')
                : t('recurringTemplates:reactivateAria')
            }
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
            label={
              template.active
                ? t('recurringTemplates:statusActive')
                : t('recurringTemplates:statusStopped')
            }
            color={template.active ? 'success' : 'default'}
          />
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {categoryName(template.categoryId)} · {accountName(template.accountId)}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {template.currentAmount !== null
            ? formatMoney(template.currentAmount, locale)
            : t('recurringTemplates:noCapYet')}{' '}
          · {t('recurringTemplates:dayPrefix')} {template.currentDayOfMonth ?? '—'}
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
          {t('recurringTemplates:title')}
        </Typography>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          {t('recurringTemplates:addButton')}
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('recurringTemplates:description')}
      </Typography>

      {/* While a dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert
        message={addDialogOpen || editDialogOpen ? null : error}
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
          renderCard={renderCard}
          emptyMessage={t('recurringTemplates:emptyList')}
        />
      </Box>

      <ResponsiveDialog open={addDialogOpen} onClose={closeAddDialog}>
        <DialogTitle>{t('recurringTemplates:addDialogTitle')}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <Select
                size="small"
                displayEmpty
                value={createForm.categoryId}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, categoryId: e.target.value }))}
                aria-label={t('recurringTemplates:categoryLabel')}
              >
                <MenuItem value="" disabled>
                  {t('recurringTemplates:categoryLabel')}
                </MenuItem>
                {templateCategories.map((c) => (
                  <MenuItem key={c.id} value={c.id}>
                    {translateSeedName('categories', c.name, t)}
                  </MenuItem>
                ))}
              </Select>
              <Select
                size="small"
                displayEmpty
                value={createForm.accountId}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, accountId: e.target.value }))}
                aria-label={t('recurringTemplates:accountLabel')}
              >
                <MenuItem value="" disabled>
                  {t('recurringTemplates:accountLabel')}
                </MenuItem>
                {templateAccounts.map((a) => (
                  <MenuItem key={a.id} value={a.id}>
                    {a.name}
                  </MenuItem>
                ))}
              </Select>
              <TextField
                label={t('recurringTemplates:descriptionLabel')}
                size="small"
                value={createForm.description}
                onChange={(e) =>
                  setCreateForm((prev) => ({ ...prev, description: e.target.value }))
                }
                slotProps={{ htmlInput: { maxLength: 150 } }}
                sx={{ gridColumn: '1 / -1' }}
              />
              <TextField
                label={t('recurringTemplates:amountLabel')}
                type="number"
                size="small"
                value={createForm.amount}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, amount: e.target.value }))}
                slotProps={{ htmlInput: { step: '0.01', min: '0.01' } }}
              />
              <TextField
                label={t('recurringTemplates:dayOfMonthLabel')}
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
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={creating || !isCreateFormValid()}
            onClick={() => void handleCreate()}
          >
            {t('common:actions.add')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ResponsiveDialog open={editDialogOpen} onClose={savingCap ? undefined : cancelEditCap}>
        <DialogTitle>{t('recurringTemplates:editDialogTitle')}</DialogTitle>
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
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={savingCap || !isRecurringTemplateEditValid(editAmount, editDayOfMonth)}
            onClick={() => editingId && void saveEditCap(editingId)}
          >
            {t('recurringTemplates:saveCapAction')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <PendingOccurrencesWidget />
    </Box>
  )
}
