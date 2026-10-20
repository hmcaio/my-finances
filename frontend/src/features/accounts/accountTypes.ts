import type { AccountType } from '../../api/accounts/accounts'

/**
 * Translation-key lookup for every account type, shared by the account list, form and detail page
 * (F033/ADR 0027: was a plain `Record<AccountType, string>` of literal English labels before this
 * feature). `t` is typed loosely for the same reason `translateSeedName` is - see its own
 * docblock (`src/i18n/seedNames.ts`).
 */
const ACCOUNT_TYPE_KEYS: Record<AccountType, string> = {
  CHECKING: 'accounts:accountTypes.checking',
  SAVINGS: 'accounts:accountTypes.savings',
  CASH_WALLET: 'accounts:accountTypes.cashWallet',
  CREDIT_CARD: 'accounts:accountTypes.creditCard',
  INVESTMENT: 'accounts:accountTypes.investment',
}

/** Every account type, for building a picker (`Object.keys` order matches the object above). */
export const ACCOUNT_TYPES: AccountType[] = [
  'CHECKING',
  'SAVINGS',
  'CASH_WALLET',
  'CREDIT_CARD',
  'INVESTMENT',
]

export function accountTypeLabel(type: AccountType, t: (key: never) => string): string {
  return t(ACCOUNT_TYPE_KEYS[type] as never)
}
