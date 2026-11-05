import { useTranslation } from 'react-i18next'
import { TextField } from '@mui/material'
import type { OpeningPositionDraft } from './openingPositionDraft'

interface OpeningPositionFieldsProps {
  draft: OpeningPositionDraft
  onChange: (draft: OpeningPositionDraft) => void
  /** The current-value helper text differs between the create form and the card's dialog. */
  currentValueHelper: string
  /** Hide the current value (kept out of an edit that only changes the position's own fields). */
  showCurrentValue?: boolean
}

/**
 * The four inputs of an opening position (F038): as-of date, cotas, average price and the
 * optional current value. Rendered inside a `FormGrid` by both the create-product form's
 * "Opening position" section and the detail page's `OpeningPositionDialog`, so they share labels
 * and number-input behaviour. Quantity and price accept up to 8 decimals (the backend's scale).
 */
export function OpeningPositionFields({
  draft,
  onChange,
  currentValueHelper,
  showCurrentValue = true,
}: OpeningPositionFieldsProps) {
  const { t } = useTranslation('investmentProducts')
  const set = (field: keyof OpeningPositionDraft) => (value: string) =>
    onChange({ ...draft, [field]: value })

  return (
    <>
      <TextField
        label={t('form.openingAsOfLabel')}
        type="date"
        size="small"
        value={draft.asOfDate}
        onChange={(e) => set('asOfDate')(e.target.value)}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        label={t('form.openingQuantityLabel')}
        type="number"
        size="small"
        value={draft.quantity}
        onChange={(e) => set('quantity')(e.target.value)}
        slotProps={{ htmlInput: { step: 'any', min: 0 } }}
      />
      <TextField
        label={t('form.openingUnitPriceLabel')}
        type="number"
        size="small"
        value={draft.unitPrice}
        onChange={(e) => set('unitPrice')(e.target.value)}
        slotProps={{ htmlInput: { step: 'any', min: 0 } }}
      />
      {showCurrentValue && (
        <TextField
          label={t('form.openingCurrentValueLabel')}
          type="number"
          size="small"
          value={draft.currentValue}
          onChange={(e) => set('currentValue')(e.target.value)}
          helperText={currentValueHelper}
          slotProps={{ htmlInput: { step: '0.01', min: 0 } }}
        />
      )}
    </>
  )
}
