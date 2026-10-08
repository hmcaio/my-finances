import { useMemo, useState, type SyntheticEvent } from 'react'
import { Box, Button, Grid, MenuItem, Select, Tab, Tabs, Typography } from '@mui/material'
import { useInvestmentCategories } from '../../api/investments/investmentCategoriesQueries'
import { useFiiPortfolio } from '../../api/investments/fiiPortfolioQueries'
import { useInvestmentProducts } from '../../api/investments/investmentProductsQueries'
import type { InvestmentProductStatus } from '../../api/investments/investmentProducts'
import { useQueryState } from '../../hooks/queryState'
import { MonthPicker } from '../../components/inputs/MonthPicker'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { currentMonth } from '../../utils/localDate'
import { AllocationPlanEditor } from './AllocationPlanEditor'
import { DividendHistorySection } from './DividendHistorySection'
import { FiiAllocationDonutChart } from './FiiAllocationDonutChart'
import { RegisterDividendDialog } from './RegisterDividendDialog'
import type { FiiPortfolioRow } from '../../api/investments/fiiPortfolio'

/** The sub-category name every FII product is classified under (mirrors `AllocationPlanService`). */
const FII_SUBCATEGORY_NAME = 'REITs (FIIs)'

type FiiTab = 'allocation' | 'dividends'

/**
 * The FII portfolio page (F026 spec, ADR 0023): the four allocation pie charts (actual/planned x
 * ticker/segment) at the top, then the portfolio list (one row per FII, closed holdings hidden by
 * default) with its month/status filters, then two sub-tabs - "Dividends" (default; dividend
 * history - the register-dividend dialog trigger lives in the header, not this tab) and "Allocation
 * Plan" (the target-allocation editor) - mirroring F023's Investments-page layout (charts above
 * tabs). The month picker (Addendum: Month Selector) drives the portfolio list and all four charts,
 * defaulting to (and clamped to) the current month; the `needsSnapshot` badge is hidden whenever a
 * past month is selected, since it's a "today" concept the backend never recomputes against a past
 * date.
 */
