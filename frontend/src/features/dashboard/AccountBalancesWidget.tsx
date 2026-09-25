import {
  Box,
  Link as MuiLink,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Typography,
} from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { ACCOUNT_TYPE_LABELS } from '../accounts/accountTypes'

/**
 * Balances of every open account (F012 spec, from F003's account list), each linking to its detail
 * page. A credit card's figure is the amount owed, as on the accounts page. A prop-less widget that
 * fetches its own data on mount (frontend `CLAUDE.md`, "Pages that embed other pages' widgets").
 */
export function AccountBalancesWidget() {
  const accountsQuery = useAccounts()
  const accounts = accountsQuery.data
  const { loading, loadError, reload } = useQueryState(accountsQuery)
  const showSkeleton = useDelayedFlag(loading)

  if (loadError) return <LoadFailedNotice message={loadError} onRetry={reload} />
  if (accounts === undefined) {
    return showSkeleton ? (
      <Box role="status" aria-label="Loading account balances">
        {[0, 1, 2].map((row) => (
          <Skeleton key={row} variant="text" sx={{ typography: 'body2' }} />
        ))}
      </Box>
    ) : null
  }
  if (accounts.length === 0) return <Typography color="text.secondary">No accounts yet.</Typography>

  return (
    <Table size="small" sx={fadeInSx}>
      <TableBody>
        {accounts.map((account) => (
          <TableRow key={account.id}>
            <TableCell>
              <MuiLink component={RouterLink} to={`/accounts/${account.id}`} underline="hover">
                {account.name}
              </MuiLink>
            </TableCell>
            <TableCell>{ACCOUNT_TYPE_LABELS[account.type]}</TableCell>
            <TableCell align="right">
              {account.balance.toFixed(2)}
              {account.type === 'CREDIT_CARD' && ' owed'}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
