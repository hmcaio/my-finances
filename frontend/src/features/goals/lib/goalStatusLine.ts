/**
 * The one base-scenario status line of a goal on the list and the dashboard card (PRD S6.17,
 * F039), as data so the component can translate it. A dated goal is "on track" when its projected
 * shortfall at the target date is zero or a surplus, else "short by X at <date>"; a dateless goal
 * says when it is reached ("reached" if it already is) or that it is not within 50 years.
 */
export type StatusLine =
  | { kind: 'onTrack' }
  | { kind: 'short'; amount: number; date: string }
  | { kind: 'reached' }
  | { kind: 'reachedInMonths'; months: number }
  | { kind: 'notWithin50Years' }

export function goalStatusLine(goal: {
  targetDate: string | null
  projectedShortfall: number | null
  monthsToTarget: number | null
}): StatusLine {
  if (goal.targetDate !== null) {
    const shortfall = goal.projectedShortfall ?? 0
    return shortfall > 0
      ? { kind: 'short', amount: shortfall, date: goal.targetDate }
      : { kind: 'onTrack' }
  }
  if (goal.monthsToTarget === null) return { kind: 'notWithin50Years' }
  return goal.monthsToTarget === 0
    ? { kind: 'reached' }
    : { kind: 'reachedInMonths', months: goal.monthsToTarget }
}
