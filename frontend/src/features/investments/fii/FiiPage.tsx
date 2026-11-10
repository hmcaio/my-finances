import { useMemo, useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, Grid, MenuItem, Select, Tab, Tabs, Typography } from '@mui/material'
import { useInvestmentCategories } from '../../../api/investments/taxonomy/investmentCategoriesQueries'
import { useFiiPortfolio } from '../../../api/investments/fii/fiiPortfolioQueries'
import { useInvestmentProducts } from '../../../api/investments/products/investmentProductsQueries'
import type { InvestmentProductStatus } from '../../../api/investments/products/investmentProducts'
import { useIsMobile } from '../../../hooks/useBreakpointBand'
import { useQueryState } from '../../../hooks/queryState'
import { MonthPicker } from '../../../components/inputs/MonthPicker'
import { ResponsiveTable, type ResponsiveColumn } from '../../../components/table/ResponsiveTable'
import { useLocale } from '../../../providers/locale/useLocale'
import { formatMoney, formatNumber } from '../../../i18n/format'
import { currentMonth } from '../../../utils/localDate'
import { AllocationPlanEditor } from './AllocationPlanEditor'
import { DividendHistorySection } from './DividendHistorySection'
import { FiiAllocationComparison } from './FiiAllocationComparison'
import { FiiNestedAllocationDonutChart } from './FiiNestedAllocationDonutChart'
import { RegisterDividendDialog } from './RegisterDividendDialog'
import type { FiiPortfolioRow } from '../../../api/investments/fii/fiiPortfolio'
import { PageContainer } from '../../../components/layout/PageContainer'
import { PageHeader } from '../../../components/layout/PageHeader'

/** The sub-category name every FII product is classified under (mirrors `AllocationPlanService`). */
const FII_SUBCATEGORY_NAME = 'REITs (FIIs)'

type FiiTab = 'allocation' | 'dividends'

/**
 * The FII portfolio page (F026 spec, ADR 0023): two nested (two-ring) allocation donuts - Actual
 * and Planned, inner ring by segment, outer ring by ticker within each segment (Addendum - Nested
 * Allocation Charts, replacing the four original flat single-ring charts) - at the top, then the
 * portfolio list (one row per FII, closed holdings hidden by default) with its month/status
 * filters, then two sub-tabs - "Dividends" (default; dividend history - the register-dividend
 * dialog trigger lives in the header, not this tab) and "Allocation Plan" (the target-allocation
 * editor) - mirroring F023's Investments-page layout (charts above tabs). The month picker
 * (Addendum: Month Selector) drives the portfolio list and both charts, defaulting to (and clamped
 * to) the current month; the `needsSnapshot` badge is hidden whenever a past month is selected,
 * since it's a "today" concept the backend never recomputes against a past date.
 *
 * Phones (F040, below `sm`): the two donuts sit side by side in two half-width columns with their
 * legends left out, followed by `FiiAllocationComparison`, one table that replaces both legends and
 * lines Actual up against Planned. From `sm` up each donut keeps its own legend beside it.
 */
export function FiiPage() {
  const { t } = useTranslation('fii')
  const { locale } = useLocale()
  const isMobile = useIsMobile()
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

  const allocationCharts = [
    {
      basis: 'ACTUAL',
      title: t('actualAllocationTitle'),
      ariaLabel: t('actualAllocationAria'),
      emptyMessage: t('actualEmptyMessage'),
    },
    {
      basis: 'PLANNED',
      title: t('plannedAllocationTitle'),
      ariaLabel: t('plannedAllocationAria'),
      emptyMessage: t('plannedEmptyMessage'),
    },
  ] as const

  const columns: ResponsiveColumn<FiiPortfolioRow>[] = [
    { key: 'ticker', header: t('columns.ticker'), render: (r) => r.ticker ?? '-', role: 'primary' },
    { key: 'name', header: t('columns.name'), render: (r) => r.name, role: 'secondary' },
    {
      key: 'cotas',
      header: t('columns.cotas'),
      render: (r) => formatNumber(r.cotasHeld, locale, 0),
      align: 'right',
      tabletPriority: 'low',
    },
    {
      key: 'contributed',
      header: t('columns.contributed'),
      render: (r) => formatMoney(r.amountContributed, locale),
      align: 'right',
      tabletPriority: 'low',
    },
    {
      key: 'value',
      header: t('columns.value'),
      render: (r) =>
        `${formatMoney(r.currentValue, locale)}${r.needsSnapshot && isCurrentMonth ? ' *' : ''}`,
      align: 'right',
    },
    {
      key: 'snapshot',
      header: t('columns.snapshot'),
      render: (r) => r.latestSnapshotDate ?? t('noSnapshotYet'),
      align: 'right',
      tabletPriority: 'low',
    },
  ]

  return (
    <PageContainer>
      <PageHeader title={t('title')}>
        <Button variant="contained" onClick={() => setRegisterOpen(true)}>
          {t('registerDividendButton')}
        </Button>
      </PageHeader>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('description')}
      </Typography>

      {isMobile ? (
        <Box sx={{ mb: 3 }}>
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 2 }}>
            {allocationCharts.map((chart) => (
              <Box key={chart.basis}>
                <Typography variant="subtitle1" component="h2" sx={{ mb: 1 }}>
                  {chart.title}
                </Typography>
                <FiiNestedAllocationDonutChart
                  basis={chart.basis}
                  month={month}
                  ariaLabel={chart.ariaLabel}
                  emptyMessage={chart.emptyMessage}
                  compact
                />
              </Box>
            ))}
          </Box>
          <Box sx={{ mt: 2 }}>
            <FiiAllocationComparison month={month} />
          </Box>
        </Box>
      ) : (
        <Grid container spacing={3} sx={{ mb: 3 }}>
          {allocationCharts.map((chart) => (
            <Grid key={chart.basis} size={{ xs: 12, md: 6 }}>
              <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
                {chart.title}
              </Typography>
              <FiiNestedAllocationDonutChart
                basis={chart.basis}
                month={month}
                ariaLabel={chart.ariaLabel}
                emptyMessage={chart.emptyMessage}
              />
            </Grid>
          ))}
        </Grid>
      )}

      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2, mb: 2 }}>
        <MonthPicker
          label={t('monthLabel')}
          value={month}
          onChange={setMonth}
          clampToCurrentMonth
        />
        <Select
          size="small"
          value={status}
          onChange={(e) => setStatus(e.target.value as InvestmentProductStatus)}
          aria-label={t('statusAria')}
        >
          <MenuItem value="OPEN">{t('statusOpen')}</MenuItem>
          <MenuItem value="CLOSED">{t('statusClosed')}</MenuItem>
          <MenuItem value="ALL">{t('statusAll')}</MenuItem>
        </Select>
      </Box>

      <ResponsiveTable
        columns={columns}
        rows={portfolio}
        getRowKey={(r) => r.productId}
        state={portfolioState}
        emptyMessage={t('emptyPortfolio')}
        aria-label={t('portfolioAria')}
      />

      <Tabs value={tab} onChange={handleTabChange} aria-label={t('tabsAria')} sx={{ mt: 3 }}>
        <Tab
          value="dividends"
          label={t('dividendsTab')}
          id="fii-tab-dividends"
          aria-controls="fii-tabpanel-dividends"
        />
        <Tab
          value="allocation"
          label={t('allocationTab')}
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
    </PageContainer>
  )
}