export function FiiPage() {
  const categoriesQuery = useInvestmentCategories()
  const categories = categoriesQuery.data
  const productsQuery = useInvestmentProducts()
  const products = productsQuery.data

  const fiiSubcategoryId = useMemo(
    () =>
      (categories ?? [])
        .flatMap((c) => c.subcategories)
        .find((s) => s.name === FII_SUBCATEGORY_NAME)?.id,
    [categories],
  )
  const fiiProducts = useMemo(
    () => (products ?? []).filter((p) => p.investmentSubcategoryId === fiiSubcategoryId),
    [products, fiiSubcategoryId],
  )

  const [status, setStatus] = useState<InvestmentProductStatus>('OPEN')
  const [month, setMonth] = useState(currentMonth())
  const isCurrentMonth = month === currentMonth()
  const portfolioQuery = useFiiPortfolio(status, month)
  const portfolio = portfolioQuery.data
  const portfolioState = useQueryState(portfolioQuery)

  const [registerOpen, setRegisterOpen] = useState(false)
  const [tab, setTab] = useState<FiiTab>('dividends')

  function handleTabChange(_: SyntheticEvent, value: FiiTab) {
    setTab(value)
  }

  const columns: ResponsiveColumn<FiiPortfolioRow>[] = [
    { key: 'ticker', header: 'Ticker', render: (r) => r.ticker ?? '-', role: 'primary' },
    { key: 'name', header: 'Name', render: (r) => r.name, role: 'secondary' },
    {
      key: 'cotas',
      header: 'Cotas held',
      render: (r) => r.cotasHeld.toFixed(0),
      align: 'right',
      tabletPriority: 'low',
    },
    {
      key: 'contributed',
      header: 'Contributed',
      render: (r) => r.amountContributed.toFixed(2),
      align: 'right',
      tabletPriority: 'low',
    },
    {
      key: 'value',
      header: 'Current value',
      render: (r) => `${r.currentValue.toFixed(2)}${r.needsSnapshot && isCurrentMonth ? ' *' : ''}`,
      align: 'right',
    },
    {
      key: 'snapshot',
      header: 'Latest snapshot',
      render: (r) => r.latestSnapshotDate ?? 'None yet',
      align: 'right',
      tabletPriority: 'low',
    },
  ]

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          mb: 1,
        }}
      >
        <Typography variant="h4" component="h1">
          FII Portfolio
        </Typography>
        <Button variant="contained" onClick={() => setRegisterOpen(true)}>
          Register dividend
        </Button>
      </Box>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        FIIs classified under the "REITs (FIIs)" sub-category: cotas held and amount contributed are
        computed from recorded trades, never price-derived.
      </Typography>

      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
            Actual allocation by ticker
          </Typography>
          <FiiAllocationDonutChart
            basis="ACTUAL"
            groupBy="TICKER"
            month={month}
            ariaLabel="Actual allocation by ticker"
            emptyMessage="No FII value yet."
          />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
            Actual allocation by segment
          </Typography>
          <FiiAllocationDonutChart
            basis="ACTUAL"
            groupBy="SEGMENT"
            month={month}
            ariaLabel="Actual allocation by segment"
            emptyMessage="No FII value yet."
          />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
            Planned allocation by ticker
          </Typography>
          <FiiAllocationDonutChart
            basis="PLANNED"
            groupBy="TICKER"
            month={month}
            ariaLabel="Planned allocation by ticker"
            emptyMessage="No allocation plan set yet."
          />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
            Planned allocation by segment
          </Typography>
          <FiiAllocationDonutChart
            basis="PLANNED"
            groupBy="SEGMENT"
            month={month}
            ariaLabel="Planned allocation by segment"
            emptyMessage="No allocation plan set yet."
          />
        </Grid>
      </Grid>

      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2, mb: 2 }}>
        <MonthPicker label="Month" value={month} onChange={setMonth} clampToCurrentMonth />
        <Select
          size="small"
          value={status}
          onChange={(e) => setStatus(e.target.value as InvestmentProductStatus)}
          aria-label="Status"
        >
          <MenuItem value="OPEN">Open</MenuItem>
          <MenuItem value="CLOSED">Closed</MenuItem>
          <MenuItem value="ALL">All</MenuItem>
        </Select>
      </Box>

      <ResponsiveTable
        columns={columns}
        rows={portfolio}
        getRowKey={(r) => r.productId}
        state={portfolioState}
        emptyMessage="No FII holdings yet."
        aria-label="FII portfolio"
      />

      <Tabs value={tab} onChange={handleTabChange} aria-label="FII views" sx={{ mt: 3 }}>
        <Tab
          value="dividends"
          label="Dividends"
          id="fii-tab-dividends"
          aria-controls="fii-tabpanel-dividends"
        />
        <Tab
          value="allocation"
          label="Allocation Plan"
          id="fii-tab-allocation"
          aria-controls="fii-tabpanel-allocation"
        />
      </Tabs>

      <Box
        role="tabpanel"
        id="fii-tabpanel-dividends"
        aria-labelledby="fii-tab-dividends"
        hidden={tab !== 'dividends'}
        sx={{ mt: 3 }}
      >
        {tab === 'dividends' && <DividendHistorySection fiiProducts={fiiProducts} />}
      </Box>

      <Box
        role="tabpanel"
        id="fii-tabpanel-allocation"
        aria-labelledby="fii-tab-allocation"
        hidden={tab !== 'allocation'}
        sx={{ mt: 3 }}
      >
        {tab === 'allocation' && <AllocationPlanEditor fiiProducts={fiiProducts} />}
      </Box>

      <RegisterDividendDialog
        open={registerOpen}
        onClose={() => setRegisterOpen(false)}
        fiiProducts={fiiProducts}
      />
    </Box>
  )
}
