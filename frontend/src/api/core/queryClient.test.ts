import { describe, expect, it, vi } from 'vitest'
import { createQueryClient } from './queryClient'

describe('createQueryClient', () => {
  it('sets the documented defaults', () => {
    const options = createQueryClient().getDefaultOptions()

    expect(options.queries).toMatchObject({
      retry: false,
      gcTime: 10 * 60_000,
      staleTime: 30_000,
      refetchOnWindowFocus: true,
    })
    expect(options.mutations).toMatchObject({ retry: false })
  })

  it('lets a test override query defaults', () => {
    expect(createQueryClient({ queries: { gcTime: 0 } }).getDefaultOptions().queries?.gcTime).toBe(
      0,
    )
  })

  it('invalidates cached queries after a successful mutation', async () => {
    const client = createQueryClient()
    const spy = vi.spyOn(client, 'invalidateQueries')

    await client
      .getMutationCache()
      .build(client, { mutationFn: async () => 'ok' })
      .execute(undefined)

    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('marks cached queries stale after a successful mutation', async () => {
    const client = createQueryClient()
    client.setQueryData(['api', 'x'], 1)
    expect(client.getQueryState(['api', 'x'])?.isInvalidated).toBe(false)

    await client
      .getMutationCache()
      .build(client, { mutationFn: async () => 'ok' })
      .execute(undefined)

    expect(client.getQueryState(['api', 'x'])?.isInvalidated).toBe(true)
  })

  it('does not invalidate after a failed mutation', async () => {
    const client = createQueryClient()
    const spy = vi.spyOn(client, 'invalidateQueries')
    const mutation = client.getMutationCache().build(client, {
      mutationFn: async () => {
        throw new Error('boom')
      },
    })

    await expect(mutation.execute(undefined)).rejects.toThrow('boom')

    expect(spy).not.toHaveBeenCalled()
  })

  it('skips invalidation when the mutation sets meta.skipInvalidate', async () => {
    const client = createQueryClient()
    const spy = vi.spyOn(client, 'invalidateQueries')

    await client
      .getMutationCache()
      .build(client, { mutationFn: async () => 'ok', meta: { skipInvalidate: true } })
      .execute(undefined)

    expect(spy).not.toHaveBeenCalled()
  })
})
