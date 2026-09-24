/**
 * Pure helpers behind the transfer form's buy/sell fields (F009 spec). Everything here is a
 * *suggestion* shown to the user - the backend records quantity, unit price and taxes but never
 * computes anything from them, so `amount` and `resultingBalance` stay whatever the user submits.
 */

export type TradeDirection = 'buy' | 'sell'

/** Rounds to cents, avoiding float noise like `100.10000000000001`. */
export function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100
}

function isPositive(value: number | null | undefined): value is number {
  return value !== null && value !== undefined && Number.isFinite(value) && value > 0
}

/** The gross traded value, `quantity x unitPrice`; `null` unless both are positive numbers. */
export function grossFromQuantityAndPrice(
  quantity: number | null,
  unitPrice: number | null,
): number | null {
  if (!isPositive(quantity) || !isPositive(unitPrice)) return null
  return round2(quantity * unitPrice)
}

/**
 * The cash that moves: buy `quantity x price + taxes`, sell `quantity x price - taxes`. `null`
 * until quantity and price are both given, so the form leaves `amount` alone.
 */
export function tradeTotal(
  direction: TradeDirection,
  quantity: number | null,
  unitPrice: number | null,
  taxes: number | null,
): number | null {
  const gross = grossFromQuantityAndPrice(quantity, unitPrice)
  if (gross === null) return null
  const fee = taxes !== null && Number.isFinite(taxes) ? taxes : 0
  return round2(direction === 'buy' ? gross + fee : gross - fee)
}

/**
 * The value traded before taxes: `quantity x price` when both are given, otherwise backed out of
 * `amount` (buy: `amount - taxes`; sell: `amount + taxes`).
 */
export function grossTradedValue(
  direction: TradeDirection,
  amount: number,
  taxes: number | null,
  quantity: number | null,
  unitPrice: number | null,
): number {
  const fromUnits = grossFromQuantityAndPrice(quantity, unitPrice)
  if (fromUnits !== null) return fromUnits
  const fee = taxes !== null && Number.isFinite(taxes) ? taxes : 0
  return round2(direction === 'buy' ? amount - fee : amount + fee)
}

/**
 * The suggested resulting balance: the prior latest snapshot moved by the gross traded value (up
 * for a buy, down - never below zero - for a sell). The user edits it to the broker's real balance.
 */
export function suggestedResultingBalance(
  direction: TradeDirection,
  latestBalance: number,
  gross: number,
): number {
  return round2(direction === 'buy' ? latestBalance + gross : Math.max(0, latestBalance - gross))
}
