import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  IconButton,
  Table,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import type { InvestmentSnapshot } from '../../api/investments/investmentSnapshots'
import { DataTableBody } from '../../components/table/DataTableBody'
import { InlineEditActions } from '../../components/table/InlineEditActions'
import type { LoadState } from '../../hooks/queryState'
import { useLocale } from '../../hooks/locale/useLocale'
import { formatMoney } from '../../i18n/format'
import { PageSection } from '../../components/layout/PageSection'

export interface SnapshotEdit {
  id: string
  date: string
  balance: string
}

interface SnapshotsPanelProps {
  snapshots: InvestmentSnapshot[] | undefined
  state: LoadState
  onRecord: () => void
  editing: SnapshotEdit | null
  onEditingChange: (edit: SnapshotEdit | null) => void
  onSaveEdit: () => void
  savingEdit: boolean
  onDelete: (snapshot: InvestmentSnapshot) => void
}

/**
 * The selected holding's snapshots, most recent first, each editable (date and balance) or
 * deletable. Two data columns (Date, Balance), so it stays a plain table at every size (F021's
 * 1-2-column rule) - only the "Record snapshot" add form became a dialog.
 */
export function SnapshotsPanel({
  snapshots,
  state,
  onRecord,
  editing,
  onEditingChange,
  onSaveEdit,
  savingEdit,
  onDelete,
}: SnapshotsPanelProps) {
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
        <Typography variant="h6">{t('detail.snapshotsTitle')}</Typography>
        <Button variant="contained" onClick={onRecord}>
          {t('detail.recordSnapshotButton')}
        </Button>
      </Box>
      <TableContainer>
        <Table size="small" aria-label={t('detail.snapshotHistoryAria')}>
          <TableHead>
            <TableRow>
              <TableCell>{t('detail.snapshotColumns.date')}</TableCell>
              <TableCell align="right">{t('detail.snapshotColumns.balance')}</TableCell>
              <TableCell align="right">{t('detail.snapshotColumns.actions')}</TableCell>
            </TableRow>
          </TableHead>
          <DataTableBody state={state} onRetry={state.reload} columns={3} actionsColumn>
            {snapshots?.length === 0 && (
              <TableRow>
                <TableCell colSpan={3} align="center">
                  <Typography color="text.secondary">{t('detail.emptySnapshots')}</Typography>
                </TableCell>
              </TableRow>
            )}
            {snapshots?.map((snapshot) => {
              const edit = editing?.id === snapshot.id ? editing : null
              return (
                <TableRow key={snapshot.id}>
                  <TableCell>
                    {edit ? (
                      <TextField
                        size="small"
                        type="date"
                        value={edit.date}
                        onChange={(e) => onEditingChange({ ...edit, date: e.target.value })}
                        slotProps={{
                          htmlInput: { 'aria-label': t('detail.editSnapshotDateAria') },
                        }}
                      />
                    ) : (
                      snapshot.date
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {edit ? (
                      <TextField
                        size="small"
                        type="number"
                        value={edit.balance}
                        onChange={(e) => onEditingChange({ ...edit, balance: e.target.value })}
                        slotProps={{
                          htmlInput: {
                            'aria-label': t('detail.editSnapshotBalanceAria'),
                            step: '0.01',
                            min: '0',
                          },
                        }}
                      />
                    ) : (
                      formatMoney(snapshot.balance, locale)
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <InlineEditActions
                      editing={edit !== null}
                      onEdit={() =>
                        onEditingChange({
                          id: snapshot.id,
                          date: snapshot.date,
                          balance: String(snapshot.balance),
                        })
                      }
                      onSave={onSaveEdit}
                      onCancel={() => onEditingChange(null)}
                      editLabel={t('detail.editSnapshotAria')}
                      saveLabel={t('detail.saveSnapshotAria')}
                      saving={savingEdit}
                    />
                    {!edit && (
                      <IconButton
                        size="small"
                        aria-label={t('detail.deleteSnapshotAria')}
                        onClick={() => onDelete(snapshot)}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              )
            })}
          </DataTableBody>
        </Table>
      </TableContainer>
    </PageSection>
  )
}
