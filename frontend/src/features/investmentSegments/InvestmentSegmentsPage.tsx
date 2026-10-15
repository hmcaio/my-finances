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
import { useInvestmentSegments } from '../../api/investments/investmentSegmentsQueries'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { DataTableBody } from '../../components/table/DataTableBody'
import { useQueryState } from '../../hooks/queryState'
import { translateSeedName } from '../../i18n/seedNames'

/**
 * Read-only list of investment segments (F026 spec, ADR 0023; F034/ADR 0028): every row is fixed,
 * migration-seeded content (the nine B3 FII sectors) with no create/rename/delete affordance
 * anywhere - the backend's write endpoints were removed outright.
 *
 * Responsive (F021): Name is the only data column, so per the spec's 1-2-column rule this stays a
 * plain table at every size.
 */
export function InvestmentSegmentsPage() {
  const { t } = useTranslation(['investmentSegments', 'common'])
  const [error, setError] = useState<string | null>(null)
  const segmentsQuery = useInvestmentSegments()
  const segments = segmentsQuery.data
  const segmentsState = useQueryState(segmentsQuery, setError)

  // Clears the stale banner before retrying, so it doesn't outlive a successful retry.
  function retry() {
    setError(null)
    segmentsState.reload()
  }

  return (
    <Box sx={{ py: { xs: 2, sm: 4 } }}>
      <Typography variant="h4" component="h1" sx={{ mb: 1 }}>
        {t('investmentSegments:title')}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('investmentSegments:description')}
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
            <DataTableBody state={segmentsState} onRetry={retry} columns={1}>
              {segments?.length === 0 && (
                <TableRow>
                  <TableCell align="center">
                    <Typography color="text.secondary">
                      {t('investmentSegments:emptyList')}
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {segments?.map((segment) => (
                <TableRow key={segment.id}>
                  <TableCell>{translateSeedName('investmentSegments', segment.name, t)}</TableCell>
                </TableRow>
              ))}
            </DataTableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  )
}
