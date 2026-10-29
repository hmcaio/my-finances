import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, DialogActions, DialogContent, DialogTitle, Typography, Box } from '@mui/material'
import type {
  OpeningPosition,
  OpeningPositionRequest,
} from '../../api/investments/investmentOpeningPositions'
import { ErrorAlert } from '../../components/feedback/ErrorAlert'
import { FormGrid, ResponsiveDialog } from '../../components/feedback/ResponsiveDialog'
import {
  draftOf,
  emptyOpeningPositionDraft,
  openingPositionRequestOf,
  type OpeningPositionDraft,
} from './openingPositionDraft'
import { OpeningPositionFields } from './OpeningPositionFields'

interface OpeningPositionDialogProps {
  open: boolean
  /** The position being edited; `null` adds one. */
  position: OpeningPosition | null
  saving: boolean
  /** A save error, shown inside the dialog (the page banner sits behind it). */
  error: string | null
  onDismissError: () => void
  onSave: (request: OpeningPositionRequest) => void
  onClose: () => void
}

/**
 * Add/edit dialog of a holding's opening position (F038, ADR 0033). The form mounts fresh for each
 * open (`DialogBody` is only rendered while open), so Cancel, Esc and a saved add all reopen with
 * the as-of date at today. An edit starts from the stored values; the current value is not stored
 * on the position, so it starts empty (leave it empty to keep the snapshots as they are).
 */
export function OpeningPositionDialog({
  open,
  position,
  saving,
  error,
  onDismissError,
  onSave,
  onClose,
}: OpeningPositionDialogProps) {
  const { t } = useTranslation(['investmentProducts', 'common'])
  return (
    <ResponsiveDialog open={open} onClose={saving ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        {position
          ? t('investmentProducts:openingPosition.editDialogTitle')
          : t('investmentProducts:openingPosition.addDialogTitle')}
      </DialogTitle>
      {open && (
        <DialogBody
          position={position}
          saving={saving}
          error={error}
          onDismissError={onDismissError}
          onSave={onSave}
          onClose={onClose}
        />
      )}
    </ResponsiveDialog>
  )
}

function DialogBody({
  position,
  saving,
  error,
  onDismissError,
  onSave,
  onClose,
}: Omit<OpeningPositionDialogProps, 'open'>) {
  const { t } = useTranslation(['investmentProducts', 'common'])
  const [draft, setDraft] = useState<OpeningPositionDraft>(() =>
    position ? draftOf(position) : emptyOpeningPositionDraft(),
  )
  const request = openingPositionRequestOf(draft)
  return (
    <>
      <DialogContent>
        <Box sx={{ pt: 1 }}>
          <ErrorAlert message={error} onDismiss={onDismissError} />
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('investmentProducts:openingPosition.noCashNote')}
          </Typography>
          <FormGrid>
            <OpeningPositionFields
              draft={draft}
              onChange={setDraft}
              currentValueHelper={t('investmentProducts:openingPosition.currentValueHelper')}
            />
          </FormGrid>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          disabled={saving || request === null}
          onClick={() => request && onSave(request)}
        >
          {t('investmentProducts:openingPosition.saveButton')}
        </Button>
      </DialogActions>
    </>
  )
}
