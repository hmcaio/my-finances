import { MutationCache, QueryClient, type QueryClientConfig } from '@tanstack/react-query'

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: {
      /** Opts a mutation out of the global invalidate-everything-on-success rule. */
      skipInvalidate?: boolean
    }
  }
}

/** Root of every query key (see each area's `<area>Queries.ts` key factory). */
export const API_KEY_ROOT = 'api' as const

const MINUTE = 60_000

export const STALE_TIME = {
  /** Lists and reports. */
  default: 30_000,
  /** Reference data: categories, payment methods, institutions, investment taxonomy. */
  reference: 5 * MINUTE,
  /** Reads with a side effect or time dependence (pending occurrences, health check). */
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
  const client: QueryClient = new QueryClient({
    mutationCache: new MutationCache({
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
