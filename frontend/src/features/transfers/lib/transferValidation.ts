import { netSettlementPreview } from './tradeMath'
import type { FormState, LineState } from './transferFormState'

/**
 * The transfer form's rules as pure functions of its state: what makes a line and the whole form
 * submittable. Client-side only; the backend enforces the same (and more) on submit, but sends no
 * message for most of its 400s, so the form blocks them up front.
 */

/** A form field's text as a number, or `null` when empty or not a number. */
export function numberOrNull(text: string): number | null {
  if (text.trim() === '') return null
  const value = Number(text)
  return Number.isFinite(value) ? value : null
}

/**
 * The trade confirmation's net settlement (see `tradeMath`), or `null` in plain mode and while a
 * line's quantity/unit price is still missing.
 */
export function tradeNetPreview(form: FormState): number | null {
  if (form.mode !== 'trade') return null
  return netSettlementPreview(
    form.lines.map((l) => ({
      side: l.side,
      quantity: numberOrNull(l.quantity),
      unitPrice: numberOrNull(l.unitPrice),
    })),
    numberOrNull(form.taxes),
  )
}

/**
 * Product ids with a `resultingBalance` set on more than one line - mirrors the backend's
 * `TradeConfirmation`/`TradeConfirmationRequest` cross-line invariant (a holding has one snapshot
 * per date, so two lines of the same product each trying to set one would be ambiguous). Blocked
 * client-side, same as the same-account case, since the backend sends no message for its 400.
 */
export function duplicateResultingBalanceProductIds(lines: LineState[]): Set<string> {
  const seen = new Set<string>()
  const duplicates = new Set<string>()
  for (const line of lines) {
    if (line.resultingBalance.trim() === '' || line.productId === '') continue
    if (seen.has(line.productId)) duplicates.add(line.productId)
    seen.add(line.productId)
  }
  return duplicates
}

export function isLineValid(line: LineState): boolean {
  if (line.productId === '') return false
  const quantity = numberOrNull(line.quantity)
  const unitPrice = numberOrNull(line.unitPrice)
  if (!(quantity !== null && quantity > 0)) return false
  if (!(unitPrice !== null && unitPrice > 0)) return false
  if (line.resultingBalance.trim() !== '' && !((numberOrNull(line.resultingBalance) ?? -1) >= 0)) {
    return false
  }
  if (line.closeHolding && line.side !== 'SELL') return false
  return true
}

export function isTransferFormValid(form: FormState): boolean {
  if (form.date === '' || form.description.trim() === '') return false
  if (form.mode === 'plain') {
    const amount = Number(form.amount)
    return (
      amount > 0 &&
      form.fromAccountId !== '' &&
      form.toAccountId !== '' &&
      form.fromAccountId !== form.toAccountId
    )
  }
  if (form.cashAccountId === '' || form.investmentAccountId === '') return false
  if (form.cashAccountId === form.investmentAccountId) return false
  if (form.lines.length === 0) return false
  const taxes = numberOrNull(form.taxes)
  if (!(taxes !== null && taxes >= 0)) return false
  if (!form.lines.every(isLineValid)) return false
  if (duplicateResultingBalanceProductIds(form.lines).size > 0) return false
  const net = tradeNetPreview(form)
  return net !== null && net !== 0
}
