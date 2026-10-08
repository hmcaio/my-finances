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
  INVESTMENT_SEGMENT_NAME_MAX_LENGTH,
  type InvestmentSegment,
} from '../../api/investments/investmentSegments'
import {
  useCreateInvestmentSegment,
  useDeleteInvestmentSegment,
  useInvestmentSegments,
  useRenameInvestmentSegment,
} from '../../api/investments/investmentSegmentsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'

/**
 * Settings-style CRUD screen for investment segments (F026 spec, ADR 0023): table with name,
 * inline rename, add-new dialog, delete. Same flat, name-only taxonomy pattern as
 * `VehiclesPage`/`PaymentMethodsPage`. A segment still referenced by an investment product can't
 * be deleted (409); renaming is always allowed.
 *
 * Responsive (F021): Name is the only data column, so per the spec's 1-2-column rule this stays a
 * plain table at every size.
 */
export function InvestmentSegmentsPage() {
  const [error, setError] = useState<string | null>(null)
  const segmentsQuery = useInvestmentSegments()
  const segments = segmentsQuery.data
  const segmentsState = useQueryState(segmentsQuery, setError)
  const createMutation = useCreateInvestmentSegment()
  const renameMutation = useRenameInvestmentSegment()
  const deleteMutation = useDeleteInvestmentSegment()

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

  function startEdit(segment: InvestmentSegment) {
    setEditingId(segment.id)
    setEditingName(segment.name)
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
    segmentsState.reload()
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
          Investment segments
        </Typography>
        <Button variant="contained" onClick={() => setAddDialogOpen(true)}>
          Add segment
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        What kind of real estate an FII holds (Shoppings, Logistica, Papel, ...) - orthogonal to the
        category/sub-category taxonomy. A segment still used by an investment product can&apos;t be
        deleted.
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
            <DataTableBody state={segmentsState} onRetry={retry} columns={2} actionsColumn>
              {segments?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2} align="center">
                    <Typography color="text.secondary">No investment segments yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {segments?.map((segment) => (
                <TableRow key={segment.id}>
                  <TableCell>
                    {editingId === segment.id ? (
                      <TextField
                        size="small"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        slotProps={{
                          htmlInput: { maxLength: INVESTMENT_SEGMENT_NAME_MAX_LENGTH },
                        }}
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') void saveEdit(segment.id)
                          if (e.key === 'Escape') cancelEdit()
                        }}
                      />
                    ) : (
                      segment.name
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <InlineEditActions
                      editing={editingId === segment.id}
                      onEdit={() => startEdit(segment)}
                      onSave={() => void saveEdit(segment.id)}
                      onCancel={cancelEdit}
                      editLabel="Rename"
                    />
                    {editingId !== segment.id && (
                      <IconButton
                        size="small"
                        aria-label="Delete"
                        disabled={pendingDeleteId === segment.id}
                        onClick={() => void handleDelete(segment.id)}
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
        <DialogTitle>Add segment</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <ErrorAlert message={error} onDismiss={() => setError(null)} />
            <FormGrid columns={1}>
              <TextField
                label="Name"
                size="small"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                slotProps={{ htmlInput: { maxLength: INVESTMENT_SEGMENT_NAME_MAX_LENGTH } }}
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
