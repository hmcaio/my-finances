import { useTranslation } from 'react-i18next'
import { Box, IconButton } from '@mui/material'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import { shiftMonth } from '../../utils/localDate'
import { MonthPicker } from './MonthPicker'

interface MonthNavigatorProps {
  /** The shown month, `YYYY-MM`. */
  value: string
  onChange: (month: string) => void
  /** The picker's label; defaults to the translated "Month". */
  label?: string
}

/**
 * `‹ month ›` (F037): previous and next buttons around the shared {@link MonthPicker}, whose native
 * month field shows the month in the browser's locale and opens the native chooser on click.
 * Unclamped: a future month is a valid place to look (recurring items, planned entries).
 */
export function MonthNavigator({ value, onChange, label }: MonthNavigatorProps) {
  const { t } = useTranslation()
  return (
    <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
      <IconButton
        size="small"
        aria-label={t('monthNavigator.previous')}
        onClick={() => onChange(shiftMonth(value, -1))}
      >
        <ChevronLeftIcon />
      </IconButton>
      <MonthPicker
        label={label ?? t('monthNavigator.label')}
        value={value}
        onChange={(month) => {
          // The native field emits '' while it is being cleared or edited into an invalid state.
          if (month) onChange(month)
        }}
      />
      <IconButton
        size="small"
        aria-label={t('monthNavigator.next')}
        onClick={() => onChange(shiftMonth(value, 1))}
      >
        <ChevronRightIcon />
      </IconButton>
    </Box>
  )
}
