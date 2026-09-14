import { useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import CheckIcon from '@mui/icons-material/Check'
import CloseIcon from '@mui/icons-material/Close'
import {
  createPaymentMethod,
  deletePaymentMethod,
  getPaymentMethods,
  renamePaymentMethod,
  type PaymentMethod,
} from '../../api/paymentMethods'
import { ApiError } from '../../api/apiError'

/**
 * Settings-style CRUD screen for payment methods (F002 spec): table with name, inline rename,
 * add-new form, delete. Same pattern as CategoriesPage, minus the type column/picker (payment
 * methods have no type, PRD S5.2).
 */
export function PaymentMethodsPage() {
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingName, setEditingName] = useState('')

  const [newName, setNewName] = useState('')
  const [adding, setAdding] = useState(false)

  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  function load() {
    getPaymentMethods()
      .then(setPaymentMethods)
      .catch((err: unknown) => setError(errorMessage(err)))
  }

  useEffect(() => {
    load()
  }, [])

  async function handleAdd() {
    if (!newName.trim()) return
    setError(null)
    setAdding(true)
    try {
      const created = await createPaymentMethod({ name: newName.trim() })
      setPaymentMethods((prev) => [...(prev ?? []), created])
      setNewName('')
    } catch (err) {
      setError(errorMessage(err))
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
      const updated = await renamePaymentMethod(id, { name: editingName.trim() })
      setPaymentMethods((prev) => prev?.map((pm) => (pm.id === id ? updated : pm)) ?? null)
      cancelEdit()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function handleDelete(id: string) {
    setError(null)
    setPendingDeleteId(id)
    try {
      await deletePaymentMethod(id)
      setPaymentMethods((prev) => prev?.filter((pm) => pm.id !== id) ?? null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setPendingDeleteId(null)
    }
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

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {paymentMethods === null && (
                <TableRow>
                  <TableCell colSpan={2} align="center">
                    <CircularProgress size={20} />
                  </TableCell>
                </TableRow>
              )}
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
                    {editingId === paymentMethod.id ? (
                      <>
                        <IconButton
                          size="small"
                          aria-label="Save"
                          onClick={() => void saveEdit(paymentMethod.id)}
                        >
                          <CheckIcon fontSize="small" />
                        </IconButton>
                        <IconButton size="small" aria-label="Cancel" onClick={cancelEdit}>
                          <CloseIcon fontSize="small" />
                        </IconButton>
                      </>
                    ) : (
                      <>
                        <IconButton
                          size="small"
                          aria-label="Rename"
                          onClick={() => startEdit(paymentMethod)}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          aria-label="Delete"
                          disabled={pendingDeleteId === paymentMethod.id}
                          onClick={() => void handleDelete(paymentMethod.id)}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
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

function errorMessage(err: unknown): string {
  if (err instanceof ApiError && err.status === 409) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong.'
}
