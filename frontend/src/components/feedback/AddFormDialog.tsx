import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, DialogActions, DialogContent, DialogTitle } from '@mui/material'
import { ErrorAlert } from './ErrorAlert'
import { FormGrid, ResponsiveDialog } from './ResponsiveDialog'

interface AddFormDialogProps {
  open: boolean
  title: string
  /** The page's save error, shown inside the open dialog (the page hides its own banner then). */
  error: string | null
  onDismissError: () => void
  /** A save is in flight: Cancel and Add are disabled. */
  busy: boolean
  /** The draft is valid; Add stays disabled until it is. */
  canSubmit: boolean
  /** `FormGrid` columns from `sm` up (one column on a phone either way). Default 2. */
  columns?: number
  onSubmit: () => void
  /** Cancel button and Esc/backdrop. The page's handler resets the draft (`useAddDialog.close`). */
  onCancel: () => void
  /** The fields, laid out in the `FormGrid`. */
  children: ReactNode
  /** Rendered below the grid, inside the dialog body (e.g. a hint about the fields). */
  afterFields?: ReactNode
}

/**
 * The shell the settings-style pages share for their "Add" dialog (Institutions, Categories,
 * Vehicles, Budgets, Recurring templates; issue #232): a `ResponsiveDialog` with the title, the
 * error banner, the fields in a `FormGrid`, and Cancel / Add. The fields and the draft state stay
 * in the page (`useAddDialog`), since they differ per page.
 */
export function AddFormDialog({
  open,
  title,
  error,
  onDismissError,
  busy,
  canSubmit,
  columns,
  onSubmit,
  onCancel,
  children,
  afterFields,
}: AddFormDialogProps) {
  const { t } = useTranslation('common')
  return (
    <ResponsiveDialog open={open} onClose={onCancel}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Box sx={{ pt: 1 }}>
          <ErrorAlert message={error} onDismiss={onDismissError} />
          <FormGrid columns={columns}>{children}</FormGrid>
          {afterFields}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={busy}>
          {t('actions.cancel')}
        </Button>
        <Button variant="contained" disabled={busy || !canSubmit} onClick={onSubmit}>
          {t('actions.add')}
        </Button>
      </DialogActions>
    </ResponsiveDialog>
  )
}
