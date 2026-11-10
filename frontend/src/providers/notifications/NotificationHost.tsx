import { useEffect, useState, useSyncExternalStore, type SyntheticEvent } from 'react'
import { Alert, Portal, Snackbar, type SnackbarCloseReason } from '@mui/material'
import { DRAWER_WIDTH } from '../../components/layout/layoutSx'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { getSnapshot, subscribe } from './notificationStore'

const AUTO_HIDE_MS = 4000

/** Over the app bar and above the page, so nothing a tap needs is under it. */
const PHONE_SX = { pointerEvents: 'none' } as const
/** From `lg` the permanent drawer takes the left edge: start beside it, not over its last items. */
const DESKTOP_SX = { left: { lg: DRAWER_WIDTH + 24 } } as const

/**
 * A `body` child of its own that the toast portals into, kept out of MUI's aria-hidden (F040).
 * While a Modal (a `Dialog`, a `Menu`, a `Select` list) is open, MUI's modal manager marks every
 * other child of `body` `aria-hidden="true"`, `#root` included, and a toast inside that subtree is
 * not announced by a screen reader; "Add and new" keeps the dialog open, and a plain "Add" shows
 * its toast before the dialog has closed. MUI has no opt-out (no ignore attribute, one manager for
 * every modal), and a node created after a modal opened is hidden by the next modal. So the host
 * exists from mount on and drops the attribute whenever it appears on it; the dialog still hides
 * the app (`#root`) behind it. The element is created in the initializer (appended and observed in
 * the effect), so there is no state update in an effect.
 */
function useAnnouncementHost(): HTMLElement {
  const [host] = useState(() => document.createElement('div'))
  useEffect(() => {
    document.body.appendChild(host)
    const observer = new MutationObserver(() => host.removeAttribute('aria-hidden'))
    observer.observe(host, { attributes: true, attributeFilter: ['aria-hidden'] })
    return () => {
      observer.disconnect()
      host.remove()
    }
  }, [host])
  return host
}

/**
 * Renders the store's current message as a success toast (F040); `Layout` mounts it once. On a
 * phone it sits at the top, over the app bar and above the page content: a full-screen dialog's
 * actions are at the bottom and the page's own "Add" button is under the header, so neither is
 * covered by anything that takes a tap (`pointerEvents: 'none'`). From `sm` up it is bottom start,
 * beside the drawer from `lg`.
 * A second message replaces the first (the `key` remounts the Snackbar, restarting the timer).
 * It is portalled into `useAnnouncementHost`'s element, so it is announced even while a dialog is
 * open.
 */
export function NotificationHost() {
  const notification = useSyncExternalStore(subscribe, getSnapshot)
  const isMobile = useIsMobile()
  const host = useAnnouncementHost()
  // The message stays in the store after it hides, so the exit transition can finish; the id of
  // the one the user (or the timer) closed is what marks it closed.
  const [closedId, setClosedId] = useState<number | null>(null)

  if (notification === null) return null

  function handleClose(_event: Event | SyntheticEvent | null, reason?: SnackbarCloseReason) {
    if (reason === 'clickaway') return
    setClosedId(notification?.id ?? null)
  }

  return (
    <Portal container={host}>
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
        sx={isMobile ? PHONE_SX : DESKTOP_SX}
      >
        <Alert severity="success" variant="filled" role="status" sx={{ width: '100%' }}>
          {notification.message}
        </Alert>
      </Snackbar>
    </Portal>
  )
}
