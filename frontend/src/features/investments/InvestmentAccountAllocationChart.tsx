import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation('investments')
  return (
    <FlatAllocationDonutChart
      groupBy="ACCOUNT"
      ariaLabel={t('accountChart.ariaLabel')}
      getKey={(row: AllocationRow) => row.accountId ?? ''}
      getLabel={(row: AllocationRow) => row.accountName ?? t('accountChart.unknownAccount')}
      emptyMessage={t('accountChart.emptyValues')}
      staleFootnote={t('accountChart.staleFootnote')}
    />
  )
}
