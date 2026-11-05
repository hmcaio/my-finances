import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, DialogActions, DialogTitle, MenuItem, Select, TextField } from '@mui/material'
import { ConfirmDialog } from '../../../../components/feedback/ConfirmDialog'
import { FormGrid, ResponsiveDialog } from '../../../../components/feedback/ResponsiveDialog'
import type { HoldingActions } from '../hooks/useHoldingActions'
import { FormDialogContent } from '../../../../components/feedback/FormDialogContent'

interface HoldingDialogsProps {
  actions: HoldingActions
  accountName: (id: string) => string
  /** The page's error banner, shown inside the dialogs (the page one is behind them). */
  banner: ReactNode
}

/** The add-holding and edit-notes dialogs and the close / delete holding confirmations. */
export function HoldingDialogs({ actions, accountName, banner }: HoldingDialogsProps) {
  const { t } = useTranslation(['investmentProducts', 'common'])
  const { add, notes, closing, removing } = actions
  return (
    <>
      <ResponsiveDialog
        open={add.open}
        onClose={add.pending ? undefined : add.close}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{t('investmentProducts:detail.addHoldingDialogTitle')}</DialogTitle>
        <FormDialogContent>
          {banner}
          <FormGrid>
            <Select
              size="small"
              displayEmpty
              value={add.accountId}
              onChange={(e) => add.setAccountId(e.target.value)}
              aria-label={t('investmentProducts:detail.accountLabel')}
            >
              <MenuItem value="" disabled>
                {t('investmentProducts:detail.accountLabel')}
              </MenuItem>
              {add.availableAccounts.map((a) => (
                <MenuItem key={a.id} value={a.id}>
                  {a.name}
                </MenuItem>
              ))}
            </Select>
            <TextField
              label={t('investmentProducts:detail.notesLabel')}
              size="small"
              multiline
              value={add.notes}
              onChange={(e) => add.setNotes(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          </FormGrid>
        </FormDialogContent>
        <DialogActions>
          <Button onClick={add.close} disabled={add.pending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            disabled={add.pending || add.accountId === ''}
            onClick={() => void add.submit()}
          >
            {t('investmentProducts:detail.addHoldingButton')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ResponsiveDialog
        open={notes.edit !== null}
        onClose={notes.pending ? undefined : () => notes.setEdit(null)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{t('investmentProducts:detail.editHoldingNotesTitle')}</DialogTitle>
        <FormDialogContent>
          {banner}
          <TextField
            label={t('investmentProducts:detail.notesLabel')}
            size="small"
            fullWidth
            multiline
            value={notes.edit?.value ?? ''}
            onChange={(e) =>
              notes.setEdit((prev) => (prev ? { ...prev, value: e.target.value } : prev))
            }
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </FormDialogContent>
        <DialogActions>
          <Button onClick={() => notes.setEdit(null)} disabled={notes.pending}>
            {t('common:actions.cancel')}
          </Button>
          <Button variant="contained" disabled={notes.pending} onClick={() => void notes.save()}>
            {t('common:actions.save')}
          </Button>
        </DialogActions>
      </ResponsiveDialog>

      <ConfirmDialog
        open={closing.target !== null}
        title={t('investmentProducts:detail.closeHoldingTitle', {
          account: closing.target ? accountName(closing.target.accountId) : '',
        })}
        body={t('investmentProducts:detail.closeHoldingBody')}
        confirmLabel={t('investmentProducts:detail.closeHoldingConfirm')}
        loading={actions.confirmPending}
        onConfirm={() => void closing.confirm()}
        onCancel={() => closing.setTarget(null)}
      />

      <ConfirmDialog
        open={removing.target !== null}
        title={t('investmentProducts:detail.deleteHoldingTitle', {
          account: removing.target ? accountName(removing.target.accountId) : '',
        })}
        body={t('investmentProducts:detail.deleteHoldingBody')}
        confirmLabel={t('investmentProducts:detail.deleteHoldingConfirm')}
        loading={actions.confirmPending}
        onConfirm={() => void removing.confirm()}
        onCancel={() => removing.setTarget(null)}
      />
    </>
  )
}
