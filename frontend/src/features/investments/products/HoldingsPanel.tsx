import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  Chip,
  IconButton,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import LockIcon from '@mui/icons-material/Lock'
import type { InvestmentHolding } from '../../../api/investments/products/investmentHoldings'
import { DataTableBody } from '../../../components/table/DataTableBody'
import type { LoadState } from '../../../hooks/queryState'
import { useLocale } from '../../../providers/locale/useLocale'
import { formatMoney } from '../../../i18n/format'
import { PageSection } from '../../../components/layout/PageSection'

interface HoldingsPanelProps {
  holdings: InvestmentHolding[] | undefined
  state: LoadState
  accountName: (id: string) => string
  selectedId: string | undefined
  addDisabled: boolean
  onAdd: () => void
  onSelect: (id: string) => void
  onEditNotes: (holding: InvestmentHolding) => void
  onClose: (holding: InvestmentHolding) => void
  onDelete: (holding: InvestmentHolding) => void
}

/** Every account this product is held in - the F022 holding-management panel. */
export function HoldingsPanel({
  holdings,
  state,
  accountName,
  selectedId,
  addDisabled,
  onAdd,
  onSelect,
  onEditNotes,
  onClose,
  onDelete,
}: HoldingsPanelProps) {
  const { t } = useTranslation('investmentProducts')
  const { locale } = useLocale()
  return (
    <PageSection sx={{ mb: 3 }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          mb: 2,
        }}
      >
        <Typography variant="h6">{t('detail.holdingsTitle')}</Typography>
        <Button variant="outlined" size="small" disabled={addDisabled} onClick={onAdd}>
          {t('detail.addHoldingButton')}
        </Button>
      </Box>
      <TableContainer>
        <Table size="small" aria-label={t('detail.holdingsTitle')}>
          <TableHead>
            <TableRow>
              <TableCell>{t('detail.holdingsColumns.account')}</TableCell>
              <TableCell>{t('detail.holdingsColumns.status')}</TableCell>
              <TableCell align="right">{t('detail.holdingsColumns.latestValue')}</TableCell>
              <TableCell align="right">{t('detail.holdingsColumns.actions')}</TableCell>
            </TableRow>
          </TableHead>
          <DataTableBody state={state} onRetry={state.reload} columns={4} actionsColumn>
            {holdings?.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} align="center">
                  <Typography color="text.secondary">{t('detail.emptyHoldings')}</Typography>
                </TableCell>
              </TableRow>
            )}
            {holdings?.map((holding) => (
              <TableRow
                key={holding.id}
                selected={holding.id === selectedId}
                hover
                onClick={() => onSelect(holding.id)}
                sx={{ cursor: 'pointer' }}
              >
                <TableCell>{accountName(holding.accountId)}</TableCell>
                <TableCell>
                  {holding.closed ? (
                    <Chip label={t('detail.statusClosed')} size="small" />
                  ) : (
                    <Chip label={t('detail.statusOpen')} size="small" color="success" />
                  )}
                </TableCell>
                <TableCell align="right">
                  {holding.latestSnapshot
                    ? formatMoney(holding.latestSnapshot.balance, locale)
                    : '-'}
                </TableCell>
                <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                  <IconButton
                    size="small"
                    aria-label={t('detail.editNotesAria')}
                    onClick={() => onEditNotes(holding)}
                  >
                    <EditIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    aria-label={t('detail.closeHoldingAria')}
                    disabled={holding.closed}
                    onClick={() => onClose(holding)}
                  >
                    <LockIcon fontSize="small" />
                  </IconButton>
                  {!holding.hasHistory && (
                    <IconButton
                      size="small"
                      aria-label={t('detail.deleteHoldingAria')}
                      onClick={() => onDelete(holding)}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </DataTableBody>
        </Table>
      </TableContainer>
    </PageSection>
  )
}
