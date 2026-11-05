/**
 * The retirement target helper (PRD S6.17, F039): the portfolio that pays `monthlyIncome` a month
 * when `withdrawalRatePercent` of it is withdrawn each year, i.e. `income x 12 / (rate / 100)`
 * (the "4% rule" at the default rate). Pure arithmetic with no data behind it, so it lives here
 * and not behind an endpoint; it only fills the target field and nothing is stored. Rounded to
 * cents; `null` when either input is not a positive finite number.
 */
export function retirementTarget(
  monthlyIncome: number,
  withdrawalRatePercent: number,
): number | null {
  if (!Number.isFinite(monthlyIncome) || !Number.isFinite(withdrawalRatePercent)) return null
  if (monthlyIncome <= 0 || withdrawalRatePercent <= 0) return null
  // Multiply before dividing: income x 12 x 100 / rate keeps 8000 at 4% exactly 2,400,000.
  return Math.round(((monthlyIncome * 12 * 100) / withdrawalRatePercent) * 100) / 100
}
