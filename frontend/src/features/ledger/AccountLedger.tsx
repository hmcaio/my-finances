import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import { Box, Link as MuiLink } from '@mui/material'
import { useLedger } from '../../api/ledger/ledgerQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { MonthNavigator } from '../../components/inputs/MonthNavigator'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { currentMonth, monthRange } from '../../utils/localDate'
import { LedgerSummary } from './LedgerSummary'
import { LedgerTable } from './LedgerTable'
import { useLedgerLookups } from './useLedgerLookups'

interface AccountLedgerProps {
  accountId: string
}

/**
 * The account detail page's transactions-and-transfers section (F037), replacing the two
 * separate embedded lists: the Ledger scoped to one account (a transfer matches either side),
 * read-only, with the month navigator and summary and a link to the full Ledger for that account
 * and month. The month lives in local state rather than the URL: the account page has no query
 * contract and the embed offers no other filters. An `INVESTMENT` account takes no transactions
 * (PRD S5.4), so for it the list is only transfers and the summary is zero.
 */
export function AccountLedger({ accountId }: AccountLedgerProps) {
  const { t } = useTranslation(['ledger', 'common'])
  const [month, setMonth] = useState(currentMonth)
  const [error, setError] = useState<string | null>(null)
  const { from, to } = monthRange(month)
  const { lookups, state: lookupsState } = useLedgerLookups(setError)
  const ledgerQuery = useLedger({ accountId, dateFrom: from, dateTo: to })
  const ledgerState = useQueryState(ledgerQuery, setError)
  const tableState = combineLoadState(lookupsState, ledgerState)
  const ledger = ledgerQuery.data

  function retry() {
    setError(null)
    tableState.reload()
  }

  return (
    <Box>
      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          mb: 2,
        }}
      >
        <MonthNavigator value={month} onChange={setMonth} />
        <MuiLink
          component={RouterLink}
          to={`/ledger?account=${encodeURIComponent(accountId)}&month=${month}`}
          underline="hover"
        >
          {t('ledger:accountSection.viewInLedger')}
        </MuiLink>
      </Box>

      <LedgerSummary summary={ledger?.summary} />

      <LedgerTable
        embedded
        aria-label={t('ledger:accountSection.aria')}
        entries={ledger?.entries}
        state={tableState}
        onRetry={retry}
        lookups={lookups}
        emptyMessage={t('ledger:accountSection.emptyList')}
      />
    </Box>
  )
}
