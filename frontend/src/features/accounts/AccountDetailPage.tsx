import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { Alert, Box, Chip, Link as MuiLink, Skeleton, Typography } from '@mui/material'
import { useAccount } from '../../api/accounts/accountsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInstitutions } from '../../api/institutions/institutionsQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useLocale } from '../../providers/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'
import { accountTypeLabel } from './accountTypes'
import { AccountInvestmentProductsSection } from '../investmentProducts/AccountInvestmentProductsSection'
import { AccountLedger } from '../ledger/AccountLedger'
import { PageContainer } from '../../components/layout/PageContainer'
import { PageSection } from '../../components/layout/PageSection'
import { WRAP_ANYWHERE } from '../../components/layout/layoutSx'

/** Section padding: tighter on phones so the embedded lists keep their width (F021). */

/**
 * Account detail view (F003 spec): running balance plus account fields, plus one embedded,
 * read-only Ledger section (F037, replacing F004's transaction list and F005's transfer list)
 * showing this account's transactions and transfers month by month. An `INVESTMENT` account keeps
 * its products section above it (it takes no transactions, so its ledger lists only transfers).
 */
export function AccountDetailPage() {
  const { t } = useTranslation(['accounts', 'common'])
  const { locale } = useLocale()
  const { id } = useParams<{ id: string }>()
  const accountQuery = useAccount(id)
  const account = accountQuery.data
  const accountState = useQueryState(accountQuery, undefined, (err) =>
    defaultErrorMessage(err, { 404: t('accounts:detail.notFound') }),
  )
  const institutionsQuery = useInstitutions()
  const institutions = institutionsQuery.data
  const institutionsState = useQueryState(institutionsQuery)
  const institutionName = useMemo(
    () => nameLookup(institutions ?? [], (i) => translateSeedName('institutions', i.name, t)),
    [institutions, t],
  )
  // The header names the institution, so the page stays in its skeleton until both have loaded.
  const { loading, loadError: error } = combineLoadState(accountState, institutionsState)
  const showSkeleton = useDelayedFlag(loading)

  return (
    <PageContainer>
      <MuiLink component={RouterLink} to="/accounts" underline="hover">
        {t('accounts:detail.backLink')}
      </MuiLink>

      {error && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {error}
        </Alert>
      )}

      {showSkeleton && <AccountDetailSkeleton />}

      {account && institutions && (
        <Box sx={fadeInSx}>
          <Box
            sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 2, mt: 2, mb: 1 }}
          >
            <Typography variant="h4" component="h1" sx={WRAP_ANYWHERE}>
              {account.name}
            </Typography>
            {account.closed ? (
              <Chip label={t('accounts:status.closed')} />
            ) : (
              <Chip label={t('accounts:status.open')} color="success" />
            )}
          </Box>
          <Typography color="text.secondary" sx={{ mb: 3, ...WRAP_ANYWHERE }}>
            {institutionName(account.institutionId)} &middot; {accountTypeLabel(account.type, t)}
          </Typography>

          <PageSection sx={{ mb: 3, maxWidth: 480 }}>
            <Typography variant="overline" color="text.secondary">
              {t('accounts:detail.runningBalance')}
            </Typography>
            <Typography variant="h3" sx={{ mb: 2, ...WRAP_ANYWHERE }}>
              {formatMoney(account.balance, locale)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {account.openingBalance !== null && account.openingBalanceDate !== null
                ? t('accounts:detail.openingBalanceLine', {
                    amount: formatMoney(account.openingBalance, locale),
                    date: account.openingBalanceDate,
                  })
                : t('accounts:detail.noOpeningBalance')}
              {account.closedDate
                ? t('accounts:detail.closedSuffix', { date: account.closedDate })
                : ''}
            </Typography>
          </PageSection>

          {account.type === 'INVESTMENT' && (
            // An investment account's value comes from its products (F008 spec), listed above its
            // transfers (buys and sells).
            <PageSection sx={{ mb: 3 }}>
              <Typography variant="h6" gutterBottom>
                {t('accounts:detail.products')}
              </Typography>
              <AccountInvestmentProductsSection
                accountId={account.id}
                accountClosed={account.closed}
              />
            </PageSection>
          )}

          <PageSection>
            <Typography variant="h6" gutterBottom>
              {t('accounts:detail.ledger')}
            </Typography>
            <AccountLedger accountId={account.id} />
          </PageSection>
        </Box>
      )}
    </PageContainer>
  )
}

/** Placeholder laid out like the loaded page: header, subtitle, balance card, one list section. */
function AccountDetailSkeleton() {
  const { t } = useTranslation('accounts')
  return (
    <Box role="status" aria-label={t('detail.loading')}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2, mb: 1 }}>
        <Skeleton variant="text" width={240} sx={{ typography: 'h4', maxWidth: '60%' }} />
        <Skeleton variant="rounded" width={60} height={32} />
      </Box>
      <Skeleton variant="text" width={200} sx={{ mb: 3 }} />

      <PageSection sx={{ mb: 3, maxWidth: 480 }}>
        <Skeleton variant="text" width={110} sx={{ typography: 'overline' }} />
        <Skeleton variant="text" width={180} sx={{ typography: 'h3', mb: 2 }} />
        <Skeleton variant="text" width="80%" sx={{ typography: 'body2' }} />
      </PageSection>

      <PageSection>
        <Skeleton variant="text" width={120} sx={{ typography: 'h6', mb: 1 }} />
        <Skeleton variant="rounded" height={160} />
      </PageSection>
    </Box>
  )
}
