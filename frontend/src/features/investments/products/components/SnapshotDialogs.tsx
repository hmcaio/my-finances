import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, DialogActions, DialogTitle, TextField } from '@mui/material'
import { ConfirmDialog } from '../../../../components/feedback/ConfirmDialog'
import { FormGrid, ResponsiveDialog } from '../../../../components/feedback/ResponsiveDialog'
import { isValidSnapshotInput, type SnapshotActions } from '../hooks/useSnapshotActions'
import { FormDialogContent } from '../../../../components/feedback/FormDialogContent'

interface SnapshotDialogsProps {
  actions: SnapshotActions
  /** The page's error banner, shown inside the "Record snapshot" dialog (the page one is behind it). */
  banner: ReactNode
}

/** The delete-snapshot confirmation and the "Record snapshot" dialog. */
export function SnapshotDialogs({ actions, banner }: SnapshotDialogsProps) {
  const { t } = useTranslation(['investmentProducts', 'common'])
  const { record, remove } = actions
  return (
    <>
      <ConfirmDialog
        open={remove.target !== null}
        title={t('investmentProducts:detail.deleteSnapshotTitle')}
        body={t('investmentProducts:detail.deleteSnapshotBody', {
          date: remove.target?.date ?? '',
        })}
        confirmLabel={t('investmentProducts:detail.deleteSnapshotConfirm')}
        loading={remove.pending}
        onConfirm={() => void remove.confirm()}
        onCancel={() => remove.setTarget(null)}
      />

      <ResponsiveDialog
        open={record.open}
        onClose={record.pending ? undefined : record.closeDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{t('investmentProducts:detail.recordSnapshotDialogTitle')}</DialogTitle>
        <FormDialogContent>
          {banner}
          <FormGrid>
            <TextField
              label={t('investmentProducts:detail.snapshotDateLabel')}
              type="date"
              value={record.form.date}
              onChange={(e) => record.setForm((prev) => ({ ...prev, date: e.target.value }))}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label={t('investmentProducts:detail.balanceLabel')}
              type="number"
              value={record.form.balance}
              onChange={(e) => record.setForm((prev) => ({ ...prev, balance: e.target.value }))}
              helperText={t('investmentProducts:detail.balanceHelperText')}
              slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
            />
          </FormGrid>
        </FormDialogContent>
        <DialogActions>
          <Button onClick={record.closeDialog} disabled={record.pending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={
              record.pending || !isValidSnapshotInput(record.form.date, record.form.balance)
            }
            onClick={() => void record.submit()}
          >
            {t('investmentProducts:detail.recordSnapshotButton')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    </>
  )
}
