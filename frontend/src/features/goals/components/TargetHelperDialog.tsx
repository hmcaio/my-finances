import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  DialogActions,
  DialogTitle,
  Skeleton,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import { useEmergencyFundSuggestion } from '../../../api/goals/goalsQueries'
import { FormDialogContent } from '../../../components/feedback/FormDialogContent'
import { ResponsiveDialog } from '../../../components/feedback/ResponsiveDialog'
import { useDelayedFlag } from '../../../hooks/useDelayedFlag'
import { formatMoney } from '../../../i18n/format'
import { useLocale } from '../../../providers/locale/useLocale'
import { retirementTarget } from '../lib/retirementTarget'

type Mode = 'emergencyFund' | 'retirement'

const MIN_MONTHS = 1
const MAX_MONTHS = 120

export interface TargetHelperDialogProps {
  open: boolean
  /** Receives the suggested target; the caller fills its field and closes the dialog. */
  onApply: (target: number) => void
  onClose: () => void
}

/**
 * "Suggest a target" (PRD S6.17, F039): two helpers that only compute a number to put in the
 * target field, and store nothing. *Emergency fund* = N months (default 6) x the average monthly
 * expense of the last 12 full months, read from the backend; *retirement* = desired monthly income
 * x 12 / the withdrawal rate (default 4% a year), plain arithmetic done here.
 */
export function TargetHelperDialog({ open, onApply, onClose }: TargetHelperDialogProps) {
  const { t } = useTranslation(['goals', 'common'])
  const { locale } = useLocale()
  const [mode, setMode] = useState<Mode>('emergencyFund')
  const [monthsText, setMonthsText] = useState('6')
  const [incomeText, setIncomeText] = useState('')
  const [rateText, setRateText] = useState('4')

  const months = Number(monthsText)
  const monthsValid =
    monthsText !== '' && Number.isInteger(months) && months >= MIN_MONTHS && months <= MAX_MONTHS
  const suggestionQuery = useEmergencyFundSuggestion(
    monthsValid ? months : MIN_MONTHS,
    open && mode === 'emergencyFund' && monthsValid,
  )
  const suggestion = monthsValid ? suggestionQuery.data : undefined
  const showSkeleton = useDelayedFlag(monthsValid && suggestionQuery.isPending && open)

  const retirement =
    incomeText === '' || rateText === ''
      ? null
      : retirementTarget(Number(incomeText), Number(rateText))

  const emergencyTarget =
    suggestion && suggestion.monthsAveraged > 0 ? suggestion.suggestedTarget : null
  const target = mode === 'emergencyFund' ? emergencyTarget : retirement

  return (
    <ResponsiveDialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t('goals:targetHelper.title')}</DialogTitle>
      <FormDialogContent>
        <ToggleButtonGroup
          exclusive
          fullWidth
          size="small"
          value={mode}
          aria-label={t('goals:targetHelper.mode')}
          onChange={(_event, next: Mode | null) => {
            if (next) setMode(next)
          }}
          sx={{ mb: 2 }}
        >
          <ToggleButton value="emergencyFund">{t('goals:targetHelper.emergencyFund')}</ToggleButton>
          <ToggleButton value="retirement">{t('goals:targetHelper.retirement')}</ToggleButton>
        </ToggleButtonGroup>

        {mode === 'emergencyFund' ? (
          <Box sx={{ display: 'grid', gap: 2 }}>
            <TextField
              label={t('goals:targetHelper.months')}
              type="number"
              value={monthsText}
              onChange={(e) => setMonthsText(e.target.value)}
              slotProps={{ htmlInput: { min: MIN_MONTHS, max: MAX_MONTHS, step: 1 } }}
            />
            {showSkeleton && <Skeleton variant="text" width="70%" />}
            {monthsValid && suggestionQuery.isError && (
              <Typography color="error">{t('goals:targetHelper.loadFailed')}</Typography>
            )}
            {suggestion && suggestion.monthsAveraged === 0 && (
              <Typography color="text.secondary">{t('goals:targetHelper.noExpenses')}</Typography>
            )}
            {suggestion && suggestion.monthsAveraged > 0 && (
              <>
                <Typography color="text.secondary">
                  {t('goals:targetHelper.averageExpense', {
                    months: suggestion.monthsAveraged,
                    amount: formatMoney(suggestion.averageMonthlyExpense, locale),
                  })}
                </Typography>
                <Typography variant="h6">
                  {t('goals:targetHelper.result', {
                    amount: formatMoney(suggestion.suggestedTarget, locale),
                  })}
                </Typography>
              </>
            )}
          </Box>
        ) : (
          <Box sx={{ display: 'grid', gap: 2 }}>
            <TextField
              label={t('goals:targetHelper.monthlyIncome')}
              type="number"
              value={incomeText}
              onChange={(e) => setIncomeText(e.target.value)}
              slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
            />
            <TextField
              label={t('goals:targetHelper.withdrawalRate')}
              type="number"
              value={rateText}
              onChange={(e) => setRateText(e.target.value)}
              slotProps={{ htmlInput: { min: 0, step: '0.1' } }}
            />
            {retirement !== null && (
              <Typography variant="h6">
                {t('goals:targetHelper.result', { amount: formatMoney(retirement, locale) })}
              </Typography>
            )}
          </Box>
        )}
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
          {t('goals:targetHelper.hint')}
        </Typography>
      </FormDialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common:actions.cancel')}</Button>
        <Button
          variant="contained"
          disabled={target === null}
          onClick={() => target !== null && onApply(target)}
        >
          {t('goals:targetHelper.apply')}
        </Button>
      </DialogActions>
    </ResponsiveDialog>
  )
}
