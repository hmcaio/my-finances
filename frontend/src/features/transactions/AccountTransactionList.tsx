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
import { getCategories } from '../../api/categories'
import { getPaymentMethods } from '../../api/paymentMethods'
import { getTransactions } from '../../api/transactions'
import { ErrorAlert } from '../../components/ErrorAlert'
import { DataTableBody } from '../../components/DataTableBody'
import { reloadFailed, useAsyncData } from '../../hooks/useAsyncData'
import { usePagedData } from '../../hooks/usePagedData'
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
  const { data: categories, ...categoriesState } = useAsyncData(getCategories, [], {
    onError: setError,
  })
  const { data: paymentMethods, ...paymentMethodsState } = useAsyncData(getPaymentMethods, [], {
    onError: setError,
  })
  const {
    items: transactions,
    pageInfo,
    ...transactionsState
  } = usePagedData(() => getTransactions({ accountId }, page, PAGE_SIZE), [accountId, page], {
    onError: setError,
  })

  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])
  const paymentMethodName = useMemo(
    () => nameLookup(paymentMethods ?? [], (p) => p.name),
    [paymentMethods],
  )

  // Clears the stale banner and retries whichever fetches failed - the table's own and the
  // lookup lists behind its name columns - so names don't stay as raw ids after a retry.
  function retry() {
    setError(null)
    reloadFailed(categoriesState, paymentMethodsState, transactionsState)
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
          <DataTableBody state={transactionsState} onRetry={retry} columns={5}>
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
