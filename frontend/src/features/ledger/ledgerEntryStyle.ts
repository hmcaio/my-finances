import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import type { LedgerEntryType } from '../../api/ledger/ledger'
import type { Locale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'

/**
 * How a Ledger row looks (F037): an icon, a theme colour and a sign, so colour is never the only
 * signal. Income is a green up-arrow with `+`, expense a red down-arrow with `-`, a transfer a
 * neutral (blue) swap icon with no sign: a transfer's sign relative to an account would be wrong
 * for a credit card (PRD S5.5), and its `From -> To` cell already shows the direction. Colours are
 * theme palette keys only.
 */
export interface LedgerEntryStyle {
  Icon: typeof ArrowUpwardIcon
  /** A palette key: `${color}.main` is the colour. */
  color: 'success' | 'error' | 'info'
  sign: '+' | '-' | ''
  /** Translation key of the accessible label, in the `ledger` namespace. */
  labelKey: 'type.income' | 'type.expense' | 'type.transfer'
}

const STYLES: Record<LedgerEntryType, LedgerEntryStyle> = {
  INCOME: { Icon: ArrowUpwardIcon, color: 'success', sign: '+', labelKey: 'type.income' },
  EXPENSE: { Icon: ArrowDownwardIcon, color: 'error', sign: '-', labelKey: 'type.expense' },
  TRANSFER: { Icon: SwapHorizIcon, color: 'info', sign: '', labelKey: 'type.transfer' },
}

export function ledgerEntryStyle(type: LedgerEntryType): LedgerEntryStyle {
  return STYLES[type]
}

/** The amount as shown in a row: signed for income and expense, plain for a transfer. */
export function formatEntryAmount(type: LedgerEntryType, amount: number, locale: Locale): string {
  return `${STYLES[type].sign}${formatMoney(amount, locale)}`
}
