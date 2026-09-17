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
import { getAccounts, type Account } from '../../api/accounts'
import { getTransfers, type Transfer } from '../../api/transfers'
import { ApiError } from '../../api/apiError'
import { nameLookup } from '../transactions/nameLookup'

const PAGE_SIZE = 10

interface AccountTransferListProps {
  accountId: string
}

/**
 * Read-only, pre-filtered embed of the transfer list for one account (F005 spec: "Account detail
 * view (F003) embeds transfer history alongside transaction history, both contributing to the
 * same running-balance timeline"). Filter controls and the create/edit form live on the standalone
 * `/transfers` page (`TransfersPage`) - this is just that same list, scoped to `accountId` via the
 * same `GET /api/transfers?accountId=` query param (matching either side, PRD S6.9). Mirrors
 * F004's `AccountTransactionList` shape.
 */
export function AccountTransferList({ accountId }: AccountTransferListProps) {
  const [transfers, setTransfers] = useState<Transfer[] | null>(null)
  const [pageInfo, setPageInfo] = useState<{ number: number; totalPages: number } | null>(null)
  const [page, setPage] = useState(0)
  const [accounts, setAccounts] = useState<Account[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getAccounts(true)
      .then(setAccounts)
      .catch((err: unknown) => setError(errorMessage(err)))
  }, [])

  useEffect(() => {
    getTransfers({ accountId }, page, PAGE_SIZE)
      .then((result) => {
        setTransfers(result.content)
        setPageInfo({ number: result.page.number, totalPages: result.page.totalPages })
      })
      .catch((err: unknown) => setError(errorMessage(err)))
  }, [accountId, page])

  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])

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
              <TableCell>From</TableCell>
              <TableCell>To</TableCell>
              <TableCell align="right">Amount</TableCell>
              <TableCell>Description</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {transfers === null && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  Loading…
                </TableCell>
              </TableRow>
            )}
            {transfers?.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <Typography color="text.secondary">No transfers yet.</Typography>
                </TableCell>
              </TableRow>
            )}
            {transfers?.map((transfer) => (
              <TableRow key={transfer.id}>
                <TableCell>{transfer.date}</TableCell>
                <TableCell>{accountName(transfer.fromAccountId)}</TableCell>
                <TableCell>{accountName(transfer.toAccountId)}</TableCell>
                <TableCell align="right">{transfer.amount.toFixed(2)}</TableCell>
                <TableCell>{transfer.description}</TableCell>
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
