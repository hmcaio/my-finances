import type { PropsWithChildren } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Paper, Typography, type SxProps, type Theme } from '@mui/material'
import { currentMonth } from '../../utils/localDate'
import { BudgetVsActualReport } from '../budgets/BudgetVsActualReport'
import { InvestmentAllocationChart } from '../investments/overview/components/InvestmentAllocationChart'
import { NetWorthTrendChart } from '../netWorth/NetWorthTrendChart'
import { PendingOccurrencesWidget } from '../recurringTemplates/PendingOccurrencesWidget'
import { AccountBalancesWidget } from './AccountBalancesWidget'
import { GoalsWidget } from './GoalsWidget'
import { SpendByCategoryWidget } from './SpendByCategoryWidget'
import { PageContainer } from '../../components/layout/PageContainer'

/**
 * A titled dashboard section. It is a flex column so its card can grow to the height of its grid
 * row (the cards of one row end level, whatever each holds); `sx` places it in the grid.
 */
function Section({
  title,
  sx,
  children,
}: PropsWithChildren<{ title: string; sx?: SxProps<Theme> }>) {
  return (
    <Box
      component="section"
      aria-label={title}
      sx={[{ display: 'flex', flexDirection: 'column' }, ...(Array.isArray(sx) ? sx : [sx])]}
    >
      <Typography variant="h5" component="h2" gutterBottom>
        {title}
      </Typography>
      {children}
    </Box>
  )
}

/** The outlined card every widget sits in; it grows to fill the rest of its `Section`. */
function Card({ padding = 2, children }: PropsWithChildren<{ padding?: number }>) {
  return (
    <Paper variant="outlined" sx={{ p: padding, flexGrow: 1 }}>
      {children}
    </Paper>
  )
}

/**
 * The dashboard (F012, PRD S6.8): a fixed grid that only composes widgets other features own -
 * each fetches its own data, so there is no dashboard endpoint (see F012 spec). Nothing here
 * coordinates refreshes: a confirmed pending occurrence (which creates a transaction) or any other
 * successful write refetches every widget through the global query invalidation (F019).
 */
export function DashboardPage() {
  const { t } = useTranslation('dashboard')
  return (
    <PageContainer>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('title')}
      </Typography>

      {/* Fluid widget grid (F021 spec): 1 column below `sm`, 2 from `sm` to `lg`, 3 from `lg` up -
          matching the tablet/desktop bands `useBreakpointBand` reports. Three bands at `lg`: spend,
          budget and balances; investment allocation beside a double-width net worth; then the
          full-width goals card (F039, only while a goal is flagged) and pending occurrences. Each
          band's cards end level (see `Section`). The goals card spans its own row from its own
          root element, so with no goal flagged it leaves neither a cell nor a row gap. */}
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
        <Section title={t('sections.spendByCategory')}>
          <Card>
            <SpendByCategoryWidget />
          </Card>
        </Section>

        <Section title={t('sections.budgetVsActual')}>
          <BudgetVsActualReport month={currentMonth()} sx={{ flexGrow: 1 }} />
        </Section>

        <Section title={t('sections.accountBalances')}>
          <Card padding={1}>
            <AccountBalancesWidget />
          </Card>
        </Section>

        <Section title={t('sections.investmentAllocation')}>
          <Card>
            <InvestmentAllocationChart />
          </Card>
        </Section>

        <Section title={t('sections.netWorth')} sx={{ gridColumn: { sm: '1 / -1', lg: 'span 2' } }}>
          <Card>
            <NetWorthTrendChart />
          </Card>
        </Section>

        {/* F039: renders nothing, heading included, while no goal is flagged for the dashboard. */}
        <GoalsWidget />

        <Box sx={{ gridColumn: { sm: '1 / -1' } }}>
          <PendingOccurrencesWidget />
        </Box>
      </Box>
    </PageContainer>
  )
}
