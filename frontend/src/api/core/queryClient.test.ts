import { describe, expect, it, vi } from 'vitest'
import type { MutationMeta } from '@tanstack/react-query'
import { getSnapshot } from '../../providers/notifications/notificationStore'
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
  describe('success message (F040)', () => {
    async function run(
      meta: MutationMeta | undefined,
      result: () => Promise<unknown> = async () => 'ok',
    ) {
      const client = createQueryClient()
      await client
        .getMutationCache()
        .build(client, { mutationFn: result, meta })
        .execute(undefined)
        .catch(() => undefined)
    }

    it('publishes the translated message when the mutation succeeds', async () => {
      await run({ successMessage: 'common:nav.dashboard' })

      expect(getSnapshot()?.message).toBe('Dashboard')
    })

    it('interpolates the options built from the result and the variables', async () => {
      const client = createQueryClient()
      await client
        .getMutationCache()
        .build(client, {
          mutationFn: async (variables: number) => ({ extra: variables + 1 }),
          meta: {
            successMessage: 'transfers:moreChips',
            successMessageOptions: (data, variables) => ({
              count: (data as { extra: number }).extra + (variables as number),
            }),
          },
        })
        .execute(1)

      expect(getSnapshot()?.message).toBe('+3 more')
    })

    it('translates in the language active when it publishes', async () => {
      const { default: i18n } = await import('../../i18n/i18n')
      await i18n.changeLanguage('pt-BR')

      await run({ successMessage: 'common:nav.dashboard' })

      expect(getSnapshot()?.message).toBe('Painel')
    })

    it('still publishes when the mutation skips the invalidation', async () => {
      await run({ successMessage: 'common:nav.dashboard', skipInvalidate: true })

      expect(getSnapshot()?.message).toBe('Dashboard')
    })

    it('publishes nothing when the mutation fails', async () => {
      await run({ successMessage: 'common:nav.dashboard' }, async () => {
        throw new Error('boom')
      })

      expect(getSnapshot()).toBeNull()
    })

    it('publishes nothing for a mutation without the meta', async () => {
      await run(undefined)
      await run({ skipInvalidate: true })

      expect(getSnapshot()).toBeNull()
    })
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
