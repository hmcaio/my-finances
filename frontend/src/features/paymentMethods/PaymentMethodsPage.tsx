import { useState } from 'react'
import {
  Box,
  Button,
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
import {
  PAYMENT_METHOD_NAME_MAX_LENGTH,
  type PaymentMethod,
} from '../../api/paymentMethods/paymentMethods'
import {
  usePaymentMethods,
  useCreatePaymentMethod,
  useRenamePaymentMethod,
  useDeletePaymentMethod,
} from '../../api/paymentMethods/paymentMethodsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'

/**
 * Settings-style CRUD screen for payment methods (F002 spec): table with name, inline rename,
 * add-new dialog, delete. Same pattern as CategoriesPage, minus the type column/picker (payment
 * methods have no type, PRD S5.2).
 *
 * Responsive (F021): Name is the only data column, so per the spec's 1-2-column rule this stays a
 * plain table at every size. The header's Add button opens a single Name field in a
 * `ResponsiveDialog` (one-column, full screen below `sm`) instead of the panel that used to sit
 * below the table. Inline rename stays inline at every size (same reasoning as `CategoriesPage`):
 * the table never reflows into cards, so the row's own text field is already just as usable on a
 * phone as on desktop.
 */
export function PaymentMethodsPage() {
  const [error, setError] = useState<string | null>(null)
  const paymentMethodsQuery = usePaymentMethods()
  const paymentMethods = paymentMethodsQuery.data
  const paymentMethodsState = useQueryState(paymentMethodsQuery, setError)
  const createMutation = useCreatePaymentMethod()
  const renameMutation = useRenamePaymentMethod()
  const deleteMutation = useDeletePaymentMethod()

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingName, setEditingName] = useState('')

  const [newName, setNewName] = useState('')
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
      await createMutation.mutateAsync({ name: newName.trim() })
      setNewName('')
      setAddDialogOpen(false)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  function startEdit(paymentMethod: PaymentMethod) {
    setEditingId(paymentMethod.id)
    setEditingName(paymentMethod.name)
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
    paymentMethodsState.reload()
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
          Payment Methods
        </Typography>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          Add payment method
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Which rail a transaction went through (debit card, PIX, cash, ...) - informational only,
        doesn't affect account balances.
      </Typography>

      {/* While the add dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert message={addDialogOpen ? null : error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={paymentMethodsState} onRetry={retry} columns={2} actionsColumn>
              {paymentMethods?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2} align="center">
                    <Typography color="text.secondary">No payment methods yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {paymentMethods?.map((paymentMethod) => (
                <TableRow key={paymentMethod.id}>
                  <TableCell>
                    {editingId === paymentMethod.id ? (
                      <TextField
                        size="small"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        slotProps={{ htmlInput: { maxLength: PAYMENT_METHOD_NAME_MAX_LENGTH } }}
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') void saveEdit(paymentMethod.id)
                          if (e.key === 'Escape') cancelEdit()
                        }}
                      />
                    ) : (
                      paymentMethod.name
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <InlineEditActions
                      editing={editingId === paymentMethod.id}
                      onEdit={() => startEdit(paymentMethod)}
                      onSave={() => void saveEdit(paymentMethod.id)}
                      onCancel={cancelEdit}
                      editLabel="Rename"
                    />
                    {editingId !== paymentMethod.id && (
                      <IconButton
                        size="small"
                        aria-label="Delete"
                        disabled={pendingDeleteId === paymentMethod.id}
                        onClick={() => void handleDelete(paymentMethod.id)}
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
        <DialogTitle>Add payment method</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid columns={1}>
              <TextField
                label="Name"
                size="small"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                slotProps={{ htmlInput: { maxLength: PAYMENT_METHOD_NAME_MAX_LENGTH } }}
                autoFocus
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleAdd()
                }}
              />
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
