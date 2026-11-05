/**
 * Pure helpers behind the trade-confirmation form's fields (F027 spec, ADR 0024): per-line totals
 * and the overall net-settlement preview, mirroring the backend's `TradeConfirmation.netCost` -
 * sum each line's `quantity x unitPrice`, net BUY against SELL, add taxes once, round only the
 * final figure. This is a *preview*: the backend is the one that actually derives and enforces
 * `amount`/direction on submit (ADR 0024 reverses F009/ADR 0012's "the backend doesn't enforce
 * the equality" for a trade confirmation).
 */

export type TradeSide = 'BUY' | 'SELL'

export interface TradeLineInput {
  side: TradeSide
  quantity: number | null
  unitPrice: number | null
}

/** Rounds to cents, avoiding float noise like `100.10000000000001`. */
export function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100
}

function isPositive(value: number | null | undefined): value is number {
  return value !== null && value !== undefined && Number.isFinite(value) && value > 0
}

/** One line's total, `quantity x unitPrice`; `null` unless both are positive numbers. */
export function lineTotal(quantity: number | null, unitPrice: number | null): number | null {
  if (!isPositive(quantity) || !isPositive(unitPrice)) return null
  return quantity * unitPrice
}

/**
 * The settlement's net preview across every line plus taxes: BUY lines add, SELL lines subtract,
 * taxes add once, rounded only at the end (HALF_UP via `round2`) - mirrors the backend's
 * `TradeConfirmation.netCost`. `null` when any line's total isn't computable yet (quantity/unit
 * price still missing), so the form shows a prompt instead of a wrong number.
 */
export function netSettlementPreview(lines: TradeLineInput[], taxes: number | null): number | null {
  let net = 0
  for (const line of lines) {
    const total = lineTotal(line.quantity, line.unitPrice)
    if (total === null) return null
    net += line.side === 'BUY' ? total : -total
  }
  const fee = taxes !== null && Number.isFinite(taxes) ? taxes : 0
  return round2(net + fee)
}

/**
 * The direction a net settlement resolves to: positive is a net cost (cash account -> investment
 * account), negative is net proceeds (investment account -> cash account). `null` for a net of
 * exactly zero - the backend rejects that confirmation outright.
 */
export function netSettlementDirection(net: number | null): 'cost' | 'proceeds' | null {
  if (net === null || net === 0) return null
  return net > 0 ? 'cost' : 'proceeds'
}
