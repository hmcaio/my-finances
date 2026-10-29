import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  DialogActions,
  DialogContent,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import {
  INVESTMENT_NAME_MAX_LENGTH,
  type InvestmentCategory,
} from '../../api/investments/investmentCategories'
import type { OpeningPositionRequest } from '../../api/investments/investmentOpeningPositions'
import type { InvestmentSegment } from '../../api/investments/investmentSegments'
import { FormGrid } from '../../components/feedback/ResponsiveDialog'
import { translateSeedName } from '../../i18n/seedNames'
import { today } from '../../utils/localDate'
import {
  emptyOpeningPositionDraft,
  isOpeningPositionStarted,
  openingPositionRequestOf,
  type OpeningPositionDraft,
} from './openingPositionDraft'
import { OpeningPositionFields } from './OpeningPositionFields'
import { relevantInvestmentFields } from './investmentProductFields'

/**
 * What the form collects; an empty `subcategoryId` means "no sub-category", an empty `ticker`
 * means "no ticker", an empty `segmentId` means "no segment" (F026, ADR 0023 - both generalized,
 * usable by any product; the form only shows them where they are relevant, issue #230).
 */
export interface InvestmentProductFormValues {
  name: string
  categoryId: string
  subcategoryId: string
  additionalNotes: string
  ticker: string
  segmentId: string
  /**
   * The holding's starting quantity and average price (F038, ADR 0033), only on create and only
   * when the "Opening position" section was filled; `undefined` otherwise.
   */
  openingPosition?: OpeningPositionRequest
}

interface InvestmentProductFormProps {
  categories: InvestmentCategory[]
  /** Options for the segment select (F026), shown for FII products. */
  segments: InvestmentSegment[]
  /** Values to start from (the edit case); defaults to an empty form (the add case). */
  initial?: InvestmentProductFormValues
  submitLabel: string
  submitting: boolean
  onSubmit: (values: InvestmentProductFormValues) => void
  onCancel?: () => void
  /**
   * Lays the form out for a `ResponsiveDialog` (F021): fields in a one-column-on-phone `FormGrid`
   * inside `DialogContent`, buttons in `DialogActions`. Off (default), the fields wrap in one flex
   * row with the buttons after them.
   */
  dialog?: boolean
  /** Dialog mode: shown above the fields (the caller's error banner), so a save error is visible. */
  banner?: ReactNode
}

/**
 * The create/edit form of an investment product (F008 spec): a name, a category, and an optional
 * sub-category, plus an optional ticker and segment (F026, ADR 0023). The ticker shows for
 * exchange-traded sub-categories and the segment for FIIs only (`relevantInvestmentFields`, issue
 * #230). A field that holds a stored value stays visible so saving never clears it unseen; a
 * value typed and then hidden by a sub-category change is kept in state (switching back restores
 * it) but not submitted. The sub-category select only offers the chosen category's
 * sub-categories, and is reset whenever the category changes - a sub-category always belongs to
 * exactly one category, and the backend rejects a mismatched pair. Some categories (Crypto) have
 * no sub-categories at all; a category-only product is valid.
 *
 * On create (no `initial`) the form also offers a collapsed, optional "Opening position" section
 * (F038, ADR 0033) for every product: as-of date (today), cotas, average price and current value.
 * Once anything is entered, cotas and average price are required; the current value stays optional.
 * Left alone, nothing is submitted for it.
 */
