import { useMemo, useState } from 'react'
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
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {
  CATEGORY_NAME_MAX_LENGTH,
  type Category,
  type CategoryType,
} from '../../api/categories/categories'
import {
  useCategories,
  useCreateCategory,
  useDeleteCategory,
  useRenameCategory,
} from '../../api/categories/categoriesQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'
import { sortCategories } from './sortCategories'

/**
 * Settings-style CRUD screen for categories (F002 spec): table with name + type, inline rename,
 * add-new dialog, delete. Type is immutable after creation (PRD S5.1), so there's no type picker on
 * existing rows - only on the add dialog. The built-in fallback row of each type is listed first
 * within its type, can be renamed like any other, and has no delete action (the backend would
 * answer 409 anyway).
 *
 * Responsive (F021): Name and Type are the only two data columns (the actions column doesn't
 * count), so per the spec's 1-2-column rule this stays a plain table at every size - no
 * `ResponsiveTable`/cards. The header's Add button opens the fields in a `ResponsiveDialog` (full
 * screen below `sm`) instead of the panel that used to sit below the table. Inline rename stays
 * inline at every size too: unlike `BudgetsPage` (whose table becomes cards on mobile), this table
 * never reflows, so the row's own text field is exactly as usable on a phone as on desktop and a
 * mobile edit dialog would add a surface for no benefit.
 */
export function CategoriesPage() {
  const [error, setError] = useState<string | null>(null)
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const createMutation = useCreateCategory()
  const renameMutation = useRenameCategory()
  const deleteMutation = useDeleteCategory()
  const sorted = useMemo(() => sortCategories(categories ?? []), [categories])

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingName, setEditingName] = useState('')

  const [newName, setNewName] = useState('')
  const [newType, setNewType] = useState<CategoryType>('EXPENSE')
  const [adding, setAdding] = useState(false)
  const [addDialogOpen, setAddDialogOpen] = useState(false)

  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  function closeAddDialog() {
    setAddDialogOpen(false)
    setError(null)
  }

  async function handleAdd() {
    if (!newName.trim()) return
    setError(null)
    setAdding(true)
    try {
      await createMutation.mutateAsync({ name: newName.trim(), type: newType })
      setNewName('')
      setAddDialogOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  function startEdit(category: Category) {
    setEditingId(category.id)
    setEditingName(category.name)
  }

  function cancelEdit() {
    setEditingId(null)
    setEditingName('')
  }

  async function saveEdit(id: string) {
    if (!editingName.trim()) return
    setError(null)
    try {
      await renameMutation.mutateAsync({ id, name: editingName.trim() })
      cancelEdit()
    } catch (err) {
      setError(defaultErrorMessage(err))
    }
  }

  async function handleDelete(id: string) {
    setError(null)
    setPendingDeleteId(id)
    try {
      await deleteMutation.mutateAsync(id)
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
          Categories
        </Typography>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          Add category
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Income and expense categories used to classify transactions. Renaming is always allowed;
        type is fixed once a category is created. The built-in category of each type can be renamed
        but not deleted.
      </Typography>

      {/* While the add dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert message={addDialogOpen ? null : error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={categoriesState} onRetry={retry} columns={3} actionsColumn>
              {categories?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={3} align="center">
                    <Typography color="text.secondary">No categories yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {sorted.map((category) => (
                <TableRow key={category.id}>
                  <TableCell>
                    {editingId === category.id ? (
                      <TextField
                        size="small"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        slotProps={{ htmlInput: { maxLength: CATEGORY_NAME_MAX_LENGTH } }}
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') void saveEdit(category.id)
                          if (e.key === 'Escape') cancelEdit()
                        }}
                      />
                    ) : (
                      category.name
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={category.type}
                      size="small"
                      color={category.type === 'INCOME' ? 'success' : 'default'}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <InlineEditActions
                      editing={editingId === category.id}
                      onEdit={() => startEdit(category)}
                      onSave={() => void saveEdit(category.id)}
                      onCancel={cancelEdit}
                      editLabel="Rename"
                    />
                    {editingId !== category.id && !category.builtIn && (
                      <IconButton
                        size="small"
                        aria-label="Delete"
                        disabled={pendingDeleteId === category.id}
                        onClick={() => void handleDelete(category.id)}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

      <ResponsiveDialog open={addDialogOpen} onClose={closeAddDialog}>
        <DialogTitle>Add category</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <TextField
                label="Name"
                size="small"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                slotProps={{ htmlInput: { maxLength: CATEGORY_NAME_MAX_LENGTH } }}
                autoFocus
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleAdd()
                }}
              />
              <Select
                size="small"
                value={newType}
                onChange={(e) => setNewType(e.target.value as CategoryType)}
              >
                <MenuItem value="EXPENSE">Expense</MenuItem>
                <MenuItem value="INCOME">Income</MenuItem>
              </Select>
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeAddDialog} disabled={adding}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={adding || !newName.trim()}
            onClick={() => void handleAdd()}
          >
            Add
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    </Box>
  )
}
