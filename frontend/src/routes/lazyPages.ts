import { lazyNamed } from '../utils/lazyNamed'

// Route-level code splitting: each page is its own chunk, fetched on first navigation instead of
// all being bundled into one >500kB entry chunk. Named exports, so each goes through `lazyNamed`.
export const LoginPage = lazyNamed(() => import('../features/auth/LoginPage'), 'LoginPage')
export const DashboardPage = lazyNamed(
  () => import('../features/dashboard/DashboardPage'),
  'DashboardPage',
)
export const AccountsPage = lazyNamed(
  () => import('../features/accounts/AccountsPage'),
  'AccountsPage',
)
export const AccountDetailPage = lazyNamed(
  () => import('../features/accounts/AccountDetailPage'),
  'AccountDetailPage',
)
export const CategoriesPage = lazyNamed(
  () => import('../features/categories/CategoriesPage'),
  'CategoriesPage',
)
export const FuelPage = lazyNamed(() => import('../features/fuel/FuelPage'), 'FuelPage')
export const GoalDetailPage = lazyNamed(
  () => import('../features/goals/GoalDetailPage'),
  'GoalDetailPage',
)
export const GoalsPage = lazyNamed(() => import('../features/goals/GoalsPage'), 'GoalsPage')
export const InstitutionsPage = lazyNamed(
  () => import('../features/institutions/InstitutionsPage'),
  'InstitutionsPage',
)
export const InvestmentsPage = lazyNamed(
  () => import('../features/investments/overview/InvestmentsPage'),
  'InvestmentsPage',
)
export const InvestmentProductDetailPage = lazyNamed(
  () => import('../features/investments/products/InvestmentProductDetailPage'),
  'InvestmentProductDetailPage',
)
export const InvestmentCategoriesPage = lazyNamed(
  () => import('../features/investments/categories/InvestmentCategoriesPage'),
  'InvestmentCategoriesPage',
)
export const InvestmentSegmentsPage = lazyNamed(
  () => import('../features/investments/segments/InvestmentSegmentsPage'),
  'InvestmentSegmentsPage',
)
export const FiiPage = lazyNamed(() => import('../features/investments/fii/FiiPage'), 'FiiPage')
export const PaymentMethodsPage = lazyNamed(
  () => import('../features/paymentMethods/PaymentMethodsPage'),
  'PaymentMethodsPage',
)
export const LedgerPage = lazyNamed(() => import('../features/ledger/LedgerPage'), 'LedgerPage')
export const BudgetsPage = lazyNamed(() => import('../features/budgets/BudgetsPage'), 'BudgetsPage')
export const ReportsPage = lazyNamed(() => import('../features/reports/ReportsPage'), 'ReportsPage')
export const RecurringTemplatesPage = lazyNamed(
  () => import('../features/recurringTemplates/RecurringTemplatesPage'),
  'RecurringTemplatesPage',
)
export const VehiclesPage = lazyNamed(
  () => import('../features/vehicles/VehiclesPage'),
  'VehiclesPage',
)
export const ActivityPage = lazyNamed(
  () => import('../features/activity/ActivityPage'),
  'ActivityPage',
)
