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
import { getAccounts } from '../../api/accounts'
import { getTransfers } from '../../api/transfers'
import { ErrorAlert } from '../../components/ErrorAlert'
import { DataTableBody } from '../../components/DataTableBody'
import { combineLoadState, useAsyncData } from '../../hooks/useAsyncData'
import { usePagedData } from '../../hooks/usePagedData'
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
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const { data: accounts, ...accountsState } = useAsyncData(() => getAccounts(true), [], {
    onError: setError,
  })
  const {
    items: transfers,
    pageInfo,
    ...transfersState
  } = usePagedData(() => getTransfers({ accountId }, page, PAGE_SIZE), [accountId, page], {
    onError: setError,
  })

  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])

  // One load state for the table plus the lookup lists behind its name columns: rows show only
  // once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(accountsState, transfersState)
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
              <TableCell>From</TableCell>
              <TableCell>To</TableCell>
              <TableCell align="right">Amount</TableCell>
              <TableCell>Description</TableCell>
            </TableRow>
          </TableHead>
          <DataTableBody state={tableState} onRetry={retry} columns={5}>
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
          </DataTableBody>
        </Table>
      </TableContainer>

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} />
    </Box>
  )
}
