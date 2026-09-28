import type { PropsWithChildren } from 'react'
import { Box, Paper, Typography } from '@mui/material'
import { currentMonth } from '../../utils/localDate'
import { BudgetVsActualReport } from '../budgets/BudgetVsActualReport'
import { InvestmentAllocationChart } from '../investments/InvestmentAllocationChart'
import { NetWorthTrendChart } from '../netWorth/NetWorthTrendChart'
import { PendingOccurrencesWidget } from '../recurringTemplates/PendingOccurrencesWidget'
import { AccountBalancesWidget } from './AccountBalancesWidget'
import { SpendByCategoryWidget } from './SpendByCategoryWidget'

function Section({ title, children }: PropsWithChildren<{ title: string }>) {
  return (
    <Box component="section" aria-label={title}>
      <Typography variant="h5" component="h2" gutterBottom>
        {title}
      </Typography>
      {children}
    </Box>
  )
}

/**
 * The dashboard (F012, PRD S6.8): a fixed grid that only composes widgets other features own -
 * each fetches its own data, so there is no dashboard endpoint (see F012 spec). Nothing here
 * coordinates refreshes: a confirmed pending occurrence (which creates a transaction) or any other
 * successful write refetches every widget through the global query invalidation (F019).
 */
export function DashboardPage() {
  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Dashboard
      </Typography>

      {/* Fluid widget grid (F021 spec): 1 column below `sm`, 2 from `sm` to `lg`, 3 from `lg` up -
          matching the tablet/desktop bands `useBreakpointBand` reports. The two wide sections
          (Net worth, Pending occurrences) span every column at both multi-column widths. */}
      <Box
        sx={{
          display: 'grid',
          gap: 3,
          gridTemplateColumns: {
            xs: '1fr',
            sm: 'repeat(2, minmax(0, 1fr))',
            lg: 'repeat(3, minmax(0, 1fr))',
          },
        }}
      >
        <Section title="Spend by category">
          <Paper variant="outlined" sx={{ p: 2 }}>
            <SpendByCategoryWidget />
          </Paper>
        </Section>

        <Section title="Budget vs. actual">
          <BudgetVsActualReport month={currentMonth()} />
        </Section>

        <Section title="Account balances">
          <Paper variant="outlined" sx={{ p: 1 }}>
            <AccountBalancesWidget />
          </Paper>
        </Section>

        <Section title="Investment allocation">
          <Paper variant="outlined" sx={{ p: 2 }}>
            <InvestmentAllocationChart />
          </Paper>
        </Section>

        <Box sx={{ gridColumn: { sm: '1 / -1' } }}>
          <Section title="Net worth">
            <Paper variant="outlined" sx={{ p: 2 }}>
              <NetWorthTrendChart />
            </Paper>
          </Section>
        </Box>

        <Box sx={{ gridColumn: { sm: '1 / -1' } }}>
          <PendingOccurrencesWidget />
        </Box>
      </Box>
    </Box>
  )
}
