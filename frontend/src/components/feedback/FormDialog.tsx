import type { FormEvent, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, DialogActions, DialogTitle, Typography, type PaperProps } from '@mui/material'
import { ErrorAlert } from './ErrorAlert'
import { FormDialogContent } from './FormDialogContent'
import { ResponsiveDialog } from './ResponsiveDialog'

interface FormDialogProps {
  open: boolean
  title: string
  /** Cancel button and Esc/backdrop; ignored while `busy`. */
  onClose: () => void
  /** The primary action; only called while `canSubmit` and not `busy`. */
  onSubmit: () => void
  /** The draft is valid; the primary button stays disabled until it is. */
  canSubmit: boolean
  /** A save is in flight: Cancel and the primary button are disabled, Enter and Esc do nothing. */
  busy: boolean
  /** Label of the primary (`type="submit"`) button. */
  submitLabel: string
  /** The save error, shown inside the open dialog (the page's own banner is behind it). */
  error: string | null
  onDismissError: () => void
  /** The fields (usually a `FormGrid`); give required ones `required` and the first `autoFocus`. */
  children: ReactNode
  /** Buttons rendered between Cancel and the primary button (e.g. "Add and new"). */
  extraActions?: ReactNode
}

/**
 * The shell of a form dialog (F040): a `ResponsiveDialog` whose paper is a `<form noValidate>`, so
 * Enter in a single-line field submits the primary action (`type="submit"`) and the browser's own
 * validation bubble never appears: "required" is shown by MUI's asterisk plus the hint line, and the
 * primary button stays disabled until the draft is valid. The submit handler guards `canSubmit` and
 * `busy` itself, so Enter does nothing in either state whatever the browser's implicit-submission
 * rule does with a disabled submit button. The hint sits in the scrolling body, never in the
 * actions row, so it cannot push the buttons off a phone screen.
 */
export function FormDialog({
  open,
  title,
  onClose,
  onSubmit,
  canSubmit,
  busy,
  submitLabel,
  error,
  onDismissError,
  children,
  extraActions,
}: FormDialogProps) {
  const { t } = useTranslation('common')

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (canSubmit && !busy) onSubmit()
  }

  // The paper slot types do not list the `form` props that `component: 'form'` forwards to the DOM.
  const formPaperProps = {
    component: 'form',
    noValidate: true,
    onSubmit: handleSubmit,
  } as PaperProps<'form'>

  return (
    <ResponsiveDialog
      open={open}
      onClose={busy ? undefined : onClose}
      maxWidth="sm"
      fullWidth
      slotProps={{ paper: formPaperProps }}
    >
      <DialogTitle>{title}</DialogTitle>
      <FormDialogContent>
        <ErrorAlert message={error} onDismiss={onDismissError} />
        {children}
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
          {t('form.requiredHint')}
        </Typography>
      </FormDialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          {t('actions.cancel')}
        </Button>
        {extraActions}
        <Button type="submit" variant="contained" disabled={busy || !canSubmit}>
          {submitLabel}
        </Button>
      </DialogActions>
    </ResponsiveDialog>
  )
}
