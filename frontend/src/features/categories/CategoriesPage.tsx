import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
  isProtectedCategory,
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
import { translateSeedName } from '../../i18n/seedNames'
import { sortCategories } from './sortCategories'

/**
 * Settings-style CRUD screen for categories (F002 spec): table with name + type, inline rename,
 * add-new dialog, delete. Type is immutable after creation (PRD S5.1), so there's no type picker on
 * existing rows - only on the add dialog. A protected category ({@link isProtectedCategory}: the
 * built-in fallback row of each type, F024's dedicated fuel category, F026's dedicated dividend
 * category - issue #117 unified the three) has neither a Rename nor a Delete action, since the
 * backend rejects both with a 409 anyway. The built-in row is still listed first within its type.
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
  const { t } = useTranslation(['categories', 'common'])
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
          {t('categories:title')}
        </Typography>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          {t('categories:addButton')}
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('categories:description')}
      </Typography>

      {/* While the add dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert message={addDialogOpen ? null : error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('common:actions.name')}</TableCell>
                <TableCell>{t('categories:typeColumn')}</TableCell>
                <TableCell align="right">{t('common:actions.actions')}</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={categoriesState} onRetry={retry} columns={3} actionsColumn>
              {categories?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={3} align="center">
                    <Typography color="text.secondary">{t('categories:emptyList')}</Typography>
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
                      translateSeedName('categories', category.name, t)
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={
                        category.type === 'INCOME'
                          ? t('categories:typeIncome')
                          : t('categories:typeExpense')
                      }
                      size="small"
                      color={category.type === 'INCOME' ? 'success' : 'default'}
                    />
                  </TableCell>
                  <TableCell align="right">
                    {/* Always rendered, hidden (not omitted) for a protected category: a `td`'s
                        `min-height` isn't reliably honored by the table row-height algorithm, so
                        an empty cell can render shorter than one with real buttons in it (issue
                        #117 follow-up) - real content is what reserves real space. `visibility:
                        hidden` (not `display: none`) keeps the buttons out of the accessibility
                        tree and tab order, same as not rendering them. */}
                    <Box
                      sx={isProtectedCategory(category) ? { visibility: 'hidden' } : undefined}
                      aria-hidden={isProtectedCategory(category) || undefined}
                    >
                      <InlineEditActions
                        editing={editingId === category.id}
                        onEdit={() => startEdit(category)}
                        onSave={() => void saveEdit(category.id)}
                        onCancel={cancelEdit}
                        editLabel={t('common:actions.rename')}
                      />
                      {editingId !== category.id && (
                        <IconButton
                          size="small"
                          aria-label={t('common:actions.delete')}
                          disabled={pendingDeleteId === category.id}
                          onClick={() => void handleDelete(category.id)}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

      <ResponsiveDialog open={addDialogOpen} onClose={closeAddDialog}>
        <DialogTitle>{t('categories:addDialogTitle')}</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid>
              <TextField
                label={t('common:actions.name')}
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
                <MenuItem value="EXPENSE">{t('categories:typeExpense')}</MenuItem>
                <MenuItem value="INCOME">{t('categories:typeIncome')}</MenuItem>
              </Select>
            </FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeAddDialog} disabled={adding}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={adding || !newName.trim()}
            onClick={() => void handleAdd()}
          >
            {t('common:actions.add')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    </Box>
  )
}
