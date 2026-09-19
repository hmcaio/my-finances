import { useEffect, useMemo, useState } from 'react'
import {
  Box,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { getCategories, type Category } from '../../api/categories'
import { getPaymentMethods, type PaymentMethod } from '../../api/paymentMethods'
import { getTransactions, type Transaction } from '../../api/transactions'
import { defaultErrorMessage } from '../../api/apiError'
import { ErrorAlert } from '../../components/ErrorAlert'
import { DataTableBody } from '../../components/DataTableBody'
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
  const [transactions, setTransactions] = useState<Transaction[] | null>(null)
  const [pageInfo, setPageInfo] = useState<{ number: number; totalPages: number } | null>(null)
  const [page, setPage] = useState(0)
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getCategories()
      .then(setCategories)
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
    getPaymentMethods()
      .then(setPaymentMethods)
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
  }, [])

  useEffect(() => {
    getTransactions({ accountId }, page, PAGE_SIZE)
      .then((result) => {
        setTransactions(result.content)
        setPageInfo({ number: result.page.number, totalPages: result.page.totalPages })
      })
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
  }, [accountId, page])

  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])
  const paymentMethodName = useMemo(
    () => nameLookup(paymentMethods ?? [], (p) => p.name),
    [paymentMethods],
  )

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
          <DataTableBody loading={transactions === null && !error} columns={5}>
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
