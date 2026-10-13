import { isAxiosError } from 'axios'

/**
 * Shared error type for every typed API client under `src/api` (established here in F002, reused
 * by F003+). Callers can inspect {@link ApiError.status} (e.g. to detect a `409` delete-conflict,
 * F002 spec), {@link ApiError.type} (the backend's RFC 9457 `type` identifier, F031/ADR 0026, when
 * the response carried one) or just show {@link ApiError.message}.
 */
export class ApiError extends Error {
  readonly status: number
  /** The backend's `urn:my-finances:error:<slug>` identifier (F031/ADR 0026), when present - lets
   * a screen that genuinely needs different phrasing for the same `type` branch on it, the ADR's
   * own escape hatch now that `unwrap()` no longer takes a per-call-site override. */
  readonly type?: string

  constructor(status: number, message: string, type?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.type = type
  }
}

/**
 * Central `type → message` lookup (F031/ADR 0026), replacing the 38 per-call-site `conflictMessage`
 * hardcodes `unwrap()` used to take: every `type` the backend can emit for a conflict/validation
 * case that previously had one of those overrides. Several distinct `type`s intentionally share
 * the same text where the old code already bundled their causes under one message (e.g. every
 * cause of a settings page's delete-conflict 409); a `type` collapsed across aggregate packages by
 * the backend's own slug derivation (`AccountClosedException` in `transfer`/`transaction`/
 * `recurringtemplate`) gets exactly one entry here too, for the same reason. A `type` absent from
 * this map (bean-validation 400s, F032; the generic `internal-error` 500; any 404) falls back to
 * the generic `Request failed with status {status}` string, same as before this feature.
 */
export const PROBLEM_TYPE_MESSAGES: Record<string, string> = {
  // Categories (F002, F024, F026)
  'urn:my-finances:error:category-name-already-exists': 'A category with this name already exists.',
  'urn:my-finances:error:category-in-use':
    'This category is used by existing transactions, budgets, or recurring templates — reassign them before deleting it.',
  'urn:my-finances:error:built-in-category': 'A built-in category cannot be deleted.',
  'urn:my-finances:error:fuel-category':
    'The dedicated fuel category cannot be renamed or deleted.',
  'urn:my-finances:error:dividend-category':
    'The dedicated dividend category cannot be renamed or deleted.',

  // Accounts (F003, F008, ADR 0017)
  'urn:my-finances:error:account-name-already-exists': 'An account with this name already exists.',
  'urn:my-finances:error:account-already-closed':
    'This account could not be closed: it is already closed, or it is an investment account that still has open products - close those first.',
  'urn:my-finances:error:investment-account-has-open-holdings':
    'This investment account still has open products - close those first.',
  'urn:my-finances:error:account-has-history':
    'This account has history (transactions, transfers, recurring templates or investment products) and cannot be deleted - close it instead.',

  // Shared across the transaction/recurringtemplate/transfer packages (ADR 0026's deliberate
  // cross-aggregate-duplicate collapse: one real-world condition, one `type`, one message).
  'urn:my-finances:error:account-closed':
    'This account is closed and cannot accept new transactions.',
  'urn:my-finances:error:account-type-not-allowed': 'An investment account cannot be used here.',

  // Vehicles (F024)
  'urn:my-finances:error:vehicle-name-already-exists': 'A vehicle with this name already exists.',
  'urn:my-finances:error:vehicle-in-use':
    'This vehicle is used by existing fuel transactions — reassign them before deleting it.',

  // Payment methods (F002)
  'urn:my-finances:error:payment-method-name-already-exists':
    'A payment method with this name already exists.',
  'urn:my-finances:error:payment-method-in-use':
    'This payment method is used by existing transactions — reassign them before deleting it.',

  // Institutions (F017)
  'urn:my-finances:error:institution-name-already-exists':
    'An institution with this name already exists.',
  'urn:my-finances:error:built-in-institution': 'The built-in institution cannot be deleted.',
  'urn:my-finances:error:institution-in-use':
    'This institution is still used by an account — move its accounts to another institution before deleting it.',

  // Budgets (F006)
  'urn:my-finances:error:category-not-expense':
    'This category cannot be budgeted - it may already have a budget, or not be an expense category.',
  'urn:my-finances:error:budget-already-exists': 'This category already has a budget.',

  // Transfers (F005, F009, F027)
  'urn:my-finances:error:investment-transfer-invalid':
    'The investment side of this transfer must be an INVESTMENT account, and the other side must not be.',
  'urn:my-finances:error:investment-holding-closed':
    'Every product in this transfer needs an existing open holding in the investment account.',

  // Investment categories / sub-categories (F008)
  'urn:my-finances:error:investment-category-name-already-exists':
    'An investment category with this name already exists.',
  'urn:my-finances:error:investment-category-in-use':
    'This category still has sub-categories or is used by an investment product - delete or reclassify those first.',
  'urn:my-finances:error:investment-subcategory-name-already-exists':
    'This category already has a sub-category with this name.',
  'urn:my-finances:error:investment-subcategory-in-use':
    'This sub-category is used by an investment product - reclassify those products before deleting it.',

  // Investment products (F008, F022)
  'urn:my-finances:error:investment-product-name-already-exists':
    'The product could not be saved: its name must already be unique, the account must be an open investment account, and the sub-category must belong to the chosen category.',
  'urn:my-finances:error:investment-account-required':
    'The account must be an open investment account.',
  'urn:my-finances:error:investment-subcategory-mismatch':
    'The sub-category must belong to the chosen category.',
  'urn:my-finances:error:investment-product-has-holdings':
    'This product still has a holding (even a closed, empty one) and cannot be deleted - remove its holdings first.',

  // Investment holdings (F022)
  'urn:my-finances:error:investment-holding-already-exists':
    'This holding could not be saved: the account must be an open investment account, and this product already has a holding in it.',
  'urn:my-finances:error:investment-holding-already-closed':
    'This holding could not be closed: it is already closed, or its latest snapshot still has value. Record a zero snapshot (or sell the entire position) first.',
  'urn:my-finances:error:investment-holding-not-empty':
    "This holding's latest snapshot still has value - record a zero snapshot (or sell the entire position) first.",
  'urn:my-finances:error:investment-holding-has-history':
    'This holding has history (snapshots or trades) and cannot be deleted - close it instead.',

  // Investment snapshots (F009)
  'urn:my-finances:error:investment-snapshot-date-taken':
    'Could not save: another snapshot already has that date, or the holding is closed and this would leave it with a non-zero latest snapshot.',
  'urn:my-finances:error:investment-snapshot-closed-holding':
    'Could not delete: the holding is closed and its latest snapshot would no longer be zero.',

  // Investment segments (F026)
  'urn:my-finances:error:investment-segment-name-already-exists':
    'An investment segment with this name already exists.',
  'urn:my-finances:error:investment-segment-in-use':
    'This segment is still used by an investment product - reclassify it first.',

  // FII allocation plan (F026)
  'urn:my-finances:error:allocation-plan-entry-not-fii':
    'Every entry must be a product classified under the "REITs (FIIs)" sub-category.',
}

