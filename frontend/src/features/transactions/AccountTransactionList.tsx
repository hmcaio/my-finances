import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { getCategories, type Category } from '../../api/categories'
import { getPaymentMethods, type PaymentMethod } from '../../api/paymentMethods'
import { getTransactions, type Transaction } from '../../api/transactions'
import { ApiError } from '../../api/apiError'
import { nameLookup } from './nameLookup'

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
      .catch((err: unknown) => setError(errorMessage(err)))
    getPaymentMethods()
      .then(setPaymentMethods)
      .catch((err: unknown) => setError(errorMessage(err)))
  }, [])

  useEffect(() => {
    getTransactions({ accountId }, page, PAGE_SIZE)
      .then((result) => {
        setTransactions(result.content)
        setPageInfo({ number: result.page.number, totalPages: result.page.totalPages })
      })
      .catch((err: unknown) => setError(errorMessage(err)))
  }, [accountId, page])

  const categoryName = useMemo(() => nameLookup(categories ?? [], (c) => c.name), [categories])
  const paymentMethodName = useMemo(
    () => nameLookup(paymentMethods ?? [], (p) => p.name),
    [paymentMethods],
  )

  return (
    <Box>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

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
          <TableBody>
            {transactions === null && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  Loading…
                </TableCell>
              </TableRow>
            )}
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
          </TableBody>
        </Table>
      </TableContainer>

      {pageInfo && pageInfo.totalPages > 1 && (
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mt: 2 }}>
          <Button
            size="small"
            disabled={pageInfo.number <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </Button>
          <Typography variant="body2">
            Page {pageInfo.number + 1} of {pageInfo.totalPages}
          </Typography>
          <Button
            size="small"
            disabled={pageInfo.number + 1 >= pageInfo.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </Box>
      )}
    </Box>
  )
}

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong.'
}
