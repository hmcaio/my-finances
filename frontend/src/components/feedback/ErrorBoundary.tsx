import { Component, type PropsWithChildren, type ReactNode } from 'react'
import { Alert, Button } from '@mui/material'
import i18n from '../../i18n/i18n'

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
 *
 * Resolves its fallback text via the `i18next` singleton (F033/ADR 0027, same `apiError.ts`
 * pattern) rather than the `useTranslation()` hook - a class component has no hooks.
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
            {i18n.t('errorBoundary.reload')}
          </Button>
        }
      >
        {i18n.t('errorBoundary.message')}
      </Alert>
    )
  }
}
