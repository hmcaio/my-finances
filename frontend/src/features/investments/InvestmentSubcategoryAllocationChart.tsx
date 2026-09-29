import type { AllocationRow } from '../../api/investments/investmentAllocation'
import { FlatAllocationDonutChart } from './FlatAllocationDonutChart'

/**
 * Sub-category allocation, flat across every category, with no drill-down (F023 spec's
 * "Decisions": a different question from the category chart's drill-down - "each sub-category's
 * share of the whole portfolio", not "this category's sub-categories"). A product without a
 * sub-category still contributes its own "No sub-category" slice, one per category (same
 * `SUBCATEGORY` grouping rows `InvestmentAllocationChart`'s drill-down already fetches), so its
 * total matches the sum of every category's total.
 *
 * A prop-less widget that fetches its own data on mount, like `InvestmentAllocationChart`.
 */
export function InvestmentSubcategoryAllocationChart() {
  return (
    <FlatAllocationDonutChart
      groupBy="SUBCATEGORY"
      ariaLabel="Allocation by sub-category"
      getKey={(row: AllocationRow) => `${row.categoryId}/${row.subcategoryId ?? ''}`}
      getLabel={(row: AllocationRow) => row.subcategoryName ?? 'No sub-category'}
      emptyMessage="No investment values yet - record a snapshot on a product to see the allocation."
      staleFootnote="* Includes a product with a buy or sell newer than its latest snapshot, so this value may be out of date. Record a snapshot on the product to refresh it."
    />
  )
}
