import type { components } from './generated/schema'
import type { Account } from './accounts'
import type { Budget, BudgetReportLine } from './budgets'
import type { Category } from './categories'
import type { Institution } from './institutions'
import type { InvestmentCategory, InvestmentSubcategoryEntry } from './investmentCategories'
import type { AllocationRow } from './investmentAllocation'
import type { InvestmentProduct } from './investmentProducts'
import type { InvestmentSnapshot } from './investmentSnapshots'
import type { InvestmentSubcategory } from './investmentSubcategories'
import type { ProductValueSeries, ValueSeriesPoint } from './investmentValueSeries'
import type { PaymentMethod } from './paymentMethods'
import type { PendingRecurringOccurrence, RecurringTemplate } from './recurringTemplates'
import type { Transaction, TransactionPage } from './transactions'
import type { Transfer, TransferPage } from './transfers'

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
export type InvestmentProductLatestSnapshotKeys = Assert<
  SameKeys<NonNullable<InvestmentProduct['latestSnapshot']>, Schemas['LatestSnapshotResponse']>
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
export type PaymentMethodKeys = Assert<SameKeys<PaymentMethod, Schemas['PaymentMethodResponse']>>
export type PendingRecurringOccurrenceKeys = Assert<
  SameKeys<PendingRecurringOccurrence, Schemas['PendingRecurringOccurrenceResponse']>
>
export type RecurringTemplateKeys = Assert<
  SameKeys<RecurringTemplate, Schemas['RecurringTemplateResponse']>
>
export type TransactionKeys = Assert<SameKeys<Transaction, Schemas['TransactionResponse']>>
export type TransferKeys = Assert<SameKeys<Transfer, Schemas['TransferResponse']>>
// The paged envelopes: the wrapper, and its nested `page` metadata.
export type TransactionPageKeys = Assert<
  SameKeys<TransactionPage, Schemas['PagedModelTransactionResponse']>
>
export type TransferPageKeys = Assert<SameKeys<TransferPage, Schemas['PagedModelTransferResponse']>>
export type PageMetadataKeys = Assert<SameKeys<TransactionPage['page'], Schemas['PageMetadata']>>
