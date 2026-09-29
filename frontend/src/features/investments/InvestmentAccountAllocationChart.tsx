import type { AllocationRow } from '../../api/investments/investmentAllocation'
import { FlatAllocationDonutChart } from './FlatAllocationDonutChart'

/**
 * Allocation by account (F023 spec, enabled by F022's holdings): each holding's own latest
 * snapshot summed into its account, flat with no drill-down. Sums to the same portfolio total as
 * the category and sub-category charts.
 *
 * A prop-less widget that fetches its own data on mount, like `InvestmentAllocationChart`.
 */
export function InvestmentAccountAllocationChart() {
  return (
    <FlatAllocationDonutChart
      groupBy="ACCOUNT"
      ariaLabel="Allocation by account"
      getKey={(row: AllocationRow) => row.accountId ?? ''}
      getLabel={(row: AllocationRow) => row.accountName ?? 'Unknown account'}
      emptyMessage="No investment values yet - record a snapshot on a holding to see the allocation by account."
      staleFootnote="* Includes a holding with a buy or sell newer than its latest snapshot, so this value may be out of date. Record a snapshot on it to refresh it."
    />
  )
}
