import { Navigate, Route, Routes } from 'react-router-dom'
import {
  AccountDetailPage,
  AccountsPage,
  ActivityPage,
  BudgetsPage,
  CategoriesPage,
  DashboardPage,
  FiiPage,
  FuelPage,
  GoalDetailPage,
  GoalsPage,
  InstitutionsPage,
  InvestmentCategoriesPage,
  InvestmentProductDetailPage,
  InvestmentSegmentsPage,
  InvestmentsPage,
  LedgerPage,
  PaymentMethodsPage,
  RecurringTemplatesPage,
  ReportsPage,
  VehiclesPage,
} from './lazyPages'
import { TransactionsRedirect, TransfersRedirect } from './LegacyRedirects'

/**
 * The route table behind the login. A new route is one `lazyNamed` line in `lazyPages.ts` plus
 * one `<Route>` here. Rendered inside `Layout`, `PageErrorBoundary` and `Suspense` (see `App.tsx`).
 */
export function AuthenticatedRoutes() {
  return (
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
      <Route path="/goals" element={<GoalsPage />} />
      <Route path="/goals/:id" element={<GoalDetailPage />} />
      <Route path="/fii" element={<FiiPage />} />
      <Route path="/settings/categories" element={<CategoriesPage />} />
      <Route path="/settings/institutions" element={<InstitutionsPage />} />
      <Route path="/settings/investment-categories" element={<InvestmentCategoriesPage />} />
      <Route path="/settings/investment-segments" element={<InvestmentSegmentsPage />} />
      <Route path="/settings/payment-methods" element={<PaymentMethodsPage />} />
      <Route path="/settings/vehicles" element={<VehiclesPage />} />
      <Route path="/reports" element={<ReportsPage />} />
      <Route path="/activity" element={<ActivityPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
