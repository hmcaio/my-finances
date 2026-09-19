import { useState } from 'react'
import {
  Box,
  Button,
  Chip,
  FormControlLabel,
  IconButton,
  Link as MuiLink,
  MenuItem,
  Paper,
  Select,
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
import {
  closeAccount,
  createAccount,
  editAccount,
  getAccounts,
  type Account,
  type AccountType,
} from '../../api/accounts'
import { defaultErrorMessage } from '../../api/apiError'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorAlert } from '../../components/ErrorAlert'
import { InlineEditActions } from '../../components/InlineEditActions'
import { DataTableBody } from '../../components/DataTableBody'
import { useAsyncData } from '../../hooks/useAsyncData'

const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CHECKING: 'Checking',
  SAVINGS: 'Savings',
  CASH_WALLET: 'Cash Wallet',
  CREDIT_CARD: 'Credit Card',
}

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
  const {
    data: accounts,
    setData: setAccounts,
    ...accountsState
  } = useAsyncData(() => getAccounts(includeClosed), [includeClosed], { onError: setError })

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingName, setEditingName] = useState('')
  const [editingInstitution, setEditingInstitution] = useState('')

  const [newName, setNewName] = useState('')
  const [newInstitution, setNewInstitution] = useState('')
  const [newType, setNewType] = useState<AccountType>('CHECKING')
  const [newOpeningBalance, setNewOpeningBalance] = useState('0')
  const [newOpeningBalanceDate, setNewOpeningBalanceDate] = useState(() =>
    new Date().toISOString().slice(0, 10),
  )
  const [adding, setAdding] = useState(false)

  const [closeTarget, setCloseTarget] = useState<Account | null>(null)
  const [closing, setClosing] = useState(false)

  async function handleAdd() {
    if (!newName.trim()) return
    setError(null)
    setAdding(true)
    try {
      const created = await createAccount({
        name: newName.trim(),
        institution: newInstitution.trim() || undefined,
        type: newType,
        openingBalance: Number(newOpeningBalance),
        openingBalanceDate: newOpeningBalanceDate,
      })
      setAccounts((prev) => [...(prev ?? []), created])
      setNewName('')
      setNewInstitution('')
      setNewType('CHECKING')
      setNewOpeningBalance('0')
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  function startEdit(account: Account) {
    setEditingId(account.id)
    setEditingName(account.name)
    setEditingInstitution(account.institution ?? '')
  }

  function cancelEdit() {
    setEditingId(null)
    setEditingName('')
    setEditingInstitution('')
  }

  async function saveEdit(id: string) {
    if (!editingName.trim()) return
    setError(null)
    try {
      const updated = await editAccount(id, {
        name: editingName.trim(),
        institution: editingInstitution.trim() || undefined,
      })
      setAccounts((prev) => prev?.map((a) => (a.id === id ? updated : a)) ?? null)
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
      const closed = await closeAccount(closeTarget.id)
      setAccounts((prev) =>
        includeClosed
          ? (prev?.map((a) => (a.id === closed.id ? closed : a)) ?? null)
          : (prev?.filter((a) => a.id !== closed.id) ?? null),
      )
      setCloseTarget(null)
    } catch (err) {
      setError(defaultErrorMessage(err))
    } finally {
      setClosing(false)
    }
  }

  return (
    <Box sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Accounts
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Checking, savings, cash, and credit card accounts. Opening balance/date and type are fixed
        once an account is created - name and institution can still be corrected any time.
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
            <DataTableBody state={accountsState} columns={6} actionsColumn>
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
                      <TextField
                        size="small"
                        label="Institution"
                        value={editingInstitution}
                        onChange={(e) => setEditingInstitution(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') void saveEdit(account.id)
                          if (e.key === 'Escape') cancelEdit()
                        }}
                      />
                    ) : (
                      (account.institution ?? '—')
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
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <TextField
            label="Name"
            size="small"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
          <TextField
            label="Institution"
            size="small"
            value={newInstitution}
            onChange={(e) => setNewInstitution(e.target.value)}
          />
          <Select
            size="small"
            value={newType}
            onChange={(e) => setNewType(e.target.value as AccountType)}
          >
            {Object.entries(ACCOUNT_TYPE_LABELS).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </Select>
          <TextField
            label="Opening Balance"
            size="small"
            type="number"
            value={newOpeningBalance}
            onChange={(e) => setNewOpeningBalance(e.target.value)}
            slotProps={{ htmlInput: { step: '0.01' } }}
          />
          <TextField
            label="Opening Balance Date"
            size="small"
            type="date"
            value={newOpeningBalanceDate}
            onChange={(e) => setNewOpeningBalanceDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
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
