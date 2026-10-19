import { isAxiosError } from 'axios'
import i18n from '../../i18n/i18n'

/**
 * `PROBLEM_TYPE_MESSAGES`/`VALIDATION_CODE_MESSAGES` are deliberately `Record<string, string>`
 * (keyed by a backend-controlled URN/code, not a literal union this module should enumerate), so
 * their values don't type-check as one of `i18next`'s known key literals without a cast - this
 * wrapper centralizes that one cast instead of repeating it at each `i18n.t()` call site below.
 */
const translateUnsafe = i18n.t as (key: string, options?: Record<string, unknown>) => string

function translate(key: string, options?: Record<string, unknown>): string {
  return translateUnsafe(key, options)
}

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
  /** The raw bean-validation violation list (F032) when the response's `type` was
   * `method-argument-not-valid` and it carried an `errors` extension - `field` is the backend's
   * own (possibly nested/indexed, e.g. `tradeConfirmation.lines[0].quantity`) path, `null` for a
   * cross-field `@AssertTrue` invariant. This feature itself only folds the list into one summary
   * {@link message}; a future per-field `TextField` UI can read this passthrough without another
   * backend change. */
  readonly fieldErrors?: { field: string | null; code: string }[]

  constructor(
    status: number,
    message: string,
    type?: string,
    fieldErrors?: { field: string | null; code: string }[],
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.type = type
    this.fieldErrors = fieldErrors
  }
}

/**
 * Central `type → translation key` lookup (F031/ADR 0026, keys added by F033/ADR 0027), replacing
 * the 38 per-call-site `conflictMessage` hardcodes `unwrap()` used to take: every `type` the
 * backend can emit for a conflict/validation case that previously had one of those overrides.
 * Several distinct `type`s intentionally share the same key where the old code already bundled
 * their causes under one message (e.g. every cause of a settings page's delete-conflict 409); a
 * `type` collapsed across aggregate packages by the backend's own slug derivation
 * (`AccountClosedException` in `transfer`/`transaction`/`recurringtemplate`) gets exactly one
 * entry here too, for the same reason. A `type` absent from this map (bean-validation 400s, F032;
 * the generic `internal-error` 500; any 404) falls back to the generic
 * `Request failed with status {status}` string, same as before this feature. Resolved to text by
 * calling the `i18next` singleton's `t()` directly in {@link toApiError} (not the `useTranslation`
 * hook: this module has no React dependency and shouldn't gain one) - `i18next.t()` reads whatever
 * language is currently active, so the message is still correct no matter which locale triggered
 * the error.
 */
