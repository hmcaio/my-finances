import { Suspense, useMemo, useState, type PropsWithChildren } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Box, CssBaseline, Skeleton, ThemeProvider } from '@mui/material'
import { ColorModeProvider } from './providers/colorMode/ColorModeProvider'
import { useColorMode } from './providers/colorMode/useColorMode'
import { LocaleProvider } from './providers/locale/LocaleProvider'
import { lazyNamed } from './utils/lazyNamed'
import { useHasAccounts } from './hooks/useHasAccounts'
import { getTheme } from './theme'
import { Layout } from './components/layout/Layout'
import { ErrorBoundary } from './components/feedback/ErrorBoundary'
import { BackupStatusBanner } from './components/layout/BackupStatusBanner'
import { LoadFailedNotice } from './components/feedback/LoadFailedNotice'
import { fadeInSx } from './components/feedback/fadeIn'
import { useDelayedFlag } from './hooks/useDelayedFlag'
import { OnboardingPage } from './features/onboarding/OnboardingPage'
import { RequireAuth } from './features/auth/RequireAuth'
import { TransactionsRedirect, TransfersRedirect } from './features/ledger/LegacyRedirects'

// Route-level code splitting: each page is its own chunk, fetched on first navigation instead of
// all being bundled into one >500kB entry chunk. Named exports, so each goes through `lazyNamed`.
const LoginPage = lazyNamed(() => import('./features/auth/LoginPage'), 'LoginPage')
const DashboardPage = lazyNamed(() => import('./features/dashboard/DashboardPage'), 'DashboardPage')
const AccountsPage = lazyNamed(() => import('./features/accounts/AccountsPage'), 'AccountsPage')
const AccountDetailPage = lazyNamed(
  () => import('./features/accounts/AccountDetailPage'),
  'AccountDetailPage',
)
const CategoriesPage = lazyNamed(
  () => import('./features/categories/CategoriesPage'),
  'CategoriesPage',
)
const FuelPage = lazyNamed(() => import('./features/fuel/FuelPage'), 'FuelPage')
const InstitutionsPage = lazyNamed(
  () => import('./features/institutions/InstitutionsPage'),
  'InstitutionsPage',
)
const InvestmentsPage = lazyNamed(
  () => import('./features/investments/InvestmentsPage'),
  'InvestmentsPage',
)
const InvestmentProductDetailPage = lazyNamed(
  () => import('./features/investmentProducts/InvestmentProductDetailPage'),
  'InvestmentProductDetailPage',
)
const InvestmentCategoriesPage = lazyNamed(
  () => import('./features/investmentCategories/InvestmentCategoriesPage'),
  'InvestmentCategoriesPage',
)
const InvestmentSegmentsPage = lazyNamed(
  () => import('./features/investmentSegments/InvestmentSegmentsPage'),
  'InvestmentSegmentsPage',
)
const FiiPage = lazyNamed(() => import('./features/fii/FiiPage'), 'FiiPage')
const PaymentMethodsPage = lazyNamed(
  () => import('./features/paymentMethods/PaymentMethodsPage'),
  'PaymentMethodsPage',
)
const LedgerPage = lazyNamed(() => import('./features/ledger/LedgerPage'), 'LedgerPage')
const BudgetsPage = lazyNamed(() => import('./features/budgets/BudgetsPage'), 'BudgetsPage')
const ReportsPage = lazyNamed(() => import('./features/reports/ReportsPage'), 'ReportsPage')
const RecurringTemplatesPage = lazyNamed(
  () => import('./features/recurringTemplates/RecurringTemplatesPage'),
  'RecurringTemplatesPage',
)
const VehiclesPage = lazyNamed(() => import('./features/vehicles/VehiclesPage'), 'VehiclesPage')
const ActivityPage = lazyNamed(() => import('./features/activity/ActivityPage'), 'ActivityPage')

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

/**
 * Everything behind the login (F036, ADR 0029): mounted only once `RequireAuth` has a session, so
 * none of its data hooks (accounts gate, backup banner, pages) fire while signed out.
 */
