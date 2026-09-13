/**
 * Onboarding gate (see F001 spec's "Onboarding" section): App.tsx renders onboarding
 * (F011, not built yet) standalone when this is false, and the normal router+layout shell
 * otherwise. F003 (accounts) doesn't exist yet, so there is no real "does an account exist"
 * endpoint to call - this stub always reports true so the normal app shell renders. F003
 * replaces this with a real check against its accounts endpoint.
 */
export function useHasAccounts(): boolean {
  return true
}
