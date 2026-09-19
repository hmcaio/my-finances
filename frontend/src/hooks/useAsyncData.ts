import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type DependencyList,
  type Dispatch,
  type SetStateAction,
} from 'react'
import { defaultErrorMessage } from '../api/apiError'

export interface AsyncData<T> {
  /** The latest successfully fetched value, `null` until the first fetch resolves. */
  data: T | null
  /** For optimistic local edits after a mutation (add/rename/delete) without refetching. */
  setData: Dispatch<SetStateAction<T | null>>
  /** The first fetch is in flight: no data yet and no failure to report. */
  loading: boolean
  /** Message of the latest failed fetch, set only while there is no data to show. A failed refetch
   * over existing data keeps the stale data and reports through `onError` alone. */
  loadError: string | null
  /** Refetches. Keeps any existing data on screen; clears `loadError`, so a failed first load
   * shows the loading skeleton again while it retries. */
  reload: () => void
}

export interface AsyncDataOptions {
  /** Called with the message of every failed fetch, e.g. the page's error banner setter. */
  onError?: (message: string) => void
  /** Turns a caught error into text; defaults to `defaultErrorMessage`. */
  errorMessage?: (err: unknown) => string
}

/**
 * Fetches on mount and whenever `deps` change, replacing the `useState` + `useEffect` +
 * `.then(set).catch(setError)` trio every page hand-rolled.
 *
 * Unlike a page's dismissible error banner, `loading`/`loadError` describe the fetch itself, so
 * dismissing the banner can't flip a failed load back into a loading skeleton. A response for a
 * superseded fetch (deps changed, or unmounted) is ignored.
 */
export function useAsyncData<T>(
  fetcher: () => Promise<T>,
  deps: DependencyList,
  options: AsyncDataOptions = {},
): AsyncData<T> {
  const [data, setData] = useState<T | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  // The latest closures, so the effect below re-runs on `deps` only, not on every new function.
  const latest = useRef({ fetcher, options })
  useEffect(() => {
    latest.current = { fetcher, options }
  })

  useEffect(() => {
    let cancelled = false
    latest.current
      .fetcher()
      .then((result) => {
        if (cancelled) return
        setData(result)
        setFailure(null)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        const message = (latest.current.options.errorMessage ?? defaultErrorMessage)(err)
        setFailure(message)
        latest.current.options.onError?.(message)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `deps` is the caller's dependency list
  }, [...deps, attempt])

  const reload = useCallback(() => {
    setFailure(null)
    setAttempt((n) => n + 1)
  }, [])

  return {
    data,
    setData,
    loading: data === null && failure === null,
    loadError: data === null ? failure : null,
    reload,
  }
}
