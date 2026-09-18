import { useEffect, useMemo, useState } from 'react'
import {
  Box,
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
import { defaultErrorMessage } from '../../api/apiError'
import { ErrorAlert } from '../../components/ErrorAlert'
import { LoadingTableRow } from '../../components/LoadingTableRow'
import { PaginationControls } from '../../components/PaginationControls'
import { nameLookup } from '../../utils/nameLookup'

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
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
  }, [])

  useEffect(() => {
    getTransfers({ accountId }, page, PAGE_SIZE)
      .then((result) => {
        setTransfers(result.content)
        setPageInfo({ number: result.page.number, totalPages: result.page.totalPages })
      })
      .catch((err: unknown) => setError(defaultErrorMessage(err)))
  }, [accountId, page])

  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])

  return (
    <Box>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

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
            {transfers === null && <LoadingTableRow colSpan={5} variant="text" />}
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

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} />
    </Box>
  )
}
