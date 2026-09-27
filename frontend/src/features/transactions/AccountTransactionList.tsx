import { useMemo, useState } from 'react'
import { Box } from '@mui/material'
import { useCategories } from '../../api/categories/categoriesQueries'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import type { Transaction } from '../../api/transactions/transactions'
import { useTransactions } from '../../api/transactions/transactionsQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { PaginationControls } from '../../components/table/PaginationControls'
import { nameLookup } from '../../utils/nameLookup'

const PAGE_SIZE = 10

interface AccountTransactionListProps {
  accountId: string
}

/**
 * Read-only, pre-filtered embed of the transaction list for one account (F004 spec: "Account
 * detail view (F003) embeds this feature's list, pre-filtered to that account"). Filter controls
 * and the create/edit form live on the standalone `/transactions` page (`TransactionsPage`) - this
 * is just that same list, scoped to `accountId` via the same `GET /api/transactions?accountId=`
 * query param.
 *
 * Responsive (F021): a read-only `ResponsiveTable` - cards below `sm` (description on top, then
 * date, then the labelled fields), the Payment Method column behind the row expander on tablet.
 */
export function AccountTransactionList({ accountId }: AccountTransactionListProps) {
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const categoriesQuery = useCategories()
  const categories = categoriesQuery.data
  const categoriesState = useQueryState(categoriesQuery, setError)
  const paymentMethodsQuery = usePaymentMethods()
  const paymentMethods = paymentMethodsQuery.data
  const paymentMethodsState = useQueryState(paymentMethodsQuery, setError)
  const transactionsQuery = useTransactions({ accountId }, page, PAGE_SIZE)
  const transactions = transactionsQuery.data?.content
  const pageInfo = transactionsQuery.data
    ? {
        number: transactionsQuery.data.page.number,
        totalPages: transactionsQuery.data.page.totalPages,
      }
    : null
  const transactionsState = useQueryState(transactionsQuery, setError)

  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])
  const paymentMethodName = useMemo(
    () => nameLookup(paymentMethods ?? [], (p) => p.name),
    [paymentMethods],
  )

  // One load state for the table plus the lookup lists behind its name columns: rows show only
  // once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(categoriesState, paymentMethodsState, transactionsState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  const columns: ResponsiveColumn<Transaction>[] = [
    { key: 'date', header: 'Date', role: 'secondary', render: (t) => t.date },
    { key: 'category', header: 'Category', render: (t) => categoryName(t.categoryId) },
    {
      key: 'paymentMethod',
      header: 'Payment Method',
      render: (t) => paymentMethodName(t.paymentMethodId),
      tabletPriority: 'low',
    },
    {
      key: 'amount',
      header: 'Amount',
      align: 'right',
      render: (t) => `${t.type === 'EXPENSE' ? '-' : '+'}${t.amount.toFixed(2)}`,
    },
    { key: 'description', header: 'Description', role: 'primary', render: (t) => t.description },
  ]

  return (
    <Box>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <ResponsiveTable
        embedded
        aria-label="Account transactions"
        columns={columns}
        rows={transactions}
        getRowKey={(t) => t.id}
        state={tableState}
        onRetry={retry}
        emptyMessage="No transactions yet."
      />

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} />
    </Box>
  )
}
