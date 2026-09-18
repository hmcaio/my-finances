import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@mui/material'
import type { ReactNode } from 'react'

interface ConfirmDialogProps {
  open: boolean
  title: ReactNode
  body: ReactNode
  confirmLabel: string
  loading: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * Destructive-action confirmation dialog (delete/close/dismiss), previously copy-pasted into
 * several feature pages with only the title/body/button text differing. `open` controls visibility
 * the same way a `target !== null` check did at each call site - callers keep owning that state.
 */
export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  loading,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onClose={onCancel}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{body}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={loading}>
          Cancel
        </Button>
        <Button onClick={onConfirm} color="error" disabled={loading} autoFocus>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
