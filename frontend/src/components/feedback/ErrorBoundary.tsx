import { Component, type PropsWithChildren, type ReactNode } from 'react'
import { Alert, Button } from '@mui/material'

interface ErrorBoundaryState {
  hasError: boolean
}

/**
 * Keeps a page's render crash from blanking the whole shell (F016, ADR 0011): the fallback
 * replaces only what's inside the boundary, so the nav/theme around it survive. A class component
 * because React still has no hook for `getDerivedStateFromError`.
 *
 * It deliberately doesn't log: `createRoot`'s `onCaughtError` (see `globalErrorLogging.ts`)
 * receives every error a boundary catches, with its component stack. To reset it after the user
 * navigates elsewhere, give it a `key` (`App.tsx` keys it on the route's pathname).
 */
export class ErrorBoundary extends Component<PropsWithChildren, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  render(): ReactNode {
    if (!this.state.hasError) {
      return this.props.children
    }
    return (
      <Alert
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={() => window.location.reload()}>
            Reload
          </Button>
        }
      >
        Something went wrong on this page
      </Alert>
    )
  }
}
