import { useMemo } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { Alert, Box, Chip, Link as MuiLink, Paper, Skeleton, Typography } from '@mui/material'
import { getAccount, type AccountType } from '../../api/accounts'
import { defaultErrorMessage } from '../../api/apiError'
import { getInstitutions } from '../../api/institutions'
import { fadeInSx } from '../../components/fadeIn'
import { combineLoadState, useAsyncData } from '../../hooks/useAsyncData'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { nameLookup } from '../../utils/nameLookup'
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
  const { data: account, ...accountState } = useAsyncData(
    () => (id ? getAccount(id) : Promise.reject(new Error('Missing account id.'))),
    [id],
    { errorMessage: (err) => defaultErrorMessage(err, { 404: 'Account not found.' }) },
  )
  const { data: institutions, ...institutionsState } = useAsyncData(getInstitutions, [])
  const institutionName = useMemo(
    () => nameLookup(institutions ?? [], (i) => i.name),
    [institutions],
  )
  // The header names the institution, so the page stays in its skeleton until both have loaded.
  const { loading, loadError: error } = combineLoadState(accountState, institutionsState)
  const showSkeleton = useDelayedFlag(loading)

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

      {showSkeleton && <AccountDetailSkeleton />}

      {account && institutions && (
        <Box sx={fadeInSx}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2, mb: 1 }}>
            <Typography variant="h4" component="h1">
              {account.name}
            </Typography>
            {account.closed ? <Chip label="Closed" /> : <Chip label="Open" color="success" />}
          </Box>
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            {institutionName(account.institutionId)} &middot; {ACCOUNT_TYPE_LABELS[account.type]}
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
        </Box>
      )}
    </Box>
  )
}

/** Placeholder laid out like the loaded page: header, subtitle, balance card, two list sections. */
function AccountDetailSkeleton() {
  return (
    <Box role="status" aria-label="Loading account">
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2, mb: 1 }}>
        <Skeleton variant="text" width={240} sx={{ typography: 'h4' }} />
        <Skeleton variant="rounded" width={60} height={32} />
      </Box>
      <Skeleton variant="text" width={200} sx={{ mb: 3 }} />

      <Paper variant="outlined" sx={{ p: 3, mb: 3, maxWidth: 480 }}>
        <Skeleton variant="text" width={110} sx={{ typography: 'overline' }} />
        <Skeleton variant="text" width={180} sx={{ typography: 'h3', mb: 2 }} />
        <Skeleton variant="text" width="80%" sx={{ typography: 'body2' }} />
      </Paper>

      {[0, 1].map((section) => (
        <Paper key={section} variant="outlined" sx={{ p: 3, mb: section === 0 ? 3 : 0 }}>
          <Skeleton variant="text" width={120} sx={{ typography: 'h6', mb: 1 }} />
          <Skeleton variant="rounded" height={160} />
        </Paper>
      ))}
    </Box>
  )
}