function AuthenticatedApp() {
  // Onboarding (F011) is not part of the route tree: it's a top-level check gating whether
  // the router+layout shell renders at all. `hasAccounts` stays null while the check is loading
  // or failed, so neither the shell nor onboarding renders on a guess.
  const { hasAccounts, loading, loadError, reload } = useHasAccounts()
  const showSkeleton = useDelayedFlag(loading)

  // F035: onboarding is now two steps (account, then category suggestions), but creating the
  // account alone already flips `hasAccounts` to `true` - every mutation invalidates every query
  // (frontend CLAUDE.md), so the accounts list refetches and resolves non-empty the instant step 1
  // succeeds, well before `OnboardingPage`'s own `activeStep` state renders step 2. Gating purely
  // on `hasAccounts` would swap onboarding for the dashboard mid-flow, silently skipping step 2.
  // `inOnboarding` latches "we entered onboarding" the first time `hasAccounts` resolves `false`
  // and only releases on `OnboardingPage`'s own `onCompleted` (step 2 finishing, not `hasAccounts`
  // flipping) - not persisted anywhere, just render-session state, consistent with this feature's
  // "no new persisted flag" decision (ADR 0028): a reload mid-flow re-derives from `hasAccounts`
  // alone and lands on the dashboard, same as finishing onboarding normally.
  //
  // Adjusted during render (React's documented pattern for state derived from a prop/other state
  // change), not in a `useEffect`, so the latch is already set on the very render that observes
  // `hasAccounts` turn `false` - an effect would only run after that render committed, one tick
  // too late for `showOnboarding` below to reflect it on the same render.
  const [prevHasAccounts, setPrevHasAccounts] = useState(hasAccounts)
  const [inOnboarding, setInOnboarding] = useState(hasAccounts === false)
  if (hasAccounts !== prevHasAccounts) {
    setPrevHasAccounts(hasAccounts)
    if (hasAccounts === false) setInOnboarding(true)
  }
  const showOnboarding = inOnboarding || hasAccounts === false

  return (
    <>
      {/* F018: not behind the onboarding gate below, so it shows regardless of whether
          onboarding has happened yet or which page (if any) is open. */}
      <BackupStatusBanner />
      {hasAccounts === null && !inOnboarding ? (
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
      ) : !showOnboarding ? (
        <Layout>
          <PageErrorBoundary>
            <Suspense fallback={<RouteFallback />}>
              <Routes>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/ledger" element={<LedgerPage />} />
                {/* F037: the Transactions and Transfers pages became the Ledger; old links redirect. */}
                <Route path="/transactions" element={<TransactionsRedirect />} />
                <Route path="/fuel" element={<FuelPage />} />
                <Route path="/accounts" element={<AccountsPage />} />
                <Route path="/accounts/:id" element={<AccountDetailPage />} />
                <Route path="/investment-products/:id" element={<InvestmentProductDetailPage />} />
                <Route path="/transfers" element={<TransfersRedirect />} />
                <Route path="/budgets" element={<BudgetsPage />} />
                <Route path="/recurring" element={<RecurringTemplatesPage />} />
                <Route path="/investments" element={<InvestmentsPage />} />
                <Route path="/fii" element={<FiiPage />} />
                <Route path="/settings/categories" element={<CategoriesPage />} />
                <Route path="/settings/institutions" element={<InstitutionsPage />} />
                <Route
                  path="/settings/investment-categories"
                  element={<InvestmentCategoriesPage />}
                />
                <Route path="/settings/investment-segments" element={<InvestmentSegmentsPage />} />
                <Route path="/settings/payment-methods" element={<PaymentMethodsPage />} />
                <Route path="/settings/vehicles" element={<VehiclesPage />} />
                <Route path="/reports" element={<ReportsPage />} />
                <Route path="/activity" element={<ActivityPage />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </Suspense>
          </PageErrorBoundary>
        </Layout>
      ) : (
        <OnboardingPage onCompleted={() => setInOnboarding(false)} />
      )}
    </>
  )
}

function ThemedApp() {
  const { mode } = useColorMode()
  const theme = useMemo(() => getTheme(mode), [mode])
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          {/* The one route reachable without a session; everything else is behind the guard. */}
          <Route
            path="/login"
            element={
              <Suspense fallback={<RouteFallback />}>
                <LoginPage />
              </Suspense>
            }
          />
          <Route
            path="*"
            element={
              <RequireAuth>
                <AuthenticatedApp />
              </RequireAuth>
            }
          />
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  )
}

function App() {
  return (
    <ColorModeProvider>
      <LocaleProvider>
        <ThemedApp />
      </LocaleProvider>
    </ColorModeProvider>
  )
}

export default App