/**
 * Runs an Axios call and unwraps its `data` on success. On failure, converts the `AxiosError`
 * (Axios throws on any non-2xx by default, unlike `fetch`) into an {@link ApiError} whose message
 * comes from {@link PROBLEM_TYPE_MESSAGES} when the response's RFC 9457 `type` is one it covers,
 * or the generic `Request failed with status {status}` string otherwise (F031/ADR 0026 - replaces
 * the old per-call-site `conflictMessage` parameter).
 */
export async function unwrap<T>(request: Promise<{ data: T }>): Promise<T> {
  try {
    const response = await request
    return response.data
  } catch (error) {
    throw toApiError(error)
  }
}

/**
 * Shared "turn a caught error into UI-displayable text" fallback, previously copy-pasted (almost
 * always identically) into nearly every feature page's own local `errorMessage` function. `status`
 * overrides a specific `ApiError.status` with a friendlier message (e.g. a 404 that means "this
 * record was deleted elsewhere") - the caller's own message otherwise wins for `ApiError`, and a
 * generic fallback covers anything that isn't even an `Error`.
 */
export function defaultErrorMessage(
  err: unknown,
  statusOverrides?: Record<number, string>,
): string {
  if (err instanceof ApiError) {
    const override = statusOverrides?.[err.status]
    if (override) return override
    return err.message
  }
  if (err instanceof Error) return err.message
  return 'Something went wrong.'
}

function toApiError(error: unknown): ApiError {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status === undefined) {
      // Request never reached the server (network error, CORS, backend down, ...).
      return new ApiError(0, error.message || 'Network error')
    }
    const type = (error.response?.data as { type?: string } | undefined)?.type
    const message = (type && PROBLEM_TYPE_MESSAGES[type]) || `Request failed with status ${status}`
    return new ApiError(status, message, type)
  }
  return new ApiError(0, error instanceof Error ? error.message : 'Unknown error')
}
