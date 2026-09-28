import { useState, type ReactNode } from 'react'
import {
  Box,
  Button,
  DialogActions,
  DialogContent,
  MenuItem,
  Select,
  TextField,
} from '@mui/material'
import type { Account, AccountType } from '../../api/accounts/accounts'
import { useCreateAccount } from '../../api/accounts/accountsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { FormGrid } from '../../components/feedback/ResponsiveDialog'
import { today } from '../../utils/localDate'
import { InstitutionSelect } from '../institutions/InstitutionSelect'
import { ACCOUNT_TYPE_LABELS } from './accountTypes'

interface AccountCreateFormProps {
  /** Called with the created account once the backend accepted it. Lists refetch on their own. */
  onCreated?: (account: Account) => void
  /** Called with a message when creation fails, and with `null` when a new attempt starts. */
  onError: (message: string | null) => void
  submitLabel?: string
  /**
   * Lays the form out for a `ResponsiveDialog` (F021): the fields in a one-column-on-phone
   * `FormGrid` inside `DialogContent`, the buttons in `DialogActions`. The caller supplies the
   * dialog and its title. Off (default, the onboarding screen's only remaining inline caller -
   * the account list's Add button uses dialog mode), the same `FormGrid` renders directly on the
   * page with the submit button below it, so the full-page flow also stacks to one column below
   * `sm` instead of just wrapping.
   */
  dialog?: boolean
  /** Dialog mode: shown above the fields (the caller's error banner), so a save error is visible. */
  banner?: ReactNode
  /** Dialog mode: renders a Cancel button. */
  onCancel?: () => void
}

/**
 * The create-account fields and submit button, shared by the account list (F003) and the
 * onboarding screen (F011). Institution is mandatory but preselected to the built-in "No
 * institution" row (F017); an INVESTMENT account has no opening balance or date (its value comes
 * from snapshots). The caller owns error display and what happens to the created account.
 */
export function AccountCreateForm({
  onCreated,
  onError,
  submitLabel = 'Add',
  dialog = false,
  banner,
  onCancel,
}: AccountCreateFormProps) {
  const [name, setName] = useState('')
  // `undefined` until InstitutionSelect reports its default (the built-in "No institution" row).
  const [institutionId, setInstitutionId] = useState<string | undefined>()
  const [type, setType] = useState<AccountType>('CHECKING')
  const [openingBalance, setOpeningBalance] = useState('0')
  const [openingBalanceDate, setOpeningBalanceDate] = useState(today)
  const [adding, setAdding] = useState(false)
  const createMutation = useCreateAccount()

  async function handleAdd() {
    if (!name.trim() || !institutionId) return
    onError(null)
    setAdding(true)
    try {
      const created = await createMutation.mutateAsync({
        name: name.trim(),
        institutionId,
        type,
        ...(type === 'INVESTMENT'
          ? {}
          : { openingBalance: Number(openingBalance), openingBalanceDate }),
      })
      setName('')
      setInstitutionId(undefined)
      setType('CHECKING')
      setOpeningBalance('0')
      onCreated?.(created)
    } catch (err) {
      onError(defaultErrorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  const fields = (
    <>
      <TextField
        label="Name"
        size="small"
        value={name}
        onChange={(e) => setName(e.target.value)}
        sx={{ gridColumn: '1 / -1' }}
      />
      <InstitutionSelect value={institutionId} onChange={setInstitutionId} fullWidth />
      <Select
        size="small"
        value={type}
        onChange={(e) => setType(e.target.value as AccountType)}
        aria-label="Account type"
      >
        {Object.entries(ACCOUNT_TYPE_LABELS).map(([value, label]) => (
          <MenuItem key={value} value={value}>
            {label}
          </MenuItem>
        ))}
      </Select>
      {type !== 'INVESTMENT' && (
        <>
          <TextField
            label="Opening Balance"
            size="small"
            type="number"
            value={openingBalance}
            onChange={(e) => setOpeningBalance(e.target.value)}
            slotProps={{ htmlInput: { step: '0.01' } }}
          />
          <TextField
            label="Opening Balance Date"
            size="small"
            type="date"
            value={openingBalanceDate}
            onChange={(e) => setOpeningBalanceDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </>
      )}
    </>
  )
  const submitButton = (
    <Button
      variant="contained"
      disabled={adding || !name.trim() || !institutionId}
      onClick={() => void handleAdd()}
    >
      {submitLabel}
    </Button>
  )

  if (dialog) {
    return (
      <>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            {banner}
            <FormGrid>{fields}</FormGrid>
          </Box>
        </DialogContent>
        <DialogActions>
          {onCancel && (
            <Button onClick={onCancel} disabled={adding}>
              Cancel
            </Button>
          )}
          {submitButton}
        </DialogActions>
      </>
    )
  }

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        alignItems: { xs: 'stretch', sm: 'flex-start' },
      }}
    >
      <FormGrid>{fields}</FormGrid>
      {submitButton}
    </Box>
  )
}
