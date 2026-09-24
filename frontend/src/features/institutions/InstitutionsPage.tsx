import { useMemo, useState } from 'react'
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
import { INSTITUTION_NAME_MAX_LENGTH, type Institution } from '../../api/institutions'
import {
  useInstitutions,
  useCreateInstitution,
  useRenameInstitution,
  useDeleteInstitution,
} from '../../api/institutionsQueries'
import { defaultErrorMessage } from '../../api/apiError'
import { ErrorAlert } from '../../components/ErrorAlert'
import { InlineEditActions } from '../../components/InlineEditActions'
import { DataTableBody } from '../../components/DataTableBody'
import { useQueryState } from '../../hooks/queryState'
import { sortInstitutions } from './sortInstitutions'

/**
 * Settings-style CRUD screen for institutions (F017 spec): table with name, inline rename,
 * add-new form, delete. Same pattern as PaymentMethodsPage. The built-in "No institution" row is
 * listed first, can be renamed like any other, and has no delete action (the backend would answer
 * 409 anyway).
 */
export function InstitutionsPage() {
  const [error, setError] = useState<string | null>(null)
  const institutionsQuery = useInstitutions()
  const institutions = institutionsQuery.data
  const institutionsState = useQueryState(institutionsQuery, setError)
  const createMutation = useCreateInstitution()
  const renameMutation = useRenameInstitution()
  const deleteMutation = useDeleteInstitution()
  const sorted = useMemo(() => sortInstitutions(institutions ?? []), [institutions])

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

  function startEdit(institution: Institution) {
    setEditingId(institution.id)
    setEditingName(institution.name)
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
    institutionsState.reload()
  }

  return (
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Institutions
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        The banks, brokers and card issuers your accounts sit at. Every account has one; use "No
        institution" for money that isn't at any (a cash wallet, for example). An institution can be
        deleted once no account uses it.
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
            <DataTableBody state={institutionsState} onRetry={retry} columns={2} actionsColumn>
              {institutions?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2} align="center">
                    <Typography color="text.secondary">No institutions yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {sorted.map((institution) => (
                <TableRow key={institution.id}>
                  <TableCell>
                    {editingId === institution.id ? (
                      <TextField
                        size="small"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        slotProps={{ htmlInput: { maxLength: INSTITUTION_NAME_MAX_LENGTH } }}
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') void saveEdit(institution.id)
                          if (e.key === 'Escape') cancelEdit()
                        }}
                      />
                    ) : (
                      institution.name
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <InlineEditActions
                      editing={editingId === institution.id}
                      onEdit={() => startEdit(institution)}
                      onSave={() => void saveEdit(institution.id)}
                      onCancel={cancelEdit}
                      editLabel="Rename"
                    />
                    {editingId !== institution.id && !institution.builtIn && (
                      <IconButton
                        size="small"
                        aria-label="Delete"
                        disabled={pendingDeleteId === institution.id}
                        onClick={() => void handleDelete(institution.id)}
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
          Add institution
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <TextField
            label="Name"
            size="small"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            slotProps={{ htmlInput: { maxLength: INSTITUTION_NAME_MAX_LENGTH } }}
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