export const PROBLEM_TYPE_MESSAGES: Record<string, string> = {
  // Authentication (F036, ADR 0029). `unauthenticated` is the generic "no session" wording; the
  // login form overrides its own 401 with "incorrect username or password" (`statusOverrides`).
  'urn:my-finances:error:unauthenticated': 'errors:problemType.unauthenticated',
  'urn:my-finances:error:access-denied': 'errors:problemType.accessDenied',
  'urn:my-finances:error:too-many-login-attempts': 'errors:problemType.tooManyLoginAttempts',

  // A constraint directly on a controller parameter (Spring's built-in method validation, F037's
  // ledger search text): no field-level `errors` member, so this is the whole message.
  'urn:my-finances:error:handler-method-validation': 'errors:problemType.handlerMethodValidation',

  // Categories (F002, F024, F026)
  'urn:my-finances:error:category-name-already-exists':
    'errors:problemType.categoryNameAlreadyExists',
  'urn:my-finances:error:category-in-use': 'errors:problemType.categoryInUse',
  'urn:my-finances:error:built-in-category': 'errors:problemType.builtInCategory',
  'urn:my-finances:error:fuel-category': 'errors:problemType.fuelCategory',
  'urn:my-finances:error:dividend-category': 'errors:problemType.dividendCategory',

  // Accounts (F003, F008, ADR 0017)
  'urn:my-finances:error:account-name-already-exists':
    'errors:problemType.accountNameAlreadyExists',
  'urn:my-finances:error:account-already-closed': 'errors:problemType.accountAlreadyClosed',
  'urn:my-finances:error:investment-account-has-open-holdings':
    'errors:problemType.investmentAccountHasOpenHoldings',
  'urn:my-finances:error:account-has-history': 'errors:problemType.accountHasHistory',

  // Shared across the transaction/recurringtemplate/transfer packages (ADR 0026's deliberate
  // cross-aggregate-duplicate collapse: one real-world condition, one `type`, one message).
  'urn:my-finances:error:account-closed': 'errors:problemType.accountClosed',
  'urn:my-finances:error:account-type-not-allowed': 'errors:problemType.accountTypeNotAllowed',

  // Vehicles (F024)
  'urn:my-finances:error:vehicle-name-already-exists':
    'errors:problemType.vehicleNameAlreadyExists',
  'urn:my-finances:error:vehicle-in-use': 'errors:problemType.vehicleInUse',

  // Payment methods (F002)
  'urn:my-finances:error:payment-method-name-already-exists':
    'errors:problemType.paymentMethodNameAlreadyExists',
  'urn:my-finances:error:payment-method-in-use': 'errors:problemType.paymentMethodInUse',

  // Institutions (F017)
  'urn:my-finances:error:institution-name-already-exists':
    'errors:problemType.institutionNameAlreadyExists',
  'urn:my-finances:error:built-in-institution': 'errors:problemType.builtInInstitution',
  'urn:my-finances:error:institution-in-use': 'errors:problemType.institutionInUse',

  // Budgets (F006)
  'urn:my-finances:error:category-not-expense': 'errors:problemType.categoryNotExpense',
  'urn:my-finances:error:budget-already-exists': 'errors:problemType.budgetAlreadyExists',

  // Transfers (F005, F009, F027)
  'urn:my-finances:error:same-account-transfer': 'errors:problemType.sameAccountTransfer',
  'urn:my-finances:error:invalid-trade-confirmation': 'errors:problemType.invalidTradeConfirmation',
  'urn:my-finances:error:investment-transfer-invalid':
    'errors:problemType.investmentTransferInvalid',
  'urn:my-finances:error:investment-holding-closed': 'errors:problemType.investmentHoldingClosed',

  // Investment categories / sub-categories (F008)
  'urn:my-finances:error:investment-category-name-already-exists':
    'errors:problemType.investmentCategoryNameAlreadyExists',
  'urn:my-finances:error:investment-category-in-use': 'errors:problemType.investmentCategoryInUse',
  'urn:my-finances:error:investment-subcategory-name-already-exists':
    'errors:problemType.investmentSubcategoryNameAlreadyExists',
  'urn:my-finances:error:investment-subcategory-in-use':
    'errors:problemType.investmentSubcategoryInUse',

  // Investment products (F008, F022)
  'urn:my-finances:error:investment-product-name-already-exists':
    'errors:problemType.investmentProductNameAlreadyExists',
  'urn:my-finances:error:investment-account-required':
    'errors:problemType.investmentAccountRequired',
  'urn:my-finances:error:investment-subcategory-mismatch':
    'errors:problemType.investmentSubcategoryMismatch',
  'urn:my-finances:error:investment-product-has-holdings':
    'errors:problemType.investmentProductHasHoldings',

  // Investment holdings (F022)
  'urn:my-finances:error:investment-holding-already-exists':
    'errors:problemType.investmentHoldingAlreadyExists',
  'urn:my-finances:error:investment-holding-already-closed':
    'errors:problemType.investmentHoldingAlreadyClosed',
  'urn:my-finances:error:investment-holding-not-empty':
    'errors:problemType.investmentHoldingNotEmpty',
  'urn:my-finances:error:investment-holding-has-history':
    'errors:problemType.investmentHoldingHasHistory',

  // Investment snapshots (F009)
  'urn:my-finances:error:investment-snapshot-date-taken':
    'errors:problemType.investmentSnapshotDateTaken',
  'urn:my-finances:error:investment-snapshot-closed-holding':
    'errors:problemType.investmentSnapshotClosedHolding',

  // Investment segments (F026)
  'urn:my-finances:error:investment-segment-name-already-exists':
    'errors:problemType.investmentSegmentNameAlreadyExists',
  'urn:my-finances:error:investment-segment-in-use': 'errors:problemType.investmentSegmentInUse',

  // FII allocation plan (F026)
  'urn:my-finances:error:allocation-plan-entry-not-fii':
    'errors:problemType.allocationPlanEntryNotFii',
  'urn:my-finances:error:allocation-plan-sum-invalid':
    'errors:problemType.allocationPlanSumInvalid',
  'urn:my-finances:error:allocation-plan-duplicate-product':
    'errors:problemType.allocationPlanDuplicateProduct',
}

