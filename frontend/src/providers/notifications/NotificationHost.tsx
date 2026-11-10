import { useState, useSyncExternalStore, type SyntheticEvent } from 'react'
import { Alert, Snackbar, type SnackbarCloseReason } from '@mui/material'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { getSnapshot, subscribe } from './notificationStore'

const AUTO_HIDE_MS = 4000

/**
 * Renders the store's current message as a success toast (F040); `Layout` mounts it once. On a
 * phone it sits at the top, over the app bar and above the page content: a full-screen dialog's
 * actions are at the bottom and the page's own "Add" button is under the header, so neither is
 * covered by anything that takes a tap (`pointerEvents: 'none'`). From `sm` up it is bottom start.
 * A second message replaces the first (the `key` remounts the Snackbar, restarting the timer).
 */
export function NotificationHost() {
  const notification = useSyncExternalStore(subscribe, getSnapshot)
  const isMobile = useIsMobile()
  // The message stays in the store after it hides, so the exit transition can finish; the id of
  // the one the user (or the timer) closed is what marks it closed.
  const [closedId, setClosedId] = useState<number | null>(null)

  if (notification === null) return null

  function handleClose(_event: Event | SyntheticEvent | null, reason?: SnackbarCloseReason) {
    if (reason === 'clickaway') return
    setClosedId(notification?.id ?? null)
  }

  return (
    <Snackbar
      key={notification.id}
      open={closedId !== notification.id}
      autoHideDuration={AUTO_HIDE_MS}
      onClose={handleClose}
      anchorOrigin={
        isMobile
          ? { vertical: 'top', horizontal: 'center' }
          : { vertical: 'bottom', horizontal: 'left' }
      }
      sx={isMobile ? { pointerEvents: 'none' } : undefined}
    >
      <Alert severity="success" variant="filled" role="status" sx={{ width: '100%' }}>
        {notification.message}
      </Alert>
    </Snackbar>
  )
}
