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
import { AccountTransactionList } from '../transactions/AccountTransactionList'
import { AccountTransferList } from '../transfers/AccountTransferList'

const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CHECKING: 'Checking',
  SAVINGS: 'Savings',
  CASH_WALLET: 'Cash Wallet',
  CREDIT_CARD: 'Credit Card',
}

/**
 * Account detail view (F003 spec): running balance plus account fields, plus F004's transaction
 * history and F005's transfer history, both embedded and pre-filtered to this account (F004 spec:
 * "Account detail view (F003) embeds this feature's list, pre-filtered to that account"; F005
 * spec: "Account detail view (F003) embeds transfer history alongside transaction history, both
 * contributing to the same running-balance timeline").
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

          <Paper variant="outlined" sx={{ p: 3, mb: 3 }}>
            <Typography variant="h6" gutterBottom>
              Transactions
            </Typography>
            <AccountTransactionList accountId={account.id} />
          </Paper>

          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Transfers
            </Typography>
            <AccountTransferList accountId={account.id} />
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
