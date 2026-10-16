import { describe, expect, it, vi } from 'vitest'
import { ApiError } from './apiError'
import { createQueryClient, SESSION_QUERY_KEY } from './queryClient'

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
  describe('session expiry (F036)', () => {
    const unauthenticated = new ApiError(401, 'x', 'urn:my-finances:error:unauthenticated')

    it('marks the session signed out and drops app data when a query answers 401', async () => {
      const client = createQueryClient()
      client.setQueryData(SESSION_QUERY_KEY, { authenticated: true })
      client.setQueryData(['api', 'accounts', 'list'], [{ id: 'a' }])

      await client
        .fetchQuery({
          queryKey: ['api', 'transactions'],
          queryFn: () => Promise.reject(unauthenticated),
        })
        .catch(() => undefined)

      expect(client.getQueryData(SESSION_QUERY_KEY)).toEqual({ authenticated: false })
      expect(client.getQueryData(['api', 'accounts', 'list'])).toBeUndefined()
    })

    it('does the same when a mutation answers 401', async () => {
      const client = createQueryClient()
      client.setQueryData(SESSION_QUERY_KEY, { authenticated: true })

      await client
        .getMutationCache()
        .build(client, { mutationFn: () => Promise.reject(unauthenticated) })
        .execute(undefined)
        .catch(() => undefined)

      expect(client.getQueryData(SESSION_QUERY_KEY)).toEqual({ authenticated: false })
    })

    it('leaves the session alone for a mutation that handles its own 401 (the login form)', async () => {
      const client = createQueryClient()
      client.setQueryData(SESSION_QUERY_KEY, { authenticated: true })

      await client
        .getMutationCache()
        .build(client, {
          mutationFn: () => Promise.reject(unauthenticated),
          meta: { skipSessionExpiry: true },
        })
        .execute(undefined)
        .catch(() => undefined)

      expect(client.getQueryData(SESSION_QUERY_KEY)).toEqual({ authenticated: true })
    })

    it('ignores non-401 failures', async () => {
      const client = createQueryClient()
      client.setQueryData(SESSION_QUERY_KEY, { authenticated: true })

      await client
        .fetchQuery({
          queryKey: ['api', 'transactions'],
          queryFn: () => Promise.reject(new ApiError(500, 'boom')),
        })
        .catch(() => undefined)

      expect(client.getQueryData(SESSION_QUERY_KEY)).toEqual({ authenticated: true })
    })
  })
})
