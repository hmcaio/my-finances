import { useAccountCount } from '../api/accounts/accountsQueries'
import { useQueryState, type LoadState } from './queryState'

export interface HasAccountsState extends LoadState {
  /** `null` until the check resolves (or when it failed) - never treat that as "no accounts". */
  hasAccounts: boolean | null
}

/**
 * Onboarding gate (F011): the app is in onboarding state exactly while zero accounts exist,
 * closed ones included (a user who closed every account is past onboarding). It derives from the
 * cached account count, so it can't drift from the data and flips to `true` on its own once the
 * create-account mutation has invalidated it. It reads the count, not the account list: the list
 * computes every balance, and the whole app shell waits on this gate (issue #189). App.tsx renders the onboarding screen when
 * `hasAccounts` is `false` and the normal router+layout shell when it is `true`.
 */
export function useHasAccounts(): HasAccountsState {
  const query = useAccountCount()
  const state = useQueryState(query)
  return { hasAccounts: query.data ? query.data.count > 0 : null, ...state }
}
