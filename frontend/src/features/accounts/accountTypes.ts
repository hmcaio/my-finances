import type { AccountType } from '../../api/accounts/accounts'

/** Display labels for every account type, shared by the account list, form and detail page. */
export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CHECKING: 'Checking',
  SAVINGS: 'Savings',
  CASH_WALLET: 'Cash Wallet',
  CREDIT_CARD: 'Credit Card',
  INVESTMENT: 'Investment',
}
