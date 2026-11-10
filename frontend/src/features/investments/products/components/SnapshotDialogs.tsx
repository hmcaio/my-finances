import { useTranslation } from 'react-i18next'
import { TextField } from '@mui/material'
import { ConfirmDialog } from '../../../../components/feedback/ConfirmDialog'
import { FormDialog } from '../../../../components/feedback/FormDialog'
import { FormGrid } from '../../../../components/feedback/ResponsiveDialog'
import { isValidSnapshotInput, type SnapshotActions } from '../hooks/useSnapshotActions'

interface SnapshotDialogsProps {
  actions: SnapshotActions
  /** The page's save error, shown inside the "Record snapshot" dialog (the page banner is behind it). */
  error: string | null
  onDismissError: () => void
}

/** The delete-snapshot confirmation and the "Record snapshot" dialog. */
export function SnapshotDialogs({ actions, error, onDismissError }: SnapshotDialogsProps) {
  const { t } = useTranslation('investmentProducts')
  const { record, remove } = actions
  return (
    <>
      <ConfirmDialog
        open={remove.target !== null}
        title={t('detail.deleteSnapshotTitle')}
        body={t('detail.deleteSnapshotBody', {
          date: remove.target?.date ?? '',
        })}
        confirmLabel={t('detail.deleteSnapshotConfirm')}
        loading={remove.pending}
        onConfirm={() => void remove.confirm()}
        onCancel={() => remove.setTarget(null)}
      />

      <FormDialog
        open={record.open}
        title={t('detail.recordSnapshotDialogTitle')}
        onClose={record.closeDialog}
        onSubmit={() => void record.submit()}
        canSubmit={isValidSnapshotInput(record.form.date, record.form.balance)}
        busy={record.pending}
        submitLabel={t('detail.recordSnapshotButton')}
        error={error}
        onDismissError={onDismissError}
      >
        <FormGrid>
          <TextField
            label={t('detail.snapshotDateLabel')}
            type="date"
            required
            value={record.form.date}
            onChange={(e) => record.setForm((prev) => ({ ...prev, date: e.target.value }))}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label={t('detail.balanceLabel')}
            type="number"
            required
            autoFocus
            value={record.form.balance}
            onChange={(e) => record.setForm((prev) => ({ ...prev, balance: e.target.value }))}
            helperText={t('detail.balanceHelperText')}
            slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
          />
        </FormGrid>
      </FormDialog>
    </>
  )
}
