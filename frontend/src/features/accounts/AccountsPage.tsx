import { useMemo, useState } from 'react'
import {
  Box,
  Chip,
  FormControlLabel,
  IconButton,
  Link as MuiLink,
  Paper,
  Switch,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import LockIcon from '@mui/icons-material/Lock'
import type { Account } from '../../api/accounts'
import { useAccounts, useCloseAccount, useEditAccount } from '../../api/accountsQueries'
import { defaultErrorMessage } from '../../api/apiError'
import { useInstitutions } from '../../api/institutionsQueries'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorAlert } from '../../components/ErrorAlert'
import { InlineEditActions } from '../../components/InlineEditActions'
import { DataTableBody } from '../../components/DataTableBody'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { nameLookup } from '../../utils/nameLookup'
import { ACCOUNT_TYPE_LABELS } from './accountTypes'
import { InstitutionSelect } from '../institutions/InstitutionSelect'
import { AccountCreateForm } from './AccountCreateForm'

/**
 * Account list/management screen (F003 spec): table with name, institution, type, running
 * balance; a toggle to show/hide closed accounts (default: hide, PRD S5.4); inline rename of
 * name/institution (the only editable fields - type and opening balance/date are immutable once
 * an account exists, so they render as plain text, never an input, in this table); an add-account
 * form; and a close action gated behind a non-reversible confirmation dialog.
 */
export function AccountsPage() {
  const [includeClosed, setIncludeClosed] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const accountsQuery = useAccounts(includeClosed)
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const institutionsQuery = useInstitutions()
  const institutions = institutionsQuery.data
  const institutionsState = useQueryState(institutionsQuery, setError)
  const editMutation = useEditAccount()
  const closeMutation = useCloseAccount()
  const institutionName = useMemo(
    () => nameLookup(institutions ?? [], (i) => i.name),
    [institutions],
  )

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingName, setEditingName] = useState('')
  const [editingInstitutionId, setEditingInstitutionId] = useState<string | undefined>()

  const [closeTarget, setCloseTarget] = useState<Account | null>(null)
  const [closing, setClosing] = useState(false)

  function startEdit(account: Account) {
    setEditingId(account.id)
    setEditingName(account.name)
    setEditingInstitutionId(account.institutionId)
  }

  function cancelEdit() {
    setEditingId(null)
    setEditingName('')
    setEditingInstitutionId(undefined)
  }

  async function saveEdit(id: string) {
    if (!editingName.trim() || !editingInstitutionId) return
    setError(null)
    try {
      await editMutation.mutateAsync({
        id,
        name: editingName.trim(),
        institutionId: editingInstitutionId,
      })
      cancelEdit()
    } catch (err) {
      setError(defaultErrorMessage(err))
    }
  }

  async function confirmClose() {
    if (!closeTarget) return
    setError(null)
    setClosing(true)
    try {
      await closeMutation.mutateAsync(closeTarget.id)
      setCloseTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setClosing(false)
    }
  }

  // One load state for the table plus the institutions behind its Institution column: rows show
  // only once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(institutionsState, accountsState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  return (
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Accounts
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Checking, savings, cash, credit card, and investment accounts. Opening balance/date and type
        are fixed once an account is created - name and institution can still be corrected any time.
        An investment account has no opening balance: its value comes from snapshots of its
        products.
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <FormControlLabel
        control={
          <Switch checked={includeClosed} onChange={(e) => setIncludeClosed(e.target.checked)} />
        }
        label="Show closed accounts"
        sx={{ mb: 2 }}
      />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Institution</TableCell>
                <TableCell>Type</TableCell>
                <TableCell align="right">Balance</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={tableState} onRetry={retry} columns={6} actionsColumn>
              {accounts?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    <Typography color="text.secondary">No accounts yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
              {accounts?.map((account) => (
                <TableRow key={account.id}>
                  <TableCell>
                    {editingId === account.id ? (
                      <TextField
                        size="small"
                        label="Name"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') void saveEdit(account.id)
                          if (e.key === 'Escape') cancelEdit()
                        }}
                      />
                    ) : (
                      <MuiLink
                        component={RouterLink}
                        to={`/accounts/${account.id}`}
                        underline="hover"
                      >
                        {account.name}
                      </MuiLink>
                    )}
                  </TableCell>
                  <TableCell>
                    {editingId === account.id ? (
                      <InstitutionSelect
                        value={editingInstitutionId}
                        onChange={setEditingInstitutionId}
                      />
                    ) : (
                      institutionName(account.institutionId)
                    )}
                  </TableCell>
                  <TableCell>{ACCOUNT_TYPE_LABELS[account.type]}</TableCell>
                  <TableCell align="right">{account.balance.toFixed(2)}</TableCell>
                  <TableCell>
                    {account.closed ? (
                      <Chip label="Closed" size="small" />
                    ) : (
                      <Chip label="Open" size="small" color="success" />
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <InlineEditActions
                      editing={editingId === account.id}
                      onEdit={() => startEdit(account)}
                      onSave={() => void saveEdit(account.id)}
                      onCancel={cancelEdit}
                      editLabel="Edit"
                    />
                    {editingId !== account.id && (
                      <IconButton
                        size="small"
                        aria-label="Close"
                        disabled={account.closed}
                        onClick={() => setCloseTarget(account)}
                      >
                        <LockIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, maxWidth: 640 }}>
        <Typography variant="subtitle1" gutterBottom>
          Add account
        </Typography>
        <AccountCreateForm onError={setError} />
      </Paper>

      <ConfirmDialog
        open={closeTarget !== null}
        title={`Close ${closeTarget?.name}?`}
        body={
          <>
            Closing an account is not reversible through this app - there is no "reopen" action. The
            account will drop out of "create new" pickers and the live balances view, but its
            history stays visible. Any recurring bills posting to this account will stop generating
            new occurrences.
          </>
        }
        confirmLabel="Close account"
        loading={closing}
        onConfirm={() => void confirmClose()}
        onCancel={() => setCloseTarget(null)}
      />
    </Box>
  )
}
