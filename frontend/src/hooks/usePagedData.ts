import { useCallback, type DependencyList } from 'react'
import { useAsyncData, type AsyncData, type AsyncDataOptions } from './useAsyncData'

/** The backend's `PagedModel` envelope, as mirrored by `TransactionPage` / `TransferPage`. */
interface PagedResult<T> {
  content: T[]
  page: { number: number; totalPages: number }
}

export interface PagedData<T> extends Pick<AsyncData<unknown>, 'loading' | 'loadError' | 'reload'> {
  /** The current page's rows, `null` until the first fetch resolves. */
  items: T[] | null
  /** Local edit of the current page's rows after a mutation; ignored while nothing is loaded. */
  setItems: (update: (prev: T[]) => T[]) => void
  /** Shape `PaginationControls` takes; `null` until the first fetch resolves. */
  pageInfo: { number: number; totalPages: number } | null
}

/** `useAsyncData` for a paginated endpoint: splits the envelope into rows and page info. */
export function usePagedData<T>(
  fetcher: () => Promise<PagedResult<T>>,
  deps: DependencyList,
  options?: AsyncDataOptions,
): PagedData<T> {
  const { data, setData, ...state } = useAsyncData(fetcher, deps, options)

  const setItems = useCallback(
    (update: (prev: T[]) => T[]) =>
      setData((prev) => (prev ? { ...prev, content: update(prev.content) } : prev)),
    [setData],
  )

  return {
    items: data?.content ?? null,
    setItems,
    pageInfo: data ? { number: data.page.number, totalPages: data.page.totalPages } : null,
    ...state,
  }
}
