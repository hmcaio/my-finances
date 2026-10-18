import type { LedgerEntryType, LedgerFilter } from '../../api/ledger/ledger'
import { currentMonth, monthRange } from '../../utils/localDate'

/** Longest search text the backend accepts (`TextFieldConstraints.MAX_SEARCH_TEXT_LENGTH`). */
export const MAX_SEARCH_LENGTH = 100

/** The Ledger's type filter as it appears in the URL (lower case); `''` is "all". */
export type LedgerTypeParam = '' | 'income' | 'expense' | 'transfer'

/**
 * Everything the Ledger page shows, kept in the URL query string so a link or refresh restores the
 * view (F037). Absent values are `''` so they bind straight to a controlled input.
 *
 * - `scope: 'month'` shows `month`; `scope: 'range'` shows `from`..`to` (either may be empty; both
 *   empty is "all time"). `month` is ignored while `scope` is `'range'`.
 * - `focus` is a transfer id to open for editing (the product detail page's "View confirmation").
 */
export interface LedgerView {
  scope: 'month' | 'range'
  month: string
  from: string
  to: string
  type: LedgerTypeParam
  account: string
  category: string
  paymentMethod: string
  q: string
  focus: string
}

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/
const TYPE_TO_API: Record<Exclude<LedgerTypeParam, ''>, LedgerEntryType> = {
  income: 'INCOME',
  expense: 'EXPENSE',
  transfer: 'TRANSFER',
}

function validMonth(value: string | null): string | null {
  return value !== null && MONTH_PATTERN.test(value) ? value : null
}

/** An ISO calendar date that really exists (rejects `2026-02-30`), else `''`. */
function validDate(value: string | null): string {
  if (value === null) return ''
  const match = DATE_PATTERN.exec(value)
  if (!match) return ''
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])]
  const date = new Date(year, month - 1, day)
  const exists =
    date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
  return exists ? value : ''
}

function validType(value: string | null): LedgerTypeParam {
  return value === 'income' || value === 'expense' || value === 'transfer' ? value : ''
}

/**
 * Reads the view from the URL. Invalid values are dropped silently: a bad month falls back to the
 * current one, a bad date or type to "unset". `month` is only used when `scope=range` is absent. A
 * category or payment method next to `type=transfer` is dropped too, because a transfer has
 * neither (the UI makes that combination impossible, a hand-edited URL could still ask for it).
 */
export function parseLedgerView(params: URLSearchParams): LedgerView {
  const range = params.get('scope') === 'range'
  const type = validType(params.get('type'))
  const isTransfer = type === 'transfer'
  return {
    scope: range ? 'range' : 'month',
    month: validMonth(params.get('month')) ?? currentMonth(),
    from: range ? validDate(params.get('from')) : '',
    to: range ? validDate(params.get('to')) : '',
    type,
    account: params.get('account') ?? '',
    category: isTransfer ? '' : (params.get('category') ?? ''),
    paymentMethod: isTransfer ? '' : (params.get('paymentMethod') ?? ''),
    q: (params.get('q') ?? '').slice(0, MAX_SEARCH_LENGTH),
    focus: params.get('focus') ?? '',
  }
}

/**
 * The query string for `view`: empty values are omitted, `month` is written only in month mode and
 * `from`/`to` only in range mode, so the two modes never leave stale keys behind.
 */
export function serializeLedgerView(view: LedgerView): URLSearchParams {
  const params = new URLSearchParams()
  if (view.scope === 'range') {
    params.set('scope', 'range')
    if (view.from) params.set('from', view.from)
    if (view.to) params.set('to', view.to)
  } else {
    params.set('month', view.month)
  }
  if (view.type) params.set('type', view.type)
  if (view.account) params.set('account', view.account)
  if (view.type !== 'transfer') {
    if (view.category) params.set('category', view.category)
    if (view.paymentMethod) params.set('paymentMethod', view.paymentMethod)
  }
  if (view.q) params.set('q', view.q)
  if (view.focus) params.set('focus', view.focus)
  return params
}

/** The API filter for `view`: the month (or range) becomes `dateFrom`/`dateTo`, `type` the enum. */
export function toLedgerFilter(view: LedgerView): LedgerFilter {
  const filter: LedgerFilter = {}
  if (view.scope === 'month') {
    const { from, to } = monthRange(view.month)
    filter.dateFrom = from
    filter.dateTo = to
  } else {
    if (view.from) filter.dateFrom = view.from
    if (view.to) filter.dateTo = view.to
  }
  if (view.type) filter.type = TYPE_TO_API[view.type]
  if (view.account) filter.accountId = view.account
  if (view.category) filter.categoryId = view.category
  if (view.paymentMethod) filter.paymentMethodId = view.paymentMethod
  if (view.q.trim()) filter.q = view.q.trim()
  return filter
}

/** Range mode with both bounds empty: the whole history. */
export function isAllTime(view: LedgerView): boolean {
  return view.scope === 'range' && !view.from && !view.to
}

/** How many of type/account/category/payment method/search are set (the filter badge count). */
export function activeFilterCount(view: LedgerView): number {
  return [view.type, view.account, view.category, view.paymentMethod, view.q].filter(Boolean).length
}
