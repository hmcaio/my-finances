import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAppDataKey, SESSION_QUERY_KEY } from '../core/queryClient'
import { getSession, login, logout } from './auth'

export const sessionKeys = {
  session: () => SESSION_QUERY_KEY,
}

/**
 * The boot-time "do I have a session" check (F036) behind the route guard. Refetches on window
 * focus like every query, so a session that expired while the tab was in the background is
 * noticed as soon as the user comes back - and a 401 from any other call flips this same entry
 * (see `createQueryClient`).
 */
export function useSession() {
  return useQuery({
    queryKey: sessionKeys.session(),
    queryFn: getSession,
  })
}

/**
 * Signs in. Waits for the session re-check before resolving, so the caller can navigate straight
 * to the page the user was after without the guard still seeing "signed out". `skipSessionExpiry`:
 * a 401 here is "wrong password", which the form shows itself, not an expired session.
 */
export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: login,
    meta: { skipInvalidate: true, skipSessionExpiry: true },
    onSuccess: () => queryClient.refetchQueries({ queryKey: sessionKeys.session() }),
  })
}

/**
 * Ends the session, then drops every cached app-data query (nothing financial stays in memory
 * behind the login screen) and re-checks the session, which the guard turns into a redirect.
 */
export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    meta: { skipInvalidate: true },
    onSuccess: async () => {
      queryClient.removeQueries({ predicate: (query) => isAppDataKey(query.queryKey) })
      await queryClient.refetchQueries({ queryKey: sessionKeys.session() })
    },
  })
}
