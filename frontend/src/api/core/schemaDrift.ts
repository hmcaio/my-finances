import type { components } from '../generated/schema'
import type { Account } from '../accounts/accounts'
import type { Budget, BudgetReportLine } from '../budgets/budgets'
import type { Category } from '../categories/categories'
import type { Institution } from '../institutions/institutions'
import type {
  InvestmentCategory,
  InvestmentSubcategoryEntry,
} from '../investments/investmentCategories'
import type { AllocationRow } from '../investments/investmentAllocation'
import type { InvestmentHolding } from '../investments/investmentHoldings'
import type { InvestmentProduct, InvestmentProductPage } from '../investments/investmentProducts'
import type { InvestmentSnapshot } from '../investments/investmentSnapshots'
import type { InvestmentSubcategory } from '../investments/investmentSubcategories'
import type { ProductValueSeries, ValueSeriesPoint } from '../investments/investmentValueSeries'
import type { NetWorthPoint } from '../netWorth/netWorth'
import type { PaymentMethod } from '../paymentMethods/paymentMethods'
import type {
  PendingRecurringOccurrence,
  RecurringTemplate,
} from '../recurringTemplates/recurringTemplates'
import type { CategorySpend, Transaction, TransactionPage } from '../transactions/transactions'
import type { Transfer, TransferPage } from '../transfers/transfers'

/**
 * Compile-time drift check between the hand-written response interfaces in `src/api/*.ts` (which
 * the pages and MSW handlers use, with the non-optional fields springdoc doesn't emit `required`
 * for) and the generated `schema.ts`. Each `Assert<SameKeys<...>>` below fails `npm run build`
 * (tsc) when a field exists on one side only - e.g. a new backend field that nobody added to the
 * interface, or a stale field left on it. Types only; nothing here exists at runtime, and the
 * file is not imported anywhere (tsc still checks it, being under `src`).
 *
 * Only the key sets are compared, not field types: the interfaces deliberately narrow the
 * generated `T | undefined` to `T` (and add `| null`). If a check fails, regenerate the schema
 * (`npm run generate-api-types`) and update the interface.
 *
 * Deliberately not covered: `HealthResponse` (the health endpoint isn't in the OpenAPI schema),
 * `TransactionFilter`/`TransferFilter` (query params, not a response schema) and the request types
 * (already aliases of the generated ones).
 */

type Assert<T extends true> = T
type SameKeys<A, B> = [keyof A] extends [keyof B]
  ? [keyof B] extends [keyof A]
    ? true
    : false
  : false

type Schemas = components['schemas']

export type AccountKeys = Assert<SameKeys<Account, Schemas['AccountResponse']>>
export type BudgetKeys = Assert<SameKeys<Budget, Schemas['BudgetResponse']>>
export type BudgetReportLineKeys = Assert<
  SameKeys<BudgetReportLine, Schemas['BudgetReportLineResponse']>
>
export type CategoryKeys = Assert<SameKeys<Category, Schemas['CategoryResponse']>>
export type InstitutionKeys = Assert<SameKeys<Institution, Schemas['InstitutionResponse']>>
export type InvestmentCategoryKeys = Assert<
  SameKeys<InvestmentCategory, Schemas['InvestmentCategoryResponse']>
>
export type InvestmentSubcategoryEntryKeys = Assert<
  SameKeys<InvestmentSubcategoryEntry, Schemas['InvestmentSubcategoryEntry']>
>
export type InvestmentProductKeys = Assert<
  SameKeys<InvestmentProduct, Schemas['InvestmentProductResponse']>
>
export type InvestmentProductPageKeys = Assert<
  SameKeys<InvestmentProductPage, Schemas['PagedModelInvestmentProductResponse']>
>
export type InvestmentHoldingKeys = Assert<
  SameKeys<InvestmentHolding, Schemas['InvestmentHoldingResponse']>
>
export type InvestmentHoldingLatestSnapshotKeys = Assert<
  SameKeys<NonNullable<InvestmentHolding['latestSnapshot']>, Schemas['LatestSnapshotResponse']>
>
export type InvestmentSnapshotKeys = Assert<
  SameKeys<InvestmentSnapshot, Schemas['InvestmentSnapshotResponse']>
>
export type AllocationRowKeys = Assert<SameKeys<AllocationRow, Schemas['AllocationRowResponse']>>
export type ProductValueSeriesKeys = Assert<
  SameKeys<ProductValueSeries, Schemas['ProductSeriesResponse']>
>
export type ValueSeriesPointKeys = Assert<
  SameKeys<ValueSeriesPoint, Schemas['SeriesPointResponse']>
>
export type InvestmentSubcategoryKeys = Assert<
  SameKeys<InvestmentSubcategory, Schemas['InvestmentSubcategoryResponse']>
>
export type NetWorthPointKeys = Assert<SameKeys<NetWorthPoint, Schemas['NetWorthPointResponse']>>
export type PaymentMethodKeys = Assert<SameKeys<PaymentMethod, Schemas['PaymentMethodResponse']>>
export type PendingRecurringOccurrenceKeys = Assert<
  SameKeys<PendingRecurringOccurrence, Schemas['PendingRecurringOccurrenceResponse']>
>
export type RecurringTemplateKeys = Assert<
  SameKeys<RecurringTemplate, Schemas['RecurringTemplateResponse']>
>
export type CategorySpendKeys = Assert<SameKeys<CategorySpend, Schemas['CategorySpendResponse']>>
export type TransactionKeys = Assert<SameKeys<Transaction, Schemas['TransactionResponse']>>
export type TransferKeys = Assert<SameKeys<Transfer, Schemas['TransferResponse']>>
// The paged envelopes: the wrapper, and its nested `page` metadata.
export type TransactionPageKeys = Assert<
  SameKeys<TransactionPage, Schemas['PagedModelTransactionResponse']>
>
export type TransferPageKeys = Assert<SameKeys<TransferPage, Schemas['PagedModelTransferResponse']>>
export type PageMetadataKeys = Assert<SameKeys<TransactionPage['page'], Schemas['PageMetadata']>>
