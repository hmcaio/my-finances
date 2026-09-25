import { useMemo } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { Alert, Box, Chip, Link as MuiLink, Paper, Skeleton, Typography } from '@mui/material'
import { useAccount } from '../../api/accountsQueries'
import { defaultErrorMessage } from '../../api/apiError'
import { useInstitutions } from '../../api/institutionsQueries'
import { fadeInSx } from '../../components/fadeIn'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { nameLookup } from '../../utils/nameLookup'
import { ACCOUNT_TYPE_LABELS } from './accountTypes'
import { InvestmentProductsSection } from '../investmentProducts/InvestmentProductsSection'
import { AccountTransactionList } from '../transactions/AccountTransactionList'
import { AccountTransferList } from '../transfers/AccountTransferList'

/**
 * Account detail view (F003 spec): running balance plus account fields, plus F004's transaction
 * history and F005's transfer history, both embedded and pre-filtered to this account (F004 spec:
 * "Account detail view (F003) embeds this feature's list, pre-filtered to that account"; F005
 * spec: "Account detail view (F003) embeds transfer history alongside transaction history, both
 * contributing to the same running-balance timeline").
 */
export function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const accountQuery = useAccount(id)
  const account = accountQuery.data
  const accountState = useQueryState(accountQuery, undefined, (err) =>
    defaultErrorMessage(err, { 404: 'Account not found.' }),
  )
  const institutionsQuery = useInstitutions()
  const institutions = institutionsQuery.data
  const institutionsState = useQueryState(institutionsQuery)
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
              {account.openingBalance !== null && account.openingBalanceDate !== null
                ? `Opening balance ${account.openingBalance.toFixed(2)} as of ${account.openingBalanceDate}`
                : 'No opening balance: its value comes from snapshots of its products'}
              {account.closedDate ? ` · Closed ${account.closedDate}` : ''}
            </Typography>
          </Paper>

          {account.type === 'INVESTMENT' ? (
            // An investment account takes no transactions (money moves through transfers), so its
            // products are listed instead (F008 spec).
            <Paper variant="outlined" sx={{ p: 3, mb: 3 }}>
              <Typography variant="h6" gutterBottom>
                Products
              </Typography>
              <InvestmentProductsSection accountId={account.id} accountClosed={account.closed} />
            </Paper>
          ) : (
            <Paper variant="outlined" sx={{ p: 3, mb: 3 }}>
              <Typography variant="h6" gutterBottom>
                Transactions
              </Typography>
              <AccountTransactionList accountId={account.id} />
            </Paper>
          )}

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
