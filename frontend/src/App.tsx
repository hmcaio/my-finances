import { lazy, Suspense, useMemo, type PropsWithChildren } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
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

// Route-level code splitting: each page is its own chunk, fetched on first navigation instead of
// all being bundled into one >500kB entry chunk. Named exports, so each needs its own `.then`.
const DashboardPage = lazy(() =>
  import('./features/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage })),
)
const AccountsPage = lazy(() =>
  import('./features/accounts/AccountsPage').then((m) => ({ default: m.AccountsPage })),
)
const AccountDetailPage = lazy(() =>
  import('./features/accounts/AccountDetailPage').then((m) => ({ default: m.AccountDetailPage })),
)
const CategoriesPage = lazy(() =>
  import('./features/categories/CategoriesPage').then((m) => ({ default: m.CategoriesPage })),
)
const FuelPage = lazy(() =>
  import('./features/fuel/FuelPage').then((m) => ({ default: m.FuelPage })),
)
const InstitutionsPage = lazy(() =>
  import('./features/institutions/InstitutionsPage').then((m) => ({
    default: m.InstitutionsPage,
  })),
)
const InvestmentsPage = lazy(() =>
  import('./features/investments/InvestmentsPage').then((m) => ({ default: m.InvestmentsPage })),
)
const InvestmentProductDetailPage = lazy(() =>
  import('./features/investmentProducts/InvestmentProductDetailPage').then((m) => ({
    default: m.InvestmentProductDetailPage,
  })),
)
const InvestmentCategoriesPage = lazy(() =>
  import('./features/investmentCategories/InvestmentCategoriesPage').then((m) => ({
    default: m.InvestmentCategoriesPage,
  })),
)
const PaymentMethodsPage = lazy(() =>
  import('./features/paymentMethods/PaymentMethodsPage').then((m) => ({
    default: m.PaymentMethodsPage,
  })),
)
const TransactionsPage = lazy(() =>
  import('./features/transactions/TransactionsPage').then((m) => ({
    default: m.TransactionsPage,
  })),
)
const TransfersPage = lazy(() =>
  import('./features/transfers/TransfersPage').then((m) => ({ default: m.TransfersPage })),
)
const BudgetsPage = lazy(() =>
  import('./features/budgets/BudgetsPage').then((m) => ({ default: m.BudgetsPage })),
)
const ExportPage = lazy(() =>
  import('./features/export/ExportPage').then((m) => ({ default: m.ExportPage })),
)
const RecurringTemplatesPage = lazy(() =>
  import('./features/recurringTemplates/RecurringTemplatesPage').then((m) => ({
    default: m.RecurringTemplatesPage,
  })),
)
const VehiclesPage = lazy(() =>
  import('./features/vehicles/VehiclesPage').then((m) => ({ default: m.VehiclesPage })),
)

/** Suspense fallback while a route's chunk loads - same skeleton shape as the onboarding check. */
function RouteFallback() {
  return (
    <Box sx={{ maxWidth: 720, mx: 'auto', px: 2, py: 6, ...fadeInSx }}>
      <Skeleton variant="text" width="60%" height={48} />
      <Skeleton variant="rounded" height={120} />
    </Box>
  )
}

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
              <Suspense fallback={<RouteFallback />}>
                <Routes>
                  <Route path="/" element={<DashboardPage />} />
                  <Route path="/transactions" element={<TransactionsPage />} />
                  <Route path="/fuel" element={<FuelPage />} />
                  <Route path="/accounts" element={<AccountsPage />} />
                  <Route path="/accounts/:id" element={<AccountDetailPage />} />
                  <Route
                    path="/investment-products/:id"
                    element={<InvestmentProductDetailPage />}
                  />
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
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </Suspense>
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
