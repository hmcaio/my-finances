import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  DialogActions,
  DialogTitle,
  FormControlLabel,
  FormHelperText,
  MenuItem,
  Switch,
  TextField,
} from '@mui/material'
import type { Goal, GoalStatus } from '../../../api/goals/goals'
import type { ContributionSuggestion } from '../../../api/goals/goals'
import {
  useCreateGoal,
  useEditGoal,
  useGoalContributionSuggestion,
} from '../../../api/goals/goalsQueries'
import { ErrorAlert } from '../../../components/feedback/ErrorAlert'
import { FormDialogContent } from '../../../components/feedback/FormDialogContent'
import { FormGrid, ResponsiveDialog } from '../../../components/feedback/ResponsiveDialog'
import { FORM_GRID_FULL_ROW } from '../../../components/layout/layoutSx'
import { useAction } from '../../../hooks/useAction'
import { formatMoney } from '../../../i18n/format'
import { useLocale } from '../../../providers/locale/useLocale'
import { today } from '../../../utils/localDate'
import { hasAtMostTwoDecimals } from '../lib/goalFormat'
import { TargetHelperDialog } from './TargetHelperDialog'

/** Bounds of the free-text inputs, matching the backend (`TextFieldConstraints`). */
const NAME_MAX_LENGTH = 100
const NOTES_MAX_LENGTH = 500

const MIN_RETURN = -50
const MAX_RETURN = 100

const STATUSES: GoalStatus[] = ['ACTIVE', 'ACHIEVED', 'ARCHIVED']

export interface GoalFormProps {
  open: boolean
  /** The goal being edited; omitted to create one. */
  goal?: Goal
  onClose: () => void
}

/**
 * The create / edit dialog of a goal (PRD S6.17, F039): name, notes, target amount (with a
 * "Suggest a target" helper), optional target date, planned monthly contribution, the three real
 * annual returns, the status (edit only) and "Show on dashboard". Editing a goal that has
 * allocations also offers "Suggest from history" beside the planned contribution (F041): it asks
 * for the 12-month average of what was put into the goal's products and fills the field, nothing
 * is saved until Save. Validates what the backend
 * would reject so the button stays disabled until the draft is valid; a save error shows inside
 * the open dialog. An archived goal (or one being archived) has its dashboard switch off and
 * disabled, and restoring does not turn it back on.
 */
export function GoalForm({ open, goal, onClose }: GoalFormProps) {
  return (
    <ResponsiveDialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <GoalFormBody goal={goal} onClose={onClose} />
    </ResponsiveDialog>
  )
}

/** Text of a number the user typed, as a number; `NaN` for an empty or malformed value. */
function parse(text: string): number {
  return text.trim() === '' ? Number.NaN : Number(text)
}

