import type { FormEvent, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, DialogActions, Typography } from '@mui/material'
import { FormDialogContent } from './FormDialogContent'

interface FormDialogSectionsProps {
  /** The fields (and the error banner above them); give required ones `required`. */
  children: ReactNode
  /** Cancel button. */
  onCancel: () => void
  /** The draft is valid; the primary button stays disabled until it is. */
  canSubmit: boolean
  /** A save is in flight: Cancel and the primary button are disabled. */
  busy: boolean
  /** Label of the primary (`type="submit"`) button. */
  submitLabel: string
  /** Buttons rendered between Cancel and the primary button (e.g. "Add and new"). */
  extraActions?: ReactNode
}

/**
 * The scrolling body (fields, then the "Fields marked * are required" hint) and the actions row
 * (Cancel, extra actions, the `type="submit"` primary button) every `FormDialog` has. Rendered
 * inside a `<form>`: `FormDialog` makes the dialog paper that form, `FormDialogPane` a form of its
 * own. The hint sits in the body, never in the actions row, so it cannot push the buttons off a
 * phone screen.
 */
export function FormDialogSections({
  children,
  onCancel,
  canSubmit,
  busy,
  submitLabel,
  extraActions,
}: FormDialogSectionsProps) {
  const { t } = useTranslation('common')
  return (
    <>
      <FormDialogContent>
        {children}
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
          {t('form.requiredHint')}
        </Typography>
      </FormDialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={busy}>
          {t('actions.cancel')}
        </Button>
        {extraActions}
        <Button type="submit" variant="contained" disabled={busy || !canSubmit}>
          {submitLabel}
        </Button>
      </DialogActions>
    </>
  )
}

interface FormDialogPaneProps extends FormDialogSectionsProps {
  /** The primary action; only called while `canSubmit` and not `busy`. */
  onSubmit: () => void
}

/**
 * A form that lives inside a dialog the caller owns (F040): for a dialog whose body changes while
 * it stays open (the Ledger's New entry dialog swaps its forms when the type toggle moves), so the
 * `<form>` cannot be the dialog paper as in `FormDialog`. It is a flex column of its own, so the
 * body still scrolls and the actions stay put in a full-screen phone dialog. The same rules as
 * `FormDialog`: `noValidate`, Enter in a single-line field submits the primary action only while
 * `canSubmit && !busy`, and the buttons in `extraActions` must be `type="button"`.
 */
export function FormDialogPane({ onSubmit, ...sections }: FormDialogPaneProps) {
  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (sections.canSubmit && !sections.busy) onSubmit()
  }
  return (
    <Box
      component="form"
      noValidate
      onSubmit={handleSubmit}
      sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 auto', minHeight: 0 }}
    >
      <FormDialogSections {...sections} />
    </Box>
  )
}
