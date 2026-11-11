import type { RecordSnapshotSheetRequest } from '../../../../api/investments/products/investmentSnapshotSheet'

/**
 * The pure rules of the snapshot sheet (F040): which rows count as filled, whether a balance is
 * valid, the difference against the previous balance, the request, and mapping the `errors[]` of a
 * failed save back to rows. The page holds only state around them.
 */

/** The codes a bad balance is flagged with: the ones the backend's DTO would answer with. */
export type BalanceCode = 'positive-or-zero' | 'digits'

export type BalanceCheck =
  { kind: 'blank' } | { kind: 'valid'; value: number } | { kind: 'invalid'; code: BalanceCode }

/** `numeric(19,2)`: seventeen integer digits and two decimals, like the DTO's `@Digits`. */
const MAX_INTEGER_DIGITS = 17
const MAX_FRACTION_DIGITS = 2

/**
 * Checks one balance input as typed. Blank means "skip this row"; anything else mirrors
 * `SnapshotSheetEntryRequest`: not negative, at most two decimals, at most seventeen integer
 * digits. A decimal comma is read as a point.
 */
export function checkBalance(input: string): BalanceCheck {
  const text = input.trim().replace(',', '.')
  if (text === '') return { kind: 'blank' }
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(text)
  if (!match) return { kind: 'invalid', code: 'digits' }
  const [, sign, integer, fraction = ''] = match
  const value = Number(text)
  if (sign === '-' && value !== 0) return { kind: 'invalid', code: 'positive-or-zero' }
  if (integer.length > MAX_INTEGER_DIGITS || fraction.length > MAX_FRACTION_DIGITS) {
    return { kind: 'invalid', code: 'digits' }
  }
  return { kind: 'valid', value: Math.abs(value) }
}

/** The signed change from the previous balance, to the cent; `null` when there was none. */
export function differenceOf(balance: number, previous: number | null): number | null {
  if (previous === null) return null
  return Math.round(balance * 100 - previous * 100) / 100
}

interface HoldingRef {
  holdingId: string
}

/** The inputs by holding id, as typed. A holding with no entry is blank. */
export type SheetInputs = Readonly<Record<string, string>>

/** The rows whose input is filled but not valid, with the code to flag them with. */
export function rowIssues(
  rows: readonly HoldingRef[],
  inputs: SheetInputs,
): Record<string, BalanceCode> {
  const issues: Record<string, BalanceCode> = {}
  for (const { holdingId } of rows) {
    const check = checkBalance(inputs[holdingId] ?? '')
    if (check.kind === 'invalid') issues[holdingId] = check.code
  }
  return issues
}

/**
 * The request for the filled rows, in row order, or `null` when there is nothing valid to send: no
 * date, no filled row, or any filled row that is not valid (a bad row is flagged and holds the
 * whole save back rather than being silently left out).
 */
export function buildRequest(
  date: string,
  rows: readonly HoldingRef[],
  inputs: SheetInputs,
): RecordSnapshotSheetRequest | null {
  if (date === '') return null
  const entries: RecordSnapshotSheetRequest['entries'] = []
  for (const { holdingId } of rows) {
    const check = checkBalance(inputs[holdingId] ?? '')
    if (check.kind === 'invalid') return null
    if (check.kind === 'valid') entries.push({ holdingId, balance: check.value })
  }
  return entries.length === 0 ? null : { date, entries }
}

/**
 * The server's `errors[]` of a failed save, by row. `entries[i]` is the i-th row that was SENT, not
 * the i-th row of the sheet (blank rows are not sent), so the index is resolved through the sent
 * entries. An error with no row field (`holding-ids-unique`, `date`) and an index beyond the
 * request belong to no row and are left out; each code appears once per row.
 */
export function rowCodesFromErrors(
  fieldErrors: readonly { field: string | null; code: string }[] | undefined,
  sent: readonly HoldingRef[],
): Record<string, string[]> {
  const byRow: Record<string, string[]> = {}
  for (const { field, code } of fieldErrors ?? []) {
    const index =
      field === null ? undefined : /^entries\[(\d+)\]\.(?:holdingId|balance)$/.exec(field)?.[1]
    const holdingId = index === undefined ? undefined : sent[Number(index)]?.holdingId
    if (holdingId === undefined) continue
    const codes = (byRow[holdingId] ??= [])
    if (!codes.includes(code)) codes.push(code)
  }
  return byRow
}
