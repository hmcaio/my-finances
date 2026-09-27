/** A budget cap edit (new budget or "resume"/"edit cap" on an existing one) is valid once the
 * monthly cap is a positive number. */
export function isBudgetCapValid(cap: string): boolean {
  return Number(cap) > 0
}
