import { useMemo, useState } from 'react'
import {
  Box,
  Button,
  Chip,
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
  createCategory,
  deleteCategory,
  getCategories,
  renameCategory,
  type Category,
  type CategoryType,
} from '../../api/categories'
import { defaultErrorMessage } from '../../api/apiError'
import { ErrorAlert } from '../../components/ErrorAlert'
import { InlineEditActions } from '../../components/InlineEditActions'
import { DataTableBody } from '../../components/DataTableBody'
import { useAsyncData } from '../../hooks/useAsyncData'
import { sortCategories } from './sortCategories'

/**
 * Settings-style CRUD screen for categories (F002 spec): table with name + type, inline rename,
 * add-new form, delete. Type is immutable after creation (PRD S5.1), so there's no type picker on
 * existing rows - only on the add-new form. The built-in fallback row of each type is listed first
 * within its type, can be renamed like any other, and has no delete action (the backend would
 * answer 409 anyway).
 */
export function CategoriesPage() {
  const [error, setError] = useState<string | null>(null)
  const {
    data: categories,
    setData: setCategories,
    ...categoriesState
  } = useAsyncData(getCategories, [], { onError: setError })
  const sorted = useMemo(() => sortCategories(categories ?? []), [categories])

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingName, setEditingName] = useState('')

  const [newName, setNewName] = useState('')
  const [newType, setNewType] = useState<CategoryType>('EXPENSE')
  const [adding, setAdding] = useState(false)

  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  async function handleAdd() {
    if (!newName.trim()) return
    setError(null)
    setAdding(true)
    try {
      const created = await createCategory({ name: newName.trim(), type: newType })
      setCategories((prev) => [...(prev ?? []), created])
      setNewName('')
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
      const updated = await renameCategory(id, { name: editingName.trim() })
      setCategories((prev) => prev?.map((c) => (c.id === id ? updated : c)) ?? null)
      cancelEdit()
    } catch (err) {
      setError(defaultErrorMessage(err))
    }
  }

  async function handleDelete(id: string) {
    setError(null)
    setPendingDeleteId(id)
    try {
      await deleteCategory(id)
      setCategories((prev) => prev?.filter((c) => c.id !== id) ?? null)
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
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Categories
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Income and expense categories used to classify transactions. Renaming is always allowed;
        type is fixed once a category is created. The built-in category of each type can be renamed
        but not deleted.
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

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

      <Paper variant="outlined" sx={{ p: 2, maxWidth: 560 }}>
        <Typography variant="subtitle1" gutterBottom>
          Add category
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <TextField
            label="Name"
            size="small"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
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
          <Button
            variant="contained"
            disabled={adding || !newName.trim()}
            onClick={() => void handleAdd()}
          >
            Add
          </Button>
        </Box>
      </Paper>
    </Box>
  )
}
