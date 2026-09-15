import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Link as MuiLink,
  Paper,
  Typography,
} from '@mui/material'
import { getAccount, type Account, type AccountType } from '../../api/accounts'
import { ApiError } from '../../api/apiError'

const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CHECKING: 'Checking',
  SAVINGS: 'Savings',
  CASH_WALLET: 'Cash Wallet',
  CREDIT_CARD: 'Credit Card',
}

/**
 * Account detail view (F003 spec): running balance plus account fields. Transaction/transfer
 * history is populated once F004/F005 exist - this page renders a placeholder for it until then,
 * per spec's explicit "populated once F004/F005 exist" note.
 */
export function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [account, setAccount] = useState<Account | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    getAccount(id)
      .then(setAccount)
      .catch((err: unknown) => setError(errorMessage(err)))
  }, [id])

  return (
    <Box sx={{ py: 4 }}>
      <MuiLink component={RouterLink} to="/accounts" underline="hover">
        &larr; Back to accounts
      </MuiLink>

      {error && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {error}
        </Alert>
      )}

      {!error && account === null && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <CircularProgress size={24} />
        </Box>
      )}

      {account && (
        <>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2, mb: 1 }}>
            <Typography variant="h4" component="h1">
              {account.name}
            </Typography>
            {account.closed ? <Chip label="Closed" /> : <Chip label="Open" color="success" />}
          </Box>
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            {account.institution ?? 'No institution'} &middot; {ACCOUNT_TYPE_LABELS[account.type]}
          </Typography>

          <Paper variant="outlined" sx={{ p: 3, mb: 3, maxWidth: 480 }}>
            <Typography variant="overline" color="text.secondary">
              Running balance
            </Typography>
            <Typography variant="h3" sx={{ mb: 2 }}>
              {account.balance.toFixed(2)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Opening balance {account.openingBalance.toFixed(2)} as of {account.openingBalanceDate}
              {account.closedDate ? ` · Closed ${account.closedDate}` : ''}
            </Typography>
          </Paper>

          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Transaction &amp; transfer history
            </Typography>
            <Typography color="text.secondary">
              Not available yet - this view is populated once transactions (F004) and transfers
              (F005) exist.
            </Typography>
          </Paper>
        </>
      )}
    </Box>
  )
}

function errorMessage(err: unknown): string {
  if (err instanceof ApiError && err.status === 404) return 'Account not found.'
  if (err instanceof Error) return err.message
  return 'Something went wrong.'
}
