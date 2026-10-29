import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { VEHICLE_NAME_MAX_LENGTH, type Vehicle } from '../../api/vehicles/vehicles'
import {
  useVehicles,
  useCreateVehicle,
  useRenameVehicle,
  useDeleteVehicle,
} from '../../api/vehicles/vehiclesQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { AddFormDialog } from '../../components/feedback/AddFormDialog'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useAddDialog } from '../../hooks/useAddDialog'
import { useQueryState } from '../../hooks/queryState'

/**
 * Settings-style CRUD screen for vehicles (F024 spec): table with name, inline rename, add-new
 * dialog, delete. Same pattern as PaymentMethodsPage - a flat, name-only taxonomy, no type column/
 * picker.
 *
 * Responsive (F021): Name is the only data column, so per the spec's 1-2-column rule this stays a
 * plain table at every size, same reasoning as `PaymentMethodsPage`.
 */
export function VehiclesPage() {
  const { t } = useTranslation(['vehicles', 'common'])
  const [error, setError] = useState<string | null>(null)
  const vehiclesQuery = useVehicles()
  const vehicles = vehiclesQuery.data
  const vehiclesState = useQueryState(vehiclesQuery, setError)
  const createMutation = useCreateVehicle()
  const renameMutation = useRenameVehicle()
  const deleteMutation = useDeleteVehicle()

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingName, setEditingName] = useState('')

  const [adding, setAdding] = useState(false)
  const addDialog = useAddDialog('')

  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  function closeAddDialog() {
    addDialog.close()
    setError(null)
  }

  async function handleAdd() {
    if (!addDialog.draft.trim()) return
    setError(null)
    setAdding(true)
    try {
      await createMutation.mutateAsync({ name: addDialog.draft.trim() })
      addDialog.close()
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  function startEdit(vehicle: Vehicle) {
    setEditingId(vehicle.id)
    setEditingName(vehicle.name)
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
    vehiclesState.reload()
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
          {t('vehicles:title')}
        </Typography>
        <Button variant="contained" onClick={addDialog.show}>
          {t('vehicles:addButton')}
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('vehicles:description')}
      </Typography>

      {/* While the add dialog is open a save error shows inside it: this one sits behind it. */}
      <ErrorAlert message={addDialog.isOpen ? null : error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('common:actions.name')}</TableCell>
                <TableCell align="right">{t('common:actions.actions')}</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={vehiclesState} onRetry={retry} columns={2} actionsColumn>
              {vehicles?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2} align="center">
                    <Typography color="text.secondary">{t('vehicles:emptyList')}</Typography>
                  </TableCell>
                </TableRow>
              )}
              {vehicles?.map((vehicle) => (
                <TableRow key={vehicle.id}>
                  <TableCell>
                    {editingId === vehicle.id ? (
                      <TextField
                        size="small"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        slotProps={{ htmlInput: { maxLength: VEHICLE_NAME_MAX_LENGTH } }}
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') void saveEdit(vehicle.id)
                          if (e.key === 'Escape') cancelEdit()
                        }}
                      />
                    ) : (
                      vehicle.name
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <InlineEditActions
                      editing={editingId === vehicle.id}
                      onEdit={() => startEdit(vehicle)}
                      onSave={() => void saveEdit(vehicle.id)}
                      onCancel={cancelEdit}
                      editLabel={t('common:actions.rename')}
                    />
                    {editingId !== vehicle.id && (
                      <IconButton
                        size="small"
                        aria-label={t('common:actions.delete')}
                        disabled={pendingDeleteId === vehicle.id}
                        onClick={() => void handleDelete(vehicle.id)}
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

      <AddFormDialog
        open={addDialog.isOpen}
        title={t('vehicles:addDialogTitle')}
        error={error}
        onDismissError={() => setError(null)}
        busy={adding}
        canSubmit={!!addDialog.draft.trim()}
        columns={1}
        onSubmit={() => void handleAdd()}
        onCancel={closeAddDialog}
      >
        <TextField
          label={t('common:actions.name')}
          size="small"
          value={addDialog.draft}
          onChange={(e) => addDialog.setDraft(e.target.value)}
          slotProps={{ htmlInput: { maxLength: VEHICLE_NAME_MAX_LENGTH } }}
          autoFocus
          onKeyDown={(e) => {
            if (e.key === 'Enter') void handleAdd()
          }}
        />
      </AddFormDialog>
    </Box>
  )
}
