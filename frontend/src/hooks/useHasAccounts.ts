import { useAccounts } from '../api/accountsQueries'
import { useQueryState, type LoadState } from './queryState'

export interface HasAccountsState extends LoadState {
  /** `null` until the check resolves (or when it failed) - never treat that as "no accounts". */
  hasAccounts: boolean | null
}

/**
 * Onboarding gate (F011): the app is in onboarding state exactly while zero accounts exist,
 * closed ones included (a user who closed every account is past onboarding). It derives from the
 * cached accounts list, so it can't drift from the data and flips to `true` on its own once the
 * create-account mutation has invalidated that list. App.tsx renders the onboarding screen when
 * `hasAccounts` is `false` and the normal router+layout shell when it is `true`.
 */
export function useHasAccounts(): HasAccountsState {
  const query = useAccounts(true)
  const state = useQueryState(query)
  return { hasAccounts: query.data ? query.data.length > 0 : null, ...state }
}
