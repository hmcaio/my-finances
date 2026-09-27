import { useState, type ReactNode } from 'react'
import {
  Box,
  Button,
  DialogActions,
  DialogContent,
  MenuItem,
  Select,
  TextField,
} from '@mui/material'
import {
  INVESTMENT_NAME_MAX_LENGTH,
  type InvestmentCategory,
} from '../../api/investments/investmentCategories'
import { FormGrid } from '../../components/feedback/ResponsiveDialog'

/** What the form collects; an empty `subcategoryId` means "no sub-category". */
export interface InvestmentProductFormValues {
  name: string
  categoryId: string
  subcategoryId: string
}

interface InvestmentProductFormProps {
  categories: InvestmentCategory[]
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
 * sub-category. The sub-category select only offers the chosen category's sub-categories, and is
 * reset whenever the category changes - a sub-category always belongs to exactly one category, and
 * the backend rejects a mismatched pair. Some categories (Crypto) have no sub-categories at all;
 * a category-only product is valid.
 */
export function InvestmentProductForm({
  categories,
  initial,
  submitLabel,
  submitting,
  onSubmit,
  onCancel,
  dialog = false,
  banner,
}: InvestmentProductFormProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [categoryId, setCategoryId] = useState(initial?.categoryId ?? '')
  const [subcategoryId, setSubcategoryId] = useState(initial?.subcategoryId ?? '')

  const subcategories = categories.find((c) => c.id === categoryId)?.subcategories ?? []

  function changeCategory(next: string) {
    setCategoryId(next)
    setSubcategoryId('')
  }

  const fields = (
    <>
      <TextField
        label="Product name"
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
        aria-label="Category"
        sx={dialog ? undefined : { minWidth: 180 }}
      >
        <MenuItem value="" disabled>
          Category
        </MenuItem>
        {categories.map((category) => (
          <MenuItem key={category.id} value={category.id}>
            {category.name}
          </MenuItem>
        ))}
      </Select>
      <Select
        size="small"
        displayEmpty
        value={subcategoryId}
        onChange={(e) => setSubcategoryId(e.target.value)}
        disabled={!categoryId}
        aria-label="Sub-category"
        sx={dialog ? undefined : { minWidth: 180 }}
      >
        <MenuItem value="">No sub-category</MenuItem>
        {subcategories.map((subcategory) => (
          <MenuItem key={subcategory.id} value={subcategory.id}>
            {subcategory.name}
          </MenuItem>
        ))}
      </Select>
    </>
  )
  const submitButton = (
    <Button
      variant="contained"
      disabled={submitting || !name.trim() || !categoryId}
      onClick={() => onSubmit({ name: name.trim(), categoryId, subcategoryId })}
    >
      {submitLabel}
    </Button>
  )
  const cancelButton = onCancel && (
    <Button disabled={submitting} onClick={onCancel}>
      Cancel
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
