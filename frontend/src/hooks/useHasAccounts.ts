import { useCallback } from 'react'
import { getAccounts } from '../api/accounts'
import { useAsyncData, type AsyncData } from './useAsyncData'

export interface HasAccountsState extends Pick<
  AsyncData<boolean>,
  'loading' | 'loadError' | 'reload'
> {
  /** `null` until the check resolves (or when it failed) - never treat that as "no accounts". */
  hasAccounts: boolean | null
  /** Flips to `true` without refetching, once onboarding has created the first account. */
  markHasAccounts: () => void
}

/**
 * Onboarding gate (F011): the app is in onboarding state exactly while zero accounts exist,
 * closed ones included (a user who closed every account is past onboarding). Nothing is
 * persisted, so it can't drift from the data. App.tsx renders the onboarding screen when
 * `hasAccounts` is `false` and the normal router+layout shell when it is `true`.
 */
export function useHasAccounts(): HasAccountsState {
  const { data, setData, loading, loadError, reload } = useAsyncData(
    async () => (await getAccounts(true)).length > 0,
    [],
  )
  const markHasAccounts = useCallback(() => setData(true), [setData])
  return { hasAccounts: data, loading, loadError, reload, markHasAccounts }
}
