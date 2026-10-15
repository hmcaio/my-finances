import { useTranslation } from 'react-i18next'
import {
  Box,
  Link as MuiLink,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Typography,
} from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { useAccounts } from '../../api/accounts/accountsQueries'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { useQueryState } from '../../hooks/queryState'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { accountTypeLabel } from '../accounts/accountTypes'

/**
 * Balances of every open account (F012 spec, from F003's account list), each linking to its detail
 * page. A credit card's figure is the amount owed, as on the accounts page. A prop-less widget that
 * fetches its own data on mount (frontend `CLAUDE.md`, "Pages that embed other pages' widgets").
 */
export function AccountBalancesWidget() {
  const { t } = useTranslation(['dashboard', 'accounts'])
  const { locale } = useLocale()
  const accountsQuery = useAccounts()
  const accounts = accountsQuery.data
  const { loading, loadError, reload } = useQueryState(accountsQuery)
  const showSkeleton = useDelayedFlag(loading)

  if (loadError) return <LoadFailedNotice message={loadError} onRetry={reload} />
  if (accounts === undefined) {
    return showSkeleton ? (
      <Box role="status" aria-label={t('dashboard:accountBalances.loadingAria')}>
        {[0, 1, 2].map((row) => (
          <Skeleton key={row} variant="text" sx={{ typography: 'body2' }} />
        ))}
      </Box>
    ) : null
  }
  if (accounts.length === 0)
    return (
      <Typography color="text.secondary">{t('dashboard:accountBalances.emptyList')}</Typography>
    )

  return (
    <Table size="small" sx={fadeInSx}>
      <TableBody>
        {accounts.map((account) => (
          <TableRow key={account.id}>
            <TableCell>
              <MuiLink component={RouterLink} to={`/accounts/${account.id}`} underline="hover">
                {account.name}
              </MuiLink>
            </TableCell>
            <TableCell>
              {accountTypeLabel(account.type, t as unknown as (key: never) => string)}
            </TableCell>
            <TableCell align="right">
              {formatMoney(account.balance, locale)}
              {account.type === 'CREDIT_CARD' && t('dashboard:accountBalances.owedSuffix')}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