export function InvestmentProductForm({
  categories,
  segments,
  initial,
  submitLabel,
  submitting,
  onSubmit,
  onCancel,
  dialog = false,
  banner,
}: InvestmentProductFormProps) {
  const { t } = useTranslation(['investmentProducts', 'common'])
  const [name, setName] = useState(initial?.name ?? '')
  const [categoryId, setCategoryId] = useState(initial?.categoryId ?? '')
  const [subcategoryId, setSubcategoryId] = useState(initial?.subcategoryId ?? '')
  const [additionalNotes, setAdditionalNotes] = useState(initial?.additionalNotes ?? '')
  const [ticker, setTicker] = useState(initial?.ticker ?? '')
  const [segmentId, setSegmentId] = useState(initial?.segmentId ?? '')
  const offersOpeningPosition = initial === undefined
  const [defaultAsOfDate] = useState(today)
  const [openingDraft, setOpeningDraft] = useState<OpeningPositionDraft>(emptyOpeningPositionDraft)
  const openingStarted =
    offersOpeningPosition && isOpeningPositionStarted(openingDraft, defaultAsOfDate)
  const openingRequest = openingStarted ? openingPositionRequestOf(openingDraft) : null

  const subcategories = categories.find((c) => c.id === categoryId)?.subcategories ?? []
  const relevant = relevantInvestmentFields(categories, categoryId, subcategoryId)
  const showTicker = relevant.ticker || Boolean(initial?.ticker)
  const showSegment = relevant.segment || Boolean(initial?.segmentId)

  function changeCategory(next: string) {
    setCategoryId(next)
    setSubcategoryId('')
  }

  const fields = (
    <>
      <TextField
        label={t('investmentProducts:form.nameLabel')}
        size="small"
        value={name}
        onChange={(e) => setName(e.target.value)}
        slotProps={{ htmlInput: { maxLength: INVESTMENT_NAME_MAX_LENGTH } }}
        sx={dialog ? { gridColumn: '1 / -1' } : undefined}
      />
      <Select
        size="small"
        displayEmpty
        value={categoryId}
        onChange={(e) => changeCategory(e.target.value)}
        aria-label={t('investmentProducts:form.categoryLabel')}
        sx={dialog ? undefined : { minWidth: 180 }}
      >
        <MenuItem value="" disabled>
          {t('investmentProducts:form.categoryLabel')}
        </MenuItem>
        {categories.map((category) => (
          <MenuItem key={category.id} value={category.id}>
            {translateSeedName('investmentCategories', category.name, t)}
          </MenuItem>
        ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={subcategoryId}
        onChange={(e) => setSubcategoryId(e.target.value)}
        disabled={!categoryId}
        aria-label={t('investmentProducts:form.subcategoryLabel')}
        sx={dialog ? undefined : { minWidth: 180 }}
      >
        <MenuItem value="">{t('investmentProducts:form.noSubcategoryOption')}</MenuItem>
        {subcategories.map((subcategory) => (
          <MenuItem key={subcategory.id} value={subcategory.id}>
            {translateSeedName('investmentSubcategories', subcategory.name, t)}
          </MenuItem>
        ))}
      </Select>
      {showTicker && (
        <TextField
          label={t('investmentProducts:form.tickerLabel')}
          size="small"
          value={ticker}
          onChange={(e) => setTicker(e.target.value)}
          slotProps={{ htmlInput: { maxLength: INVESTMENT_NAME_MAX_LENGTH } }}
          sx={dialog ? undefined : { minWidth: 120 }}
        />
      )}
      {showSegment && (
        <Select
          size="small"
          displayEmpty
          value={segmentId}
          onChange={(e) => setSegmentId(e.target.value)}
          aria-label={t('investmentProducts:form.segmentLabel')}
          sx={dialog ? undefined : { minWidth: 180 }}
        >
          <MenuItem value="">{t('investmentProducts:form.noSegmentOption')}</MenuItem>
          {segments.map((segment) => (
            <MenuItem key={segment.id} value={segment.id}>
              {segment.name}
            </MenuItem>
          ))}
        </Select>
      )}
      <TextField
        label={t('investmentProducts:form.notesLabel')}
        size="small"
        multiline
        value={additionalNotes}
        onChange={(e) => setAdditionalNotes(e.target.value)}
        slotProps={{ htmlInput: { maxLength: 500 } }}
        sx={dialog ? { gridColumn: '1 / -1' } : undefined}
      />
      {offersOpeningPosition && (
        <Accordion
          disableGutters
          variant="outlined"
          slotProps={{ transition: { unmountOnExit: true } }}
          sx={dialog ? { gridColumn: '1 / -1' } : { width: '100%' }}
        >
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography>{t('investmentProducts:form.openingPositionTitle')}</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {t('investmentProducts:form.openingPositionHint')}
            </Typography>
            <FormGrid>
              <OpeningPositionFields
                draft={openingDraft}
                onChange={setOpeningDraft}
                currentValueHelper={t('investmentProducts:form.openingCurrentValueHelper')}
              />
            </FormGrid>
          </AccordionDetails>
        </Accordion>
      )}
    </>
  )
  const submitButton = (
    <Button
      variant="contained"
      disabled={submitting || !name.trim() || !categoryId || (openingStarted && !openingRequest)}
      onClick={() =>
        onSubmit({
          name: name.trim(),
          categoryId,
          subcategoryId,
          additionalNotes: additionalNotes.trim(),
          ticker: showTicker ? ticker.trim() : '',
          segmentId: showSegment ? segmentId : '',
          ...(openingRequest ? { openingPosition: openingRequest } : {}),
        })
      }
    >
      {submitLabel}
    </Button>
  )
  const cancelButton = onCancel && (
    <Button disabled={submitting} onClick={onCancel}>
      {t('common:actions.cancel')}
    </Button>
  )

  if (dialog) {
    return (
      <>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            {banner}
            <FormGrid>{fields}</FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          {cancelButton}
          {submitButton}
        </DialogActions>
      </>
    )
  }

  return (
    <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
      {fields}
      {submitButton}
      {cancelButton}
    </Box>
  )
}
