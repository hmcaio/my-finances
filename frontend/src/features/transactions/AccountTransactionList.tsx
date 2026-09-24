import { useMemo, useState } from 'react'
import {
  Box,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useCategories } from '../../api/categoriesQueries'
import { usePaymentMethods } from '../../api/paymentMethodsQueries'
import { useTransactions } from '../../api/transactionsQueries'
import { ErrorAlert } from '../../components/ErrorAlert'
import { DataTableBody } from '../../components/DataTableBody'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { PaginationControls } from '../../components/PaginationControls'
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

  return (
    <Box>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Date</TableCell>
              <TableCell>Category</TableCell>
              <TableCell>Payment Method</TableCell>
              <TableCell align="right">Amount</TableCell>
              <TableCell>Description</TableCell>
            </TableRow>
          </TableHead>
          <DataTableBody state={tableState} onRetry={retry} columns={5}>
            {transactions?.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <Typography color="text.secondary">No transactions yet.</Typography>
                </TableCell>
              </TableRow>
            )}
            {transactions?.map((transaction) => (
              <TableRow key={transaction.id}>
                <TableCell>{transaction.date}</TableCell>
                <TableCell>{categoryName(transaction.categoryId)}</TableCell>
                <TableCell>{paymentMethodName(transaction.paymentMethodId)}</TableCell>
                <TableCell align="right">
                  {transaction.type === 'EXPENSE' ? '-' : '+'}
                  {transaction.amount.toFixed(2)}
                </TableCell>
                <TableCell>{transaction.description}</TableCell>
              </TableRow>
            ))}
          </DataTableBody>
        </Table>
      </TableContainer>

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} />
    </Box>
  )
}
