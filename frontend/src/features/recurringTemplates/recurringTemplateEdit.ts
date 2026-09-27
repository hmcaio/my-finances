/** A recurring template's amount/day-of-month cap edit is valid once the amount is positive and the
 * day of month is within a real month's range (F007 spec). */
export function isRecurringTemplateEditValid(amount: string, dayOfMonth: string): boolean {
  return Number(amount) > 0 && Number(dayOfMonth) >= 1 && Number(dayOfMonth) <= 31
}
