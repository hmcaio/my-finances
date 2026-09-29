import { useMemo, useState, type SyntheticEvent } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  Box,
  Link as MuiLink,
  Paper,
  Tab,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  Typography,
} from '@mui/material'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'
import { InvestmentAccountAllocationChart } from './InvestmentAccountAllocationChart'
import { InvestmentAllocationChart } from './InvestmentAllocationChart'
import { InvestmentProductsListSection } from './InvestmentProductsListSection'
import { InvestmentSubcategoryAllocationChart } from './InvestmentSubcategoryAllocationChart'

/** Tighter section padding on phones (F021), matching `AccountDetailPage`. */
const SECTION_PADDING = { xs: 2, sm: 3 }

type InvestmentsTab = 'accounts' | 'products'

/**
 * The Investments page (F009 spec, rebuilt into a dashboard by F023): a row of three allocation
 * donuts - category (with its existing click-to-drill into sub-categories, unchanged), sub-category
 * (flat, F023) and account (flat, F023, enabled by F022's holdings) - above two sub-tabs: "Accounts"
 * (today's accounts-value table, unchanged) and "Products" (F023's new global, filtered, paginated
 * product list).
 *
 * Responsive (F021): the three-chart row is a CSS grid, one column below `lg` (tablet and phone both
 * stack, matching `Layout`'s own nav breakpoint) and three across at `lg`+; section padding tightens
 * on phones. The investment-accounts table has only two data columns (Account, Value), so per the
 * 1-2-column rule it stays a plain table rather than becoming a `ResponsiveTable`.
 */
export function InvestmentsPage() {
  const [tab, setTab] = useState<InvestmentsTab>('accounts')
  const accountsQuery = useAccounts()
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery)
  const investmentAccounts = useMemo(
    () => (accounts ?? []).filter((a) => a.type === 'INVESTMENT'),
    [accounts],
  )

  function handleTabChange(_: SyntheticEvent, value: InvestmentsTab) {
    setTab(value)
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Investments
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Where your money is invested, from the latest snapshot of each holding. Buy and sell from a
        product's page (or the Transfers page); its value only changes when you record a snapshot.
      </Typography>

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', lg: 'repeat(3, 1fr)' },
          gap: 3,
          mb: 3,
        }}
      >
        <Paper variant="outlined" sx={{ p: SECTION_PADDING }}>
          <Typography variant="h6" gutterBottom>
            By category
          </Typography>
          <InvestmentAllocationChart />
        </Paper>
        <Paper variant="outlined" sx={{ p: SECTION_PADDING }}>
          <Typography variant="h6" gutterBottom>
            By sub-category
          </Typography>
          <InvestmentSubcategoryAllocationChart />
        </Paper>
        <Paper variant="outlined" sx={{ p: SECTION_PADDING }}>
          <Typography variant="h6" gutterBottom>
            By account
          </Typography>
          <InvestmentAccountAllocationChart />
        </Paper>
      </Box>

      <Tabs value={tab} onChange={handleTabChange} aria-label="Investments views">
        <Tab
          value="accounts"
          label="Accounts"
          id="investments-tab-accounts"
          aria-controls="investments-tabpanel-accounts"
        />
        <Tab
          value="products"
          label="Products"
          id="investments-tab-products"
          aria-controls="investments-tabpanel-products"
        />
      </Tabs>

      <Box
        role="tabpanel"
        id="investments-tabpanel-accounts"
        aria-labelledby="investments-tab-accounts"
        hidden={tab !== 'accounts'}
        sx={{ mt: 3 }}
      >
        {tab === 'accounts' && (
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
        )}
      </Box>

      <Box
        role="tabpanel"
        id="investments-tabpanel-products"
        aria-labelledby="investments-tab-products"
        hidden={tab !== 'products'}
        sx={{ mt: 3 }}
      >
        {tab === 'products' && (
          <Paper variant="outlined" sx={{ p: SECTION_PADDING }}>
            <Typography variant="h6" gutterBottom>
              Products
            </Typography>
            <InvestmentProductsListSection />
          </Paper>
        )}
      </Box>
    </Box>
  )
}
