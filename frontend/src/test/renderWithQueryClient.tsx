import type { ReactElement, ReactNode } from 'react'
import { render, renderHook, type RenderResult } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createQueryClient } from '../api/core/queryClient'
import { LocaleProvider } from '../hooks/locale/LocaleProvider'

/** A fresh client with the app's mutation-invalidation rules but `gcTime: 0`, so nothing leaks
 * between tests. */
export function createTestQueryClient(): QueryClient {
  return createQueryClient({ queries: { gcTime: 0 } })
}

function wrapperFor(client: QueryClient) {
  // F033/ADR 0027: any page using formatMoney/formatNumber/formatDate or translateSeedName calls
  // useLocale(), which throws outside a LocaleProvider - wrapped here so every existing
  // renderWithQueryClient/renderWithRouter/renderHookWithQueryClient call site keeps working with
  // no per-test change, the same way QueryClientProvider already is.
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <LocaleProvider>
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      </LocaleProvider>
    )
  }
}

/**
 * Renders `ui` inside a fresh `QueryClient`. Every component that uses an `<area>Queries` hook
 * needs it in place of RTL's plain `render`.
 */
export function renderWithQueryClient(ui: ReactElement): RenderResult {
  return render(ui, { wrapper: wrapperFor(createTestQueryClient()) })
}

/** `renderHook` inside a fresh `QueryClient`; pass `client` to share one across several hooks. */
export function renderHookWithQueryClient<Result>(
  hook: () => Result,
  client: QueryClient = createTestQueryClient(),
) {
  return { client, ...renderHook(hook, { wrapper: wrapperFor(client) }) }
}
