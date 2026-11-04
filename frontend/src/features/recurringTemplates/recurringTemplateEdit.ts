export interface RecurringTemplateCreateForm {
  categoryId: string
  accountId: string
  description: string
  amount: string
  dayOfMonth: string
}

export const EMPTY_RECURRING_TEMPLATE_CREATE_FORM: RecurringTemplateCreateForm = {
  categoryId: '',
  accountId: '',
  description: '',
  amount: '',
  dayOfMonth: '1',
}

/** A recurring template's amount/day-of-month cap edit is valid once the amount is positive and the
 * day of month is within a real month's range (F007 spec). */
export function isRecurringTemplateEditValid(amount: string, dayOfMonth: string): boolean {
  return Number(amount) > 0 && Number(dayOfMonth) >= 1 && Number(dayOfMonth) <= 31
}
