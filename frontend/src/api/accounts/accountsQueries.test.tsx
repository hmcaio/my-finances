import { describe, expect, it } from 'vitest'
import { waitFor } from '@testing-library/react'
import { seedAccounts, seedCheckingAccount } from '../../mocks/handlers/accounts'
import { renderHookWithQueryClient } from '../../test/renderWithQueryClient'
import { useAccount, useAccounts, useCloseAccount, useDeleteAccount } from './accountsQueries'

describe('accounts hooks', () => {
  it('loads open accounts by default and closed ones on request', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      open: useAccounts(),
      all: useAccounts(true),
    }))

    await waitFor(() => expect(result.current.all.data).toHaveLength(seedAccounts.length))
    expect(result.current.open.data).toHaveLength(seedAccounts.filter((a) => !a.closed).length)
  })

  it('loads one account by id', async () => {
    const { result } = renderHookWithQueryClient(() => useAccount(seedCheckingAccount.id))

    await waitFor(() => expect(result.current.data?.name).toBe(seedCheckingAccount.name))
  })

  it('refreshes the lists and the detail after closing an account', async () => {
    const { result } = renderHookWithQueryClient(() => ({
      open: useAccounts(),
      detail: useAccount(seedCheckingAccount.id),
      close: useCloseAccount(),
    }))
    await waitFor(() => expect(result.current.detail.data?.closed).toBe(false))

    await result.current.close.mutateAsync(seedCheckingAccount.id)

    await waitFor(() => expect(result.current.detail.data?.closed).toBe(true))
    await waitFor(() =>
      expect(result.current.open.data?.map((a) => a.id)).not.toContain(seedCheckingAccount.id),
    )
  })

  it('refreshes the list after deleting an account', async () => {
    const closed = seedAccounts.find((a) => a.closed)!
    const { result } = renderHookWithQueryClient(() => ({
      all: useAccounts(true),
      remove: useDeleteAccount(),
    }))
    await waitFor(() => expect(result.current.all.data?.map((a) => a.id)).toContain(closed.id))

    await result.current.remove.mutateAsync(closed.id)

    await waitFor(() => expect(result.current.all.data?.map((a) => a.id)).not.toContain(closed.id))
  })
})
