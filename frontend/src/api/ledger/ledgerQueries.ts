import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { API_KEY_ROOT } from '../core/queryClient'
import { getLedger, type LedgerFilter } from './ledger'

export const ledgerKeys = {
  all: [API_KEY_ROOT, 'ledger'] as const,
  list: (filter: LedgerFilter) => [...ledgerKeys.all, 'list', filter] as const,
}

/**
 * The Ledger for `filter`. The previous result stays on screen while the next one loads, so
 * stepping through months does not blank the table. Every mutation already invalidates all
 * queries, so no ledger-specific invalidation exists.
 */
export function useLedger(filter: LedgerFilter = {}) {
  return useQuery({
    queryKey: ledgerKeys.list(filter),
    queryFn: () => getLedger(filter),
    placeholderData: keepPreviousData,
  })
}
