import { useMemo, useState } from 'react'
import { Box } from '@mui/material'
import { useAccounts } from '../../api/accounts/accountsQueries'
import type { Transfer } from '../../api/transfers/transfers'
import { useTransfers } from '../../api/transfers/transfersQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { PaginationControls } from '../../components/table/PaginationControls'
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
 *
 * Responsive (F021): a read-only `ResponsiveTable` - cards below `sm` (description on top, then
 * date, then the labelled From/To/Amount), the full table from tablet up (all five columns fit).
 */
export function AccountTransferList({ accountId }: AccountTransferListProps) {
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const accountsQuery = useAccounts(true)
  const accounts = accountsQuery.data
  const accountsState = useQueryState(accountsQuery, setError)
  const transfersQuery = useTransfers({ accountId }, page, PAGE_SIZE)
  const transfers = transfersQuery.data?.content
  const pageInfo = transfersQuery.data
    ? { number: transfersQuery.data.page.number, totalPages: transfersQuery.data.page.totalPages }
    : null
  const transfersState = useQueryState(transfersQuery, setError)

  const accountName = useMemo(() => nameLookup(accounts ?? [], (a) => a.name), [accounts])

  // One load state for the table plus the lookup lists behind its name columns: rows show only
  // once every name can be resolved. Retry clears the stale banner and reloads what failed.
  const tableState = combineLoadState(accountsState, transfersState)
  function retry() {
    setError(null)
    tableState.reload()
  }

  const columns: ResponsiveColumn<Transfer>[] = [
    { key: 'date', header: 'Date', role: 'secondary', render: (t) => t.date },
    { key: 'from', header: 'From', render: (t) => accountName(t.fromAccountId) },
    { key: 'to', header: 'To', render: (t) => accountName(t.toAccountId) },
    { key: 'amount', header: 'Amount', align: 'right', render: (t) => t.amount.toFixed(2) },
    { key: 'description', header: 'Description', role: 'primary', render: (t) => t.description },
  ]

  return (
    <Box>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <ResponsiveTable
        embedded
        aria-label="Account transfers"
        columns={columns}
        rows={transfers}
        getRowKey={(t) => t.id}
        state={tableState}
        onRetry={retry}
        emptyMessage="No transfers yet."
      />

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} />
    </Box>
  )
}