/**
 * Central `code → translation key` lookup for a bean-validation violation (F032, keys added by
 * F033/ADR 0027), mirroring {@link PROBLEM_TYPE_MESSAGES}'s pattern but keyed by the
 * `errors[].code` the backend derives from a Jakarta constraint annotation's simple name
 * (kebab-cased, `GlobalExceptionHandler`/`ProblemTypeSlugs`) - never by `field`, since the same
 * `code` recurs across many fields and forms (e.g. every mandatory text field fails `@NotBlank`).
 * A `code` absent from this map (a constraint not yet given its own wording, or one of this
 * codebase's per-invariant `@AssertTrue` codes like `at-most-one-resulting-balance-per-product`)
 * is simply skipped when building the joined summary in {@link toApiError} - if every code in a
 * response is unmapped, the summary falls back to the same generic
 * `Request failed with status {status}` text as an unmapped `type` (see
 * {@link PROBLEM_TYPE_MESSAGES}).
 */
export const VALIDATION_CODE_MESSAGES: Record<string, string> = {
  'not-blank': 'errors:validationCode.notBlank',
  'not-null': 'errors:validationCode.notNull',
  'not-empty': 'errors:validationCode.notEmpty',
  size: 'errors:validationCode.size',
  positive: 'errors:validationCode.positive',
  'positive-or-zero': 'errors:validationCode.positiveOrZero',
  digits: 'errors:validationCode.digits',
  'decimal-min': 'errors:validationCode.decimalMin',
  'decimal-max': 'errors:validationCode.decimalMax',
  min: 'errors:validationCode.min',
  max: 'errors:validationCode.max',
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
  return i18n.t('errors:defaultErrorMessage')
}

function toApiError(error: unknown): ApiError {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status === undefined) {
      // Request never reached the server (network error, CORS, backend down, ...).
      return new ApiError(0, error.message || i18n.t('errors:networkError'))
    }
    const data = error.response?.data as
      { type?: string; errors?: { field: string | null; code: string }[] } | undefined
    const type = data?.type
    const fieldErrors = data?.errors
    const message =
      fieldErrors && fieldErrors.length > 0
        ? summaryFor(fieldErrors, status)
        : (type && PROBLEM_TYPE_MESSAGES[type] && translate(PROBLEM_TYPE_MESSAGES[type])) ||
          i18n.t('errors:requestFailed', { status })
    return new ApiError(status, message, type, fieldErrors)
  }
  return new ApiError(0, error instanceof Error ? error.message : i18n.t('errors:unknownError'))
}

/**
 * F032: one joined summary string for the existing generic banner, built from every violation's
 * {@link VALIDATION_CODE_MESSAGES} entry - deduplicated, since several fields commonly share one
 * `code` (e.g. two mandatory text fields both failing `@NotBlank`) and repeating the same sentence
 * would just be noise. Falls back to the same generic `Request failed with status {status}` text
 * used for an unmapped `type` when none of the violations' codes are in the map.
 */
function summaryFor(errors: { field: string | null; code: string }[], status: number): string {
  const messages = [
    ...new Set(
      errors
        .map((e) => VALIDATION_CODE_MESSAGES[e.code])
        .filter(Boolean)
        .map((key) => translate(key)),
    ),
  ]
  return messages.length > 0 ? messages.join(' ') : i18n.t('errors:requestFailed', { status })
}
