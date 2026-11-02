import { useTranslation } from 'react-i18next'
import { useInvestmentSegments } from '../../api/investments/investmentSegmentsQueries'
import { ReadOnlyNameListPage } from '../../components/table/ReadOnlyNameListPage'
import { translateSeedName } from '../../i18n/seedNames'

/**
 * Read-only list of investment segments (F026 spec, ADR 0023; F034/ADR 0028): every row is fixed,
 * migration-seeded content (the B3 FII sectors) with no create/rename/delete affordance
 * anywhere - the backend's write endpoints were removed outright.
 */
export function InvestmentSegmentsPage() {
  const { t } = useTranslation('investmentSegments')
  return (
    <ReadOnlyNameListPage
      title={t('title')}
      description={t('description')}
      emptyText={t('emptyList')}
      query={useInvestmentSegments()}
      displayName={(name) => translateSeedName('investmentSegments', name, t)}
    />
  )
}
