import {
  MutationCache,
  QueryCache,
  QueryClient,
  type QueryClientConfig,
} from '@tanstack/react-query'
import { ApiError } from './apiError'

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: {
      /** Opts a mutation out of the global invalidate-everything-on-success rule. */
      skipInvalidate?: boolean
      /** A 401 from this mutation is its own answer (the login form's "wrong password"), not an
       * expired session: don't sign the app out because of it (F036). */
      skipSessionExpiry?: boolean
    }
  }
}

/** Root of every query key (see each area's `<area>Queries.ts` key factory). */
export const API_KEY_ROOT = 'api' as const

/** The session check (`GET /auth/session`, F036): `{ authenticated }`, never an error for a 401. */
export const SESSION_QUERY_KEY = [API_KEY_ROOT, 'auth', 'session'] as const

/** Every query key under this area belongs to the login machinery, not to cached app data. */
const AUTH_AREA = 'auth'

/** True for a cache entry that holds app data (anything under the API root except `auth`). */
export function isAppDataKey(queryKey: readonly unknown[]): boolean {
  return queryKey[0] === API_KEY_ROOT && queryKey[1] !== AUTH_AREA
}

const MINUTE = 60_000

export const STALE_TIME = {
  /** Lists and reports. */
  default: 30_000,
  /** Reference data: categories, payment methods, institutions, investment taxonomy. */
  reference: 5 * MINUTE,
  /** Reads with a side effect or time dependence (pending occurrences). */
  none: 0,
} as const

/**
 * Builds the app's QueryClient (ADR 0016). Every successful mutation invalidates every query
 * (coarse on purpose: one write can change balances, net worth, allocation and budget-vs-actual);
 * a failed mutation never does, and `meta: { skipInvalidate: true }` opts a mutation out.
 * `overrides` lets tests tighten the defaults (`gcTime: 0`).
 */
export function createQueryClient(
  overrides: QueryClientConfig['defaultOptions'] = {},
): QueryClient {
  // F036: any API call answered 401 means the session is gone (expired, or the backend restarted
  // and dropped its in-memory store). Mark the app signed out - the route guard then redirects to
  // /login - and drop the cached data so nothing financial lingers in memory behind the login.
  // setQueryData rather than a refetch: a 401 is already the definitive answer.
  function handleSessionExpiry(error: unknown): void {
    if (error instanceof ApiError && error.status === 401) {
      client.removeQueries({ predicate: (query) => isAppDataKey(query.queryKey) })
      client.setQueryData(SESSION_QUERY_KEY, { authenticated: false })
    }
  }

  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({
      onError: (error) => handleSessionExpiry(error),
    }),
    mutationCache: new MutationCache({
      onError: (error, _variables, _context, mutation) => {
        if (mutation.meta?.skipSessionExpiry) return
        handleSessionExpiry(error)
      },
      onSuccess: (_data, _variables, _context, mutation) => {
        if (mutation.meta?.skipInvalidate) return
        // Not awaited: the mutation resolves as soon as the write did; views refetch behind it.
        void client.invalidateQueries()
      },
    }),
    defaultOptions: {
      ...overrides,
      queries: {
        staleTime: STALE_TIME.default,
        gcTime: 10 * MINUTE,
        refetchOnWindowFocus: true,
        retry: false,
        ...overrides.queries,
      },
      mutations: { retry: false, ...overrides.mutations },
    },
  })
  return client
}
