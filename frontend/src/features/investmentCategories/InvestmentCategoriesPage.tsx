import { Fragment, useState } from 'react'
import {
  Box,
  Button,
  Chip,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Paper,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { defaultErrorMessage } from '../../api/core/apiError'
import {
  INVESTMENT_NAME_MAX_LENGTH,
  type InvestmentCategory,
} from '../../api/investments/investmentCategories'
import {
  useCreateInvestmentCategory,
  useDeleteInvestmentCategory,
  useInvestmentCategories,
  useRenameInvestmentCategory,
} from '../../api/investments/investmentCategoriesQueries'
import {
  useCreateInvestmentSubcategory,
  useDeleteInvestmentSubcategory,
  useRenameInvestmentSubcategory,
} from '../../api/investments/investmentSubcategoriesQueries'
import { DataTableBody } from '../../components/table/DataTableBody'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { useQueryState } from '../../hooks/queryState'

/** What is being renamed inline: one row at a time, either level. */
type Editing = { kind: 'category' | 'subcategory'; id: string } | null

/**
 * Settings screen for the two-level investment taxonomy (F008 spec): an expandable list of
 * categories, each opening onto its sub-categories. Add a category, add a sub-category under it,
 * rename either level inline, delete either level - a delete the backend refuses (a category with
 * sub-categories or products, a sub-category a product uses) surfaces the client's own 409 message,
 * since the backend sends no text. A sub-category's parent can't be changed, so there is no way to
 * move one. Renaming is always allowed.
 *
 * Responsive (F021): the categories table has only two data columns (Name, Sub-categories count;
 * the actions column doesn't count), so per the spec's 1-2-column rule it stays a plain table at
 * every size - no `ResponsiveTable`/cards, and inline rename at both levels stays inline (same
 * reasoning as `CategoriesPage`/`InstitutionsPage`: the table never reflows, so there is no benefit
 * to a mobile edit dialog). The page-level "Add category" panel is genuinely a separate form panel
 * (like the other settings pages), so it moves into a header button + `ResponsiveDialog` (one
 * column, full screen below `sm`). The per-category "add sub-category" row is different: it is not
 * a separate panel but an inline part of the expanded row itself, always shown right next to the
 * category it belongs to (its label already reads "New sub-category in {category}") - lifting it
 * into a page-level dialog would need a category picker to recover context the row already gives
 * for free, for a form that's just one text field plus a button that already wraps at any width. It
 * is left exactly as it was.
 */
export function InvestmentCategoriesPage() {
  const [error, setError] = useState<string | null>(null)
  const categoriesQuery = useInvestmentCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const createCategory = useCreateInvestmentCategory()
  const renameCategory = useRenameInvestmentCategory()
  const deleteCategory = useDeleteInvestmentCategory()
  const createSubcategory = useCreateInvestmentSubcategory()
  const renameSubcategory = useRenameInvestmentSubcategory()
  const deleteSubcategory = useDeleteInvestmentSubcategory()

  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set())
  const [editing, setEditing] = useState<Editing>(null)
  const [editingName, setEditingName] = useState('')

  const [newCategoryName, setNewCategoryName] = useState('')
  const [addingCategory, setAddingCategory] = useState(false)
  const [addDialogOpen, setAddDialogOpen] = useState(false)
  // One draft per category, so typing under one never leaks into another.
  const [newSubcategoryNames, setNewSubcategoryNames] = useState<Record<string, string>>({})
  const [addingSubcategoryTo, setAddingSubcategoryTo] = useState<string | null>(null)
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  function toggle(id: string) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      return next
    })
  }

  function startEdit(kind: 'category' | 'subcategory', id: string, name: string) {
    setEditing({ kind, id })
    setEditingName(name)
  }

  function cancelEdit() {
    setEditing(null)
    setEditingName('')
  }

  function closeAddDialog() {
    setAddDialogOpen(false)
    setError(null)
  }

  async function handleAddCategory() {
    const name = newCategoryName.trim()
    if (!name) return
    setError(null)
    setAddingCategory(true)
    try {
      await createCategory.mutateAsync({ name })
      setNewCategoryName('')
      setAddDialogOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAddingCategory(false)
    }
  }

  async function handleAddSubcategory(category: InvestmentCategory) {
    const name = (newSubcategoryNames[category.id] ?? '').trim()
    if (!name) return
    setError(null)
    setAddingSubcategoryTo(category.id)
    try {
      await createSubcategory.mutateAsync({ investmentCategoryId: category.id, name })
      setNewSubcategoryNames((prev) => ({ ...prev, [category.id]: '' }))
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAddingSubcategoryTo(null)
    }
  }

  async function saveEdit() {
    const name = editingName.trim()
    if (!editing || !name) return
    setError(null)
    try {
      if (editing.kind === 'category') {
        await renameCategory.mutateAsync({ id: editing.id, name })
      } else {
        await renameSubcategory.mutateAsync({ id: editing.id, name })
      }
      cancelEdit()
    } catch (err) {
      setError(defaultErrorMessage(err))
    }
  }

  async function handleDeleteCategory(id: string) {
    setError(null)
    setPendingDeleteId(id)
    try {
      await deleteCategory.mutateAsync(id)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setPendingDeleteId(null)
    }
  }

  async function handleDeleteSubcategory(id: string) {
    setError(null)
    setPendingDeleteId(id)
    try {
      await deleteSubcategory.mutateAsync(id)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setPendingDeleteId(null)
    }
  }

  // Clears the stale banner before retrying, so it doesn't outlive a successful retry.
  function retry() {
    setError(null)
    categoriesState.reload()
  }

  function nameCell(kind: 'category' | 'subcategory', id: string, name: string, label: string) {
    if (editing?.kind === kind && editing.id === id) {
      return (
        <TextField
          size="small"
          value={editingName}
          onChange={(e) => setEditingName(e.target.value)}
          autoFocus
          slotProps={{
            htmlInput: { maxLength: INVESTMENT_NAME_MAX_LENGTH, 'aria-label': label },
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') void saveEdit()
            if (e.key === 'Escape') cancelEdit()
          }}
        />
      )
    }
    return name
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
          Investment categories
        </Typography>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          Add category
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        The two-level taxonomy used to classify investment products: a category, and optionally a
        sub-category under it. Renaming is always allowed; a category or sub-category that products
        still use can&apos;t be deleted.
      </Typography>

      {/* While the add dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert message={addDialogOpen ? null : error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Sub-categories</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={categoriesState} onRetry={retry} columns={3} actionsColumn>
              {categories?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={3} align="center">
                    <Typography color="text.secondary">No investment categories yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {categories?.map((category) => {
                const isOpen = expanded.has(category.id)
                const isEditing = editing?.kind === 'category' && editing.id === category.id
                return (
                  <Fragment key={category.id}>
                    <TableRow>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <IconButton
                            size="small"
                            aria-label={`${isOpen ? 'Collapse' : 'Expand'} ${category.name}`}
                            aria-expanded={isOpen}
                            onClick={() => toggle(category.id)}
                          >
                            {isOpen ? (
                              <ExpandLessIcon fontSize="small" />
                            ) : (
                              <ExpandMoreIcon fontSize="small" />
                            )}
                          </IconButton>
                          {nameCell('category', category.id, category.name, 'Category name')}
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={
                            category.subcategories.length === 1
                              ? '1 sub-category'
                              : `${category.subcategories.length} sub-categories`
                          }
                        />
                      </TableCell>
                      <TableCell align="right">
                        <InlineEditActions
                          editing={isEditing}
                          onEdit={() => startEdit('category', category.id, category.name)}
                          onSave={() => void saveEdit()}
                          onCancel={cancelEdit}
                          editLabel="Rename"
                        />
                        {!isEditing && (
                          <IconButton
                            size="small"
                            aria-label="Delete"
                            disabled={pendingDeleteId === category.id}
                            onClick={() => void handleDeleteCategory(category.id)}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        )}
                      </TableCell>
                    </TableRow>
                    {isOpen && (
                      <>
                        {category.subcategories.map((subcategory) => {
                          const isEditingSub =
                            editing?.kind === 'subcategory' && editing.id === subcategory.id
                          return (
                            <TableRow key={subcategory.id} sx={{ bgcolor: 'action.hover' }}>
                              <TableCell sx={{ pl: 7 }} colSpan={2}>
                                {nameCell(
                                  'subcategory',
                                  subcategory.id,
                                  subcategory.name,
                                  'Sub-category name',
                                )}
                              </TableCell>
                              <TableCell align="right">
                                <InlineEditActions
                                  editing={isEditingSub}
                                  onEdit={() =>
                                    startEdit('subcategory', subcategory.id, subcategory.name)
                                  }
                                  onSave={() => void saveEdit()}
                                  onCancel={cancelEdit}
                                  editLabel="Rename"
                                />
                                {!isEditingSub && (
                                  <IconButton
                                    size="small"
                                    aria-label="Delete"
                                    disabled={pendingDeleteId === subcategory.id}
                                    onClick={() => void handleDeleteSubcategory(subcategory.id)}
                                  >
                                    <DeleteIcon fontSize="small" />
                                  </IconButton>
                                )}
                              </TableCell>
                            </TableRow>
                          )
                        })}
                        <TableRow sx={{ bgcolor: 'action.hover' }}>
                          <TableCell sx={{ pl: 7 }} colSpan={3}>
                            <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
                              <TextField
                                size="small"
                                label={`New sub-category in ${category.name}`}
                                value={newSubcategoryNames[category.id] ?? ''}
                                onChange={(e) =>
                                  setNewSubcategoryNames((prev) => ({
                                    ...prev,
                                    [category.id]: e.target.value,
                                  }))
                                }
                                onKeyDown={(e) => {
                                  if (e.key === 'Enter') void handleAddSubcategory(category)
                                }}
                                slotProps={{ htmlInput: { maxLength: INVESTMENT_NAME_MAX_LENGTH } }}
                              />
                              <Button
                                variant="outlined"
                                aria-label={`Add sub-category to ${category.name}`}
                                disabled={
                                  addingSubcategoryTo === category.id ||
                                  !(newSubcategoryNames[category.id] ?? '').trim()
                                }
                                onClick={() => void handleAddSubcategory(category)}
                              >
                                Add sub-category
                              </Button>
                            </Box>
                          </TableCell>
                        </TableRow>
                      </>
                    )}
                  </Fragment>
                )
              })}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

      <ResponsiveDialog open={addDialogOpen} onClose={closeAddDialog}>
        <DialogTitle>Add category</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid columns={1}>
              <TextField
                label="Category name"
                size="small"
                value={newCategoryName}
                onChange={(e) => setNewCategoryName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleAddCategory()
                }}
                slotProps={{ htmlInput: { maxLength: INVESTMENT_NAME_MAX_LENGTH } }}
                autoFocus
              />
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeAddDialog} disabled={addingCategory}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={addingCategory || !newCategoryName.trim()}
            onClick={() => void handleAddCategory()}
          >
            Add
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    </Box>
  )
}
