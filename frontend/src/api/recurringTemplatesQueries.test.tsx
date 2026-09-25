import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import {
  seedPendingRecurringOccurrences,
  seedRecurringTemplates,
  seedRentPendingOccurrence,
  seedRentRecurringTemplate,
} from '../mocks/handlers/recurringTemplates'
import { renderHookWithQueryClient } from '../test/renderWithQueryClient'
import { createQueryClient } from './queryClient'
import {
  useConfirmPendingOccurrence,
  useDismissPendingOccurrence,
  usePendingRecurringOccurrences,
  useRecurringTemplates,
  useStopRecurringTemplate,
} from './recurringTemplatesQueries'

describe('recurring template hooks', () => {
  it('loads templates and pending occurrences', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      templates: useRecurringTemplates(),
      pending: usePendingRecurringOccurrences(),
    }))

    await waitFor(() => expect(result.current.templates.data).toEqual(seedRecurringTemplates))
    await waitFor(() =>
      expect(result.current.pending.data).toEqual(seedPendingRecurringOccurrences),
    )
  })

  it('fetches pending occurrences on every mount (they trigger catch-up), but caches templates', async () => {
    const requests = { templates: 0, pending: 0 }
    server.use(
      http.get('/api/recurring-templates', () => {
        requests.templates += 1
        return HttpResponse.json(seedRecurringTemplates)
      }),
      http.get('/api/recurring-templates/pending', () => {
        requests.pending += 1
        return HttpResponse.json([])
      }),
    )
    // The app's real defaults (a test client would drop the cache on unmount with gcTime 0).
    const client = createQueryClient()
    const useBoth = () => ({
      templates: useRecurringTemplates(),
      pending: usePendingRecurringOccurrences(),
    })

    const first = renderHookWithQueryClient(useBoth, client)
    await waitFor(() => expect(first.result.current.pending.data).toEqual([]))
    first.unmount()
    const second = renderHookWithQueryClient(useBoth, client)
    await waitFor(() => expect(requests.pending).toBe(2))

    expect(second.result.current.templates.data).toEqual(seedRecurringTemplates)
    expect(requests.templates).toBe(1)
  })

  it('refetches pending occurrences and templates after confirm, dismiss and stop', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      templates: useRecurringTemplates(),
      pending: usePendingRecurringOccurrences(),
      confirm: useConfirmPendingOccurrence(),
      dismiss: useDismissPendingOccurrence(),
      stop: useStopRecurringTemplate(),
    }))
    await waitFor(() => expect(result.current.pending.data).toHaveLength(1))

    await result.current.stop.mutateAsync(seedRentRecurringTemplate.id)
    await waitFor(() => expect(result.current.templates.data?.[0].active).toBe(false))

    await result.current.confirm.mutateAsync({
      id: seedRentPendingOccurrence.id,
      paymentMethodId: 'pm-1',
    })
    await waitFor(() => expect(result.current.pending.data).toEqual([]))

    await result.current.dismiss.mutateAsync('anything')
  })
})