function GoalFormBody({ goal, onClose }: { goal?: Goal; onClose: () => void }) {
  const { t } = useTranslation(['goals', 'common'])
  const { locale } = useLocale()
  const editing = goal !== undefined
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState(goal?.name ?? '')
  const [notes, setNotes] = useState(goal?.notes ?? '')
  const [targetAmount, setTargetAmount] = useState(goal ? String(goal.targetAmount) : '')
  const [targetDate, setTargetDate] = useState(goal?.targetDate ?? '')
  const [contribution, setContribution] = useState(
    goal ? String(goal.plannedMonthlyContribution) : '0',
  )
  const [pessimistic, setPessimistic] = useState(goal ? String(goal.returnPessimistic) : '2')
  const [base, setBase] = useState(goal ? String(goal.returnBase) : '4')
  const [optimistic, setOptimistic] = useState(goal ? String(goal.returnOptimistic) : '6')
  const [status, setStatus] = useState<GoalStatus>(goal?.status ?? 'ACTIVE')
  const [showOnDashboard, setShowOnDashboard] = useState(goal?.showOnDashboard ?? true)
  const [helperOpen, setHelperOpen] = useState(false)
  // What "Suggest from history" last filled in; cleared as soon as the field is edited by hand.
  const [suggestion, setSuggestion] = useState<ContributionSuggestion | null>(null)

  const createMutation = useCreateGoal()
  const editMutation = useEditGoal()
  const saveAction = useAction(setError)
  // Fetched only when the button is pressed (`refetch`), never while the form is open.
  const suggestionQuery = useGoalContributionSuggestion(goal?.id, false)
  const suggestAction = useAction(setError)
  const canSuggest = editing && goal.allocations.length > 0

  async function suggestFromHistory() {
    setSuggestion(null)
    await suggestAction.run(async () => {
      const { data } = await suggestionQuery.refetch({ throwOnError: true })
      if (data === undefined) return
      setContribution(String(data.averageMonthlyContribution))
      setSuggestion(data)
    })
  }

  const target = parse(targetAmount)
  const monthly = parse(contribution)
  const returns = [parse(pessimistic), parse(base), parse(optimistic)]
  const returnsInRange = returns.every((r) => r >= MIN_RETURN && r <= MAX_RETURN)
  const returnsOrdered = returns[0] <= returns[1] && returns[1] <= returns[2]
  const returnsDecimals = [pessimistic, base, optimistic].every(hasAtMostTwoDecimals)
  const dateValid = targetDate === '' || targetDate === goal?.targetDate || targetDate > today()

  const returnsError = !returnsInRange
    ? t('goals:form.returnsOutOfRange')
    : !returnsDecimals
      ? t('goals:form.twoDecimals')
      : !returnsOrdered
        ? t('goals:form.returnsOrder')
        : null
  const valid =
    name.trim() !== '' &&
    target > 0 &&
    hasAtMostTwoDecimals(targetAmount) &&
    monthly >= 0 &&
    hasAtMostTwoDecimals(contribution) &&
    returnsError === null &&
    dateValid

  const archived = status === 'ARCHIVED'

  function changeStatus(next: GoalStatus) {
    setStatus(next)
    if (next === 'ARCHIVED') setShowOnDashboard(false)
  }

  async function save() {
    const fields = {
      name: name.trim(),
      ...(notes.trim() !== '' && { notes: notes.trim() }),
      targetAmount: target,
      ...(targetDate !== '' && { targetDate }),
      plannedMonthlyContribution: monthly,
      returnPessimistic: returns[0],
      returnBase: returns[1],
      returnOptimistic: returns[2],
    }
    const saved = await saveAction.run(() =>
      goal
        ? editMutation.mutateAsync({
            id: goal.id,
            ...fields,
            status,
            showOnDashboard: archived ? false : showOnDashboard,
          })
        : createMutation.mutateAsync({ ...fields, showOnDashboard }),
    )
    if (saved) onClose()
  }

  return (
    <>
      <DialogTitle>{editing ? t('goals:form.editTitle') : t('goals:form.createTitle')}</DialogTitle>
      <FormDialogContent>
        <ErrorAlert message={error} onDismiss={() => setError(null)} />
        <FormGrid>
          <TextField
            label={t('goals:form.name')}
            value={name}
            onChange={(e) => setName(e.target.value)}
            slotProps={{ htmlInput: { maxLength: NAME_MAX_LENGTH } }}
            sx={FORM_GRID_FULL_ROW}
            autoFocus
          />
          <TextField
            label={t('goals:form.notes')}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            slotProps={{ htmlInput: { maxLength: NOTES_MAX_LENGTH } }}
            multiline
            minRows={2}
            sx={FORM_GRID_FULL_ROW}
          />
          <Box sx={{ ...FORM_GRID_FULL_ROW, display: 'flex', gap: 1, alignItems: 'flex-start' }}>
            <TextField
              label={t('goals:form.targetAmount')}
              type="number"
              value={targetAmount}
              onChange={(e) => setTargetAmount(e.target.value)}
              slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
              error={targetAmount !== '' && !hasAtMostTwoDecimals(targetAmount)}
              helperText={
                targetAmount !== '' && !hasAtMostTwoDecimals(targetAmount)
                  ? t('goals:form.twoDecimals')
                  : undefined
              }
              sx={{ flex: 1 }}
            />
            <Button sx={{ mt: 1, whiteSpace: 'normal' }} onClick={() => setHelperOpen(true)}>
              {t('goals:form.suggestTarget')}
            </Button>
          </Box>
          <TextField
            label={t('goals:form.targetDate')}
            type="date"
            value={targetDate}
            onChange={(e) => setTargetDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            error={!dateValid}
            helperText={dateValid ? undefined : t('goals:form.dateMustBeFuture')}
          />
          <Box sx={{ ...FORM_GRID_FULL_ROW, display: 'flex', gap: 1, alignItems: 'flex-start' }}>
            <TextField
              label={t('goals:form.plannedMonthlyContribution')}
              type="number"
              value={contribution}
              onChange={(e) => {
                setContribution(e.target.value)
                setSuggestion(null)
              }}
              slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
              error={contribution !== '' && !(monthly >= 0 && hasAtMostTwoDecimals(contribution))}
              helperText={
                suggestion === null
                  ? canSuggest
                    ? t('goals:contribution.hint')
                    : undefined
                  : suggestion.monthsAveraged === 0
                    ? t('goals:contribution.none')
                    : t('goals:contribution.average', {
                        count: suggestion.monthsAveraged,
                        amount: formatMoney(suggestion.averageMonthlyContribution, locale),
                      })
              }
              sx={{ flex: 1 }}
            />
            {canSuggest && (
              <Button
                sx={{ mt: 1, whiteSpace: 'normal' }}
                disabled={suggestAction.pending}
                onClick={() => void suggestFromHistory()}
              >
                {t('goals:contribution.suggest')}
              </Button>
            )}
          </Box>
          <TextField
            label={t('goals:form.returnPessimistic')}
            type="number"
            value={pessimistic}
            onChange={(e) => setPessimistic(e.target.value)}
            slotProps={{ htmlInput: { min: MIN_RETURN, max: MAX_RETURN, step: '0.01' } }}
            error={returnsError !== null}
          />
          <TextField
            label={t('goals:form.returnBase')}
            type="number"
            value={base}
            onChange={(e) => setBase(e.target.value)}
            slotProps={{ htmlInput: { min: MIN_RETURN, max: MAX_RETURN, step: '0.01' } }}
            error={returnsError !== null}
          />
          <TextField
            label={t('goals:form.returnOptimistic')}
            type="number"
            value={optimistic}
            onChange={(e) => setOptimistic(e.target.value)}
            slotProps={{ htmlInput: { min: MIN_RETURN, max: MAX_RETURN, step: '0.01' } }}
            error={returnsError !== null}
          />
          <FormHelperText error={returnsError !== null} sx={FORM_GRID_FULL_ROW}>
            {returnsError ?? t('goals:form.returnsHint')}
          </FormHelperText>
          {editing && (
            <TextField
              select
              label={t('goals:form.status')}
              value={status}
              onChange={(e) => changeStatus(e.target.value as GoalStatus)}
            >
              {STATUSES.map((value) => (
                <MenuItem key={value} value={value}>
                  {t(`goals:status.${value}`)}
                </MenuItem>
              ))}
            </TextField>
          )}
          <FormControlLabel
            control={
              <Switch
                checked={showOnDashboard && !archived}
                disabled={archived}
                onChange={(e) => setShowOnDashboard(e.target.checked)}
              />
            }
            label={t('goals:form.showOnDashboard')}
          />
        </FormGrid>
      </FormDialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saveAction.pending}>
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          disabled={saveAction.pending || !valid}
          onClick={() => void save()}
        >
          {editing ? t('goals:form.save') : t('goals:form.create')}
        </Button>
      </DialogActions>
      <TargetHelperDialog
        open={helperOpen}
        onClose={() => setHelperOpen(false)}
        onApply={(suggested) => {
          setTargetAmount(String(suggested))
          setHelperOpen(false)
        }}
      />
    </>
  )
}
