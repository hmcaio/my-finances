import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box } from '@mui/material'
import { useAccounts } from '../../api/accounts/accountsQueries'
import type { Transfer } from '../../api/transfers/transfers'
import { useTransfers } from '../../api/transfers/transfersQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { ResponsiveTable, type ResponsiveColumn } from '../../components/table/ResponsiveTable'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { PaginationControls } from '../../components/table/PaginationControls'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
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
  const { t } = useTranslation('transfers')
  const { locale } = useLocale()
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
    { key: 'date', header: t('columns.date'), role: 'secondary', render: (tr) => tr.date },
    { key: 'from', header: t('columns.from'), render: (tr) => accountName(tr.fromAccountId) },
    { key: 'to', header: t('columns.to'), render: (tr) => accountName(tr.toAccountId) },
    {
      key: 'amount',
      header: t('columns.amount'),
      align: 'right',
      render: (tr) => formatMoney(tr.amount, locale),
    },
    {
      key: 'description',
      header: t('columns.description'),
      role: 'primary',
      render: (tr) => tr.description,
    },
  ]

  return (
    <Box>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <ResponsiveTable
        embedded
        aria-label={t('accountList.aria')}
        columns={columns}
        rows={transfers}
        getRowKey={(tr) => tr.id}
        state={tableState}
        onRetry={retry}
        emptyMessage={t('accountList.emptyList')}
      />

      <PaginationControls pageInfo={pageInfo} onPageChange={setPage} />
    </Box>
  )
}
