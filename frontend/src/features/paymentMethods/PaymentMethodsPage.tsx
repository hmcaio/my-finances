import { useTranslation } from 'react-i18next'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import { ReadOnlyNameListPage } from '../../components/table/ReadOnlyNameListPage'
import { translateSeedName } from '../../i18n/seedNames'

/**
 * Read-only list of payment methods (F002 spec, F034/ADR 0028): every row is fixed,
 * migration-seeded content (Debit Card, Credit Card, PIX, Cash) with no create/rename/delete
 * affordance anywhere - the backend's write endpoints were removed outright.
 */
export function PaymentMethodsPage() {
  const { t } = useTranslation('paymentMethods')
  return (
    <ReadOnlyNameListPage
      title={t('title')}
      description={t('description')}
      emptyText={t('emptyList')}
      query={usePaymentMethods()}
      displayName={(name) => translateSeedName('paymentMethods', name, t)}
    />
  )
}
