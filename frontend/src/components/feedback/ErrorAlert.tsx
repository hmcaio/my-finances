import { Alert } from '@mui/material'

interface ErrorAlertProps {
  message: string | null
  onDismiss: () => void
}

/**
 * Dismissible error banner, previously copy-pasted verbatim into nearly every feature page. Renders
 * nothing when `message` is `null` - callers keep their own `error` state and just pass it straight
 * through, same as the inline markup this replaces.
 */
export function ErrorAlert({ message, onDismiss }: ErrorAlertProps) {
  if (!message) return null
  return (
    <Alert severity="error" sx={{ mb: 2 }} onClose={onDismiss}>
      {message}
    </Alert>
  )
}
