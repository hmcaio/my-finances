import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { Alert, Box, Chip, Link as MuiLink, Paper, Skeleton, Typography } from '@mui/material'
import { useAccount } from '../../api/accounts/accountsQueries'
import { defaultErrorMessage } from '../../api/core/apiError'
import { useInstitutions } from '../../api/institutions/institutionsQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { combineLoadState, useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { translateSeedName } from '../../i18n/seedNames'
import { nameLookup } from '../../utils/nameLookup'
import { accountTypeLabel } from './accountTypes'
import { InvestmentProductsSection } from '../investmentProducts/InvestmentProductsSection'
import { AccountTransactionList } from '../transactions/AccountTransactionList'
import { AccountTransferList } from '../transfers/AccountTransferList'

/** Section padding: tighter on phones so the embedded lists keep their width (F021). */
const SECTION_PADDING = { xs: 2, sm: 3 }

/**
 * Account detail view (F003 spec): running balance plus account fields, plus F004's transaction
 * history and F005's transfer history, both embedded and pre-filtered to this account (F004 spec:
 * "Account detail view (F003) embeds this feature's list, pre-filtered to that account"; F005
 * spec: "Account detail view (F003) embeds transfer history alongside transaction history, both
 * contributing to the same running-balance timeline").
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
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
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
            <Typography variant="h4" component="h1" sx={{ overflowWrap: 'anywhere' }}>
              {account.name}
            </Typography>
            {account.closed ? (
              <Chip label={t('accounts:status.closed')} />
            ) : (
              <Chip label={t('accounts:status.open')} color="success" />
            )}
          </Box>
          <Typography color="text.secondary" sx={{ mb: 3, overflowWrap: 'anywhere' }}>
            {institutionName(account.institutionId)} &middot; {accountTypeLabel(account.type, t)}
          </Typography>

          <Paper variant="outlined" sx={{ p: SECTION_PADDING, mb: 3, maxWidth: 480 }}>
            <Typography variant="overline" color="text.secondary">
              {t('accounts:detail.runningBalance')}
            </Typography>
            <Typography variant="h3" sx={{ mb: 2, overflowWrap: 'anywhere' }}>
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
          </Paper>

          {account.type === 'INVESTMENT' ? (
            // An investment account takes no transactions (money moves through transfers), so its
            // products are listed instead (F008 spec).
            <Paper variant="outlined" sx={{ p: SECTION_PADDING, mb: 3 }}>
              <Typography variant="h6" gutterBottom>
                {t('accounts:detail.products')}
              </Typography>
              <InvestmentProductsSection accountId={account.id} accountClosed={account.closed} />
            </Paper>
          ) : (
            <Paper variant="outlined" sx={{ p: SECTION_PADDING, mb: 3 }}>
              <Typography variant="h6" gutterBottom>
                {t('accounts:detail.transactions')}
              </Typography>
              <AccountTransactionList accountId={account.id} />
            </Paper>
          )}

          <Paper variant="outlined" sx={{ p: SECTION_PADDING }}>
            <Typography variant="h6" gutterBottom>
              {t('accounts:detail.transfers')}
            </Typography>
            <AccountTransferList accountId={account.id} />
          </Paper>
        </Box>
      )}
    </Box>
  )
}

/** Placeholder laid out like the loaded page: header, subtitle, balance card, two list sections. */
function AccountDetailSkeleton() {
  const { t } = useTranslation('accounts')
  return (
    <Box role="status" aria-label={t('detail.loading')}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2, mb: 1 }}>
        <Skeleton variant="text" width={240} sx={{ typography: 'h4', maxWidth: '60%' }} />
        <Skeleton variant="rounded" width={60} height={32} />
      </Box>
      <Skeleton variant="text" width={200} sx={{ mb: 3 }} />

      <Paper variant="outlined" sx={{ p: SECTION_PADDING, mb: 3, maxWidth: 480 }}>
        <Skeleton variant="text" width={110} sx={{ typography: 'overline' }} />
        <Skeleton variant="text" width={180} sx={{ typography: 'h3', mb: 2 }} />
        <Skeleton variant="text" width="80%" sx={{ typography: 'body2' }} />
      </Paper>

      {[0, 1].map((section) => (
        <Paper
          key={section}
          variant="outlined"
          sx={{ p: SECTION_PADDING, mb: section === 0 ? 3 : 0 }}
        >
          <Skeleton variant="text" width={120} sx={{ typography: 'h6', mb: 1 }} />
          <Skeleton variant="rounded" height={160} />
        </Paper>
      ))}
    </Box>
  )
}
