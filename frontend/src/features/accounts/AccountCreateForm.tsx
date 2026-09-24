import { useState } from 'react'
import { Box, Button, MenuItem, Select, TextField } from '@mui/material'
import type { Account, AccountType } from '../../api/accounts'
import { useCreateAccount } from '../../api/accountsQueries'
import { defaultErrorMessage } from '../../api/apiError'
import { today } from '../../utils/localDate'
import { InstitutionSelect } from '../institutions/InstitutionSelect'
import { ACCOUNT_TYPE_LABELS } from './accountTypes'

interface AccountCreateFormProps {
  /** Called with the created account once the backend accepted it. Lists refetch on their own. */
  onCreated?: (account: Account) => void
  /** Called with a message when creation fails, and with `null` when a new attempt starts. */
  onError: (message: string | null) => void
  submitLabel?: string
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

  return (
    <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}>
      <TextField label="Name" size="small" value={name} onChange={(e) => setName(e.target.value)} />
      <InstitutionSelect value={institutionId} onChange={setInstitutionId} />
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
      <Button
        variant="contained"
        disabled={adding || !name.trim() || !institutionId}
        onClick={() => void handleAdd()}
      >
        {submitLabel}
      </Button>
    </Box>
  )
}
