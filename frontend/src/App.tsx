import { useMemo, type PropsWithChildren } from 'react'
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom'
import { CssBaseline, ThemeProvider } from '@mui/material'
import { ColorModeProvider } from './hooks/ColorModeProvider'
import { useColorMode } from './hooks/useColorMode'
import { useHasAccounts } from './hooks/useHasAccounts'
import { getTheme } from './theme'
import { Layout } from './components/Layout'
import { ErrorBoundary } from './components/ErrorBoundary'
import { ComingSoon } from './components/ComingSoon'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { AccountsPage } from './features/accounts/AccountsPage'
import { AccountDetailPage } from './features/accounts/AccountDetailPage'
import { CategoriesPage } from './features/categories/CategoriesPage'
import { InstitutionsPage } from './features/institutions/InstitutionsPage'
import { InvestmentsPage } from './features/investments/InvestmentsPage'
import { InvestmentProductDetailPage } from './features/investmentProducts/InvestmentProductDetailPage'
import { InvestmentCategoriesPage } from './features/investmentCategories/InvestmentCategoriesPage'
import { PaymentMethodsPage } from './features/paymentMethods/PaymentMethodsPage'
import { TransactionsPage } from './features/transactions/TransactionsPage'
import { TransfersPage } from './features/transfers/TransfersPage'
import { BudgetsPage } from './features/budgets/BudgetsPage'
import { RecurringTemplatesPage } from './features/recurringTemplates/RecurringTemplatesPage'

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
  // the router+layout shell renders at all (see F001 spec's "Onboarding" section).
  const hasAccounts = useHasAccounts()

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {hasAccounts ? (
        <BrowserRouter>
          <Layout>
            <PageErrorBoundary>
              <Routes>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/transactions" element={<TransactionsPage />} />
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
                <Route path="/export" element={<ComingSoon title="Data Export" />} />
              </Routes>
            </PageErrorBoundary>
          </Layout>
        </BrowserRouter>
      ) : (
        <ComingSoon title="Onboarding" />
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
