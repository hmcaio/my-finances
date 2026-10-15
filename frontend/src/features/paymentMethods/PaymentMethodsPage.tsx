import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Paper,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { usePaymentMethods } from '../../api/paymentMethods/paymentMethodsQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'
import { translateSeedName } from '../../i18n/seedNames'

/**
 * Read-only list of payment methods (F002 spec, F034/ADR 0028): every row is fixed,
 * migration-seeded content (Debit Card, Credit Card, PIX, Cash) with no create/rename/delete
 * affordance anywhere - the backend's write endpoints were removed outright.
 *
 * Responsive (F021): Name is the only data column, so per the spec's 1-2-column rule this stays a
 * plain table at every size.
 */
export function PaymentMethodsPage() {
  const { t } = useTranslation(['paymentMethods', 'common'])
  const [error, setError] = useState<string | null>(null)
  const paymentMethodsQuery = usePaymentMethods()
  const paymentMethods = paymentMethodsQuery.data
  const paymentMethodsState = useQueryState(paymentMethodsQuery, setError)

  // Clears the stale banner before retrying, so it doesn't outlive a successful retry.
  function retry() {
    setError(null)
    paymentMethodsState.reload()
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Typography variant="h4" component="h1" sx={{ mb: 1 }}>
        {t('paymentMethods:title')}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('paymentMethods:description')}
      </Typography>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      <Paper variant="outlined" sx={{ mb: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('common:actions.name')}</TableCell>
              </TableRow>
            </TableHead>
            <DataTableBody state={paymentMethodsState} onRetry={retry} columns={1}>
              {paymentMethods?.length === 0 && (
                <TableRow>
                  <TableCell align="center">
                    <Typography color="text.secondary">{t('paymentMethods:emptyList')}</Typography>
                  </TableCell>
                </TableRow>
              )}
              {paymentMethods?.map((paymentMethod) => (
                <TableRow key={paymentMethod.id}>
                  <TableCell>
                    {translateSeedName('paymentMethods', paymentMethod.name, t)}
                  </TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  )
}
