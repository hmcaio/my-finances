import { useMemo, type PropsWithChildren } from 'react'
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom'
import { Box, CssBaseline, Skeleton, ThemeProvider } from '@mui/material'
import { ColorModeProvider } from './hooks/colorMode/ColorModeProvider'
import { useColorMode } from './hooks/colorMode/useColorMode'
import { useHasAccounts } from './hooks/useHasAccounts'
import { getTheme } from './theme'
import { Layout } from './components/layout/Layout'
import { ErrorBoundary } from './components/feedback/ErrorBoundary'
import { LoadFailedNotice } from './components/feedback/LoadFailedNotice'
import { fadeInSx } from './components/feedback/fadeIn'
import { useDelayedFlag } from './hooks/useDelayedFlag'
import { OnboardingPage } from './features/onboarding/OnboardingPage'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { AccountsPage } from './features/accounts/AccountsPage'
import { AccountDetailPage } from './features/accounts/AccountDetailPage'
import { CategoriesPage } from './features/categories/CategoriesPage'
import { FuelPage } from './features/fuel/FuelPage'
import { InstitutionsPage } from './features/institutions/InstitutionsPage'
import { InvestmentsPage } from './features/investments/InvestmentsPage'
import { InvestmentProductDetailPage } from './features/investmentProducts/InvestmentProductDetailPage'
import { InvestmentCategoriesPage } from './features/investmentCategories/InvestmentCategoriesPage'
import { PaymentMethodsPage } from './features/paymentMethods/PaymentMethodsPage'
import { TransactionsPage } from './features/transactions/TransactionsPage'
import { TransfersPage } from './features/transfers/TransfersPage'
import { BudgetsPage } from './features/budgets/BudgetsPage'
import { ExportPage } from './features/export/ExportPage'
import { RecurringTemplatesPage } from './features/recurringTemplates/RecurringTemplatesPage'
import { VehiclesPage } from './features/vehicles/VehiclesPage'

/**
 * Wraps the routed page (inside `Layout`, so the nav/theme survive a page crash). Keyed on the
 * pathname so navigating to another page resets a boundary that has already tripped.
 */
function PageErrorBoundary({ children }: PropsWithChildren) {
  const { pathname } = useLocation()
  return <ErrorBoundary key={pathname}>{children}</ErrorBoundary>
}

function ThemedApp() {
  const { mode } = useColorMode()
  const theme = useMemo(() => getTheme(mode), [mode])
  // Onboarding (F011) is not part of the route tree: it's a top-level check gating whether
  // the router+layout shell renders at all. `hasAccounts` stays null while the check is loading
  // or failed, so neither the shell nor onboarding renders on a guess.
  const { hasAccounts, loading, loadError, reload } = useHasAccounts()
  const showSkeleton = useDelayedFlag(loading)

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {hasAccounts === null ? (
        <Box sx={{ maxWidth: 720, mx: 'auto', px: 2, py: 6 }}>
          {loadError !== null ? (
            <LoadFailedNotice message={loadError} onRetry={reload} />
          ) : (
            showSkeleton && (
              <Box sx={fadeInSx}>
                <Skeleton variant="text" width="60%" height={48} />
                <Skeleton variant="rounded" height={120} />
              </Box>
            )
          )}
        </Box>
      ) : hasAccounts ? (
        <BrowserRouter>
          <Layout>
            <PageErrorBoundary>
              <Routes>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/transactions" element={<TransactionsPage />} />
                <Route path="/fuel" element={<FuelPage />} />
                <Route path="/accounts" element={<AccountsPage />} />
                <Route path="/accounts/:id" element={<AccountDetailPage />} />
                <Route path="/investment-products/:id" element={<InvestmentProductDetailPage />} />
                <Route path="/transfers" element={<TransfersPage />} />
                <Route path="/budgets" element={<BudgetsPage />} />
                <Route path="/recurring" element={<RecurringTemplatesPage />} />
                <Route path="/investments" element={<InvestmentsPage />} />
                <Route path="/settings/categories" element={<CategoriesPage />} />
                <Route path="/settings/institutions" element={<InstitutionsPage />} />
                <Route
                  path="/settings/investment-categories"
                  element={<InvestmentCategoriesPage />}
                />
                <Route path="/settings/payment-methods" element={<PaymentMethodsPage />} />
                <Route path="/settings/vehicles" element={<VehiclesPage />} />
                <Route path="/export" element={<ExportPage />} />
              </Routes>
            </PageErrorBoundary>
          </Layout>
        </BrowserRouter>
      ) : (
        <OnboardingPage />
      )}
    </ThemeProvider>
  )
}

function App() {
  return (
    <ColorModeProvider>
      <ThemedApp />
    </ColorModeProvider>
  )
}

export default App
