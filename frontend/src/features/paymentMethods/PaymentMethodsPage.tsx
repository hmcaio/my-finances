import { useState } from 'react'
import {
  Box,
  Button,
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
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'

/**
 * Settings-style CRUD screen for payment methods (F002 spec): table with name, inline rename,
 * add-new form, delete. Same pattern as CategoriesPage, minus the type column/picker (payment
 * methods have no type, PRD S5.2).
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

  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  async function handleAdd() {
    if (!newName.trim()) return
    setError(null)
    setAdding(true)
    try {
      await createMutation.mutateAsync({ name: newName.trim() })
      setNewName('')
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
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Payment Methods
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Which rail a transaction went through (debit card, PIX, cash, ...) - informational only,
        doesn't affect account balances.
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

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

      <Paper variant="outlined" sx={{ p: 2, maxWidth: 560 }}>
        <Typography variant="subtitle1" gutterBottom>
          Add payment method
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <TextField
            label="Name"
            size="small"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            slotProps={{ htmlInput: { maxLength: PAYMENT_METHOD_NAME_MAX_LENGTH } }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void handleAdd()
            }}
          />
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
