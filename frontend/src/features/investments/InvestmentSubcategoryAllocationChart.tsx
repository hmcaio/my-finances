import { useTranslation } from 'react-i18next'
import type { AllocationRow } from '../../api/investments/investmentAllocation'
import { translateSeedName } from '../../i18n/seedNames'
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
  const { t } = useTranslation('investments')
  return (
    <FlatAllocationDonutChart
      groupBy="SUBCATEGORY"
      ariaLabel={t('subcategoryChart.ariaLabel')}
      getKey={(row: AllocationRow) => `${row.categoryId}/${row.subcategoryId ?? ''}`}
      getLabel={(row: AllocationRow) =>
        row.subcategoryName
          ? translateSeedName('investmentSubcategories', row.subcategoryName, t)
          : t('subcategoryChart.noSubcategory')
      }
      emptyMessage={t('subcategoryChart.emptyValues')}
      staleFootnote={t('subcategoryChart.staleFootnote')}
    />
  )
}
