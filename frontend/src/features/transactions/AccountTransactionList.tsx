import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box } from '@mui/material'
import { useCategories } from '../../api/categories/categoriesQueries'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import type { Transaction } from '../../api/transactions/transactions'
import { useTransactions } from '../../api/transactions/transactionsQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { PaginationControls } from '../../components/table/PaginationControls'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { translateSeedName } from '../../i18n/seedNames'
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
  const { t } = useTranslation(['transactions', 'common'])
  const { locale } = useLocale()
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

  const categoryName = useMemo(
    () => nameLookup(categories ?? [], (c) => translateSeedName('categories', c.name, t)),
    [categories, t],
  )
  const paymentMethodName = useMemo(
    () => nameLookup(paymentMethods ?? [], (p) => translateSeedName('paymentMethods', p.name, t)),
    [paymentMethods, t],
  )

  // One load state for the table plus the lookup lists behind its name columns: rows show only
  // once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(categoriesState, paymentMethodsState, transactionsState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  const columns: ResponsiveColumn<Transaction>[] = [
    {
      key: 'date',
      header: t('transactions:columns.date'),
      role: 'secondary',
      render: (tx) => tx.date,
    },
    {
      key: 'category',
      header: t('transactions:columns.category'),
      render: (tx) => categoryName(tx.categoryId),
    },
    {
      key: 'paymentMethod',
      header: t('transactions:columns.paymentMethod'),
      render: (tx) => paymentMethodName(tx.paymentMethodId),
      tabletPriority: 'low',
    },
    {
      key: 'amount',
      header: t('transactions:columns.amount'),
      align: 'right',
      render: (tx) => `${tx.type === 'EXPENSE' ? '-' : '+'}${formatMoney(tx.amount, locale)}`,
    },
    {
      key: 'description',
      header: t('transactions:columns.description'),
      role: 'primary',
      render: (tx) => tx.description,
    },
  ]

  return (
    <Box>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <ResponsiveTable
        embedded
        aria-label={t('transactions:accountList.aria')}
        columns={columns}
        rows={transactions}
        getRowKey={(tx) => tx.id}
        state={tableState}
        onRetry={retry}
        emptyMessage={t('transactions:accountList.emptyList')}
      />

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} />
    </Box>
  )
}
