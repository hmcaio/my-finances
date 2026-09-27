import { useMemo } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  Box,
  Link as MuiLink,
  Paper,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'
import { InvestmentAllocationChart } from './InvestmentAllocationChart'

/** Tighter section padding on phones (F021), matching `AccountDetailPage`. */
const SECTION_PADDING = { xs: 2, sm: 3 }

/**
 * The Investments page (F009 spec): the allocation chart with its category -> sub-category
 * drill-down, and the investment accounts whose detail pages hold the products, snapshots and
 * trades.
 *
 * Responsive (F021): section padding tightens on phones; the page already stacks in one column at
 * every size. The investment-accounts table has only two data columns (Account, Value), so per the
 * 1-2-column rule it stays a plain table rather than becoming a `ResponsiveTable`.
 */
export function InvestmentsPage() {
  const accountsQuery = useAccounts()
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery)
  const investmentAccounts = useMemo(
    () => (accounts ?? []).filter((a) => a.type === 'INVESTMENT'),
    [accounts],
  )

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Investments
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Where your money is invested, from the latest snapshot of each product. Buy and sell from a
        product's page (or the Transfers page); its value only changes when you record a snapshot.
      </Typography>

      <Paper variant="outlined" sx={{ p: SECTION_PADDING, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Allocation
        </Typography>
        <InvestmentAllocationChart />
      </Paper>

      <Paper variant="outlined" sx={{ p: SECTION_PADDING }}>
        <Typography variant="h6" gutterBottom>
          Investment accounts
        </Typography>
        <TableContainer>
          <Table size="small" aria-label="Investment accounts">
            <TableHead>
              <TableRow>
                <TableCell>Account</TableCell>
                <TableCell align="right">Value</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={accountsState} onRetry={accountsState.reload} columns={2}>
              {investmentAccounts.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2} align="center">
                    <Typography color="text.secondary">
                      No investment accounts yet - add one on the Accounts page.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {investmentAccounts.map((account) => (
                <TableRow key={account.id}>
                  <TableCell>
                    <MuiLink
                      component={RouterLink}
                      to={`/accounts/${account.id}`}
                      underline="hover"
                    >
                      {account.name}
                    </MuiLink>
                  </TableCell>
                  <TableCell align="right">{account.balance.toFixed(2)}</TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  )
}
