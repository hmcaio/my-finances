import { useEffect } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import { defaultErrorMessage } from '../api/apiError'

/** What `DataTableBody` / `LoadFailedNotice` consume: the non-data part of a query. */
export interface LoadState {
  /** The first fetch is in flight: no data yet (and no stale/placeholder data to show). */
  loading: boolean
  /** Message of the latest failed fetch, set only while there is no data to show. A failed
   * refetch over existing data keeps the rows and reports through `onError` alone. */
  loadError: string | null
  /** Refetches; a failed first load shows the loading skeleton again while it retries. */
  reload: () => void
}

/**
 * Maps a query result to a {@link LoadState}. Skeleton on `isPending` only (no data at all), so
 * a background refetch or a `keepPreviousData` page change never flashes it, and stale rows are
 * kept when a refetch fails. `onError` receives the message of every failed fetch (e.g. the
 * page's banner setter), including refetches over existing data.
 */
export function useQueryState(
  query: Pick<
    UseQueryResult<unknown>,
    'isPending' | 'isError' | 'error' | 'errorUpdatedAt' | 'data' | 'refetch'
  >,
  onError?: (message: string) => void,
  /** Turns the error into text; defaults to `defaultErrorMessage`. */
  errorMessage: (err: unknown) => string = defaultErrorMessage,
): LoadState {
  const { isError, error, errorUpdatedAt, data, isPending, refetch } = query

  useEffect(() => {
    if (isError) onError?.(errorMessage(error))
    // Re-run per failed fetch (errorUpdatedAt), not when the caller's setter identity changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [errorUpdatedAt])

  return {
    loading: isPending,
    loadError: data === undefined && isError ? errorMessage(error) : null,
    reload: () => void refetch(),
  }
}

/**
 * One load state for a table and the lookup lists behind its name columns (categories, accounts,
 * ...). It is loading until every source has loaded, so rows never render with raw ids standing in
 * for names that haven't arrived yet; it reports the first failure, and `reload` retries only the
 * sources that failed (the lists fail together when the backend is down).
 */
export function combineLoadState(...sources: LoadState[]): LoadState {
  const loadError = sources.find((source) => source.loadError)?.loadError ?? null
  return {
    loading: loadError === null && sources.some((source) => source.loading),
    loadError,
    reload: () => {
      for (const source of sources) {
        if (source.loadError) source.reload()
      }
    },
  }
}
