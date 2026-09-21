import { useState } from 'react'
import { Box, Button, MenuItem, Select, TextField } from '@mui/material'
import { INVESTMENT_NAME_MAX_LENGTH, type InvestmentCategory } from '../../api/investmentCategories'

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
}: InvestmentProductFormProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [categoryId, setCategoryId] = useState(initial?.categoryId ?? '')
  const [subcategoryId, setSubcategoryId] = useState(initial?.subcategoryId ?? '')

  const subcategories = categories.find((c) => c.id === categoryId)?.subcategories ?? []

  function changeCategory(next: string) {
    setCategoryId(next)
    setSubcategoryId('')
  }

  return (
    <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
      <TextField
        label="Product name"
        size="small"
        value={name}
        onChange={(e) => setName(e.target.value)}
        slotProps={{ htmlInput: { maxLength: INVESTMENT_NAME_MAX_LENGTH } }}
      />
      <Select
        size="small"
        displayEmpty
        value={categoryId}
        onChange={(e) => changeCategory(e.target.value)}
        aria-label="Category"
        sx={{ minWidth: 180 }}
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
        sx={{ minWidth: 180 }}
      >
        <MenuItem value="">No sub-category</MenuItem>
        {subcategories.map((subcategory) => (
          <MenuItem key={subcategory.id} value={subcategory.id}>
            {subcategory.name}
          </MenuItem>
        ))}
      </Select>
      <Button
        variant="contained"
        disabled={submitting || !name.trim() || !categoryId}
        onClick={() => onSubmit({ name: name.trim(), categoryId, subcategoryId })}
      >
        {submitLabel}
      </Button>
      {onCancel && (
        <Button disabled={submitting} onClick={onCancel}>
          Cancel
        </Button>
      )}
    </Box>
  )
}
