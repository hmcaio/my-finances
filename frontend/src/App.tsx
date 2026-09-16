import { useMemo } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { CssBaseline, ThemeProvider } from '@mui/material'
import { ColorModeProvider, useColorMode } from './hooks/useColorMode'
import { useHasAccounts } from './hooks/useHasAccounts'
import { getTheme } from './theme'
import { Layout } from './components/Layout'
import { ComingSoon } from './components/ComingSoon'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { AccountsPage } from './features/accounts/AccountsPage'
import { AccountDetailPage } from './features/accounts/AccountDetailPage'
import { CategoriesPage } from './features/categories/CategoriesPage'
import { PaymentMethodsPage } from './features/paymentMethods/PaymentMethodsPage'
import { TransactionsPage } from './features/transactions/TransactionsPage'

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
            <Routes>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/transactions" element={<TransactionsPage />} />
              <Route path="/accounts" element={<AccountsPage />} />
              <Route path="/accounts/:id" element={<AccountDetailPage />} />
              <Route path="/transfers" element={<ComingSoon title="Transfers" />} />
              <Route path="/budgets" element={<ComingSoon title="Budgets" />} />
              <Route path="/recurring" element={<ComingSoon title="Recurring Templates" />} />
              <Route path="/investments" element={<ComingSoon title="Investments" />} />
              <Route path="/settings/categories" element={<CategoriesPage />} />
              <Route path="/settings/payment-methods" element={<PaymentMethodsPage />} />
              <Route path="/export" element={<ComingSoon title="Data Export" />} />
            </Routes>
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
