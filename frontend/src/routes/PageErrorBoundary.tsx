import type { PropsWithChildren } from 'react'
import { useLocation } from 'react-router-dom'
import { ErrorBoundary } from '../components/feedback/ErrorBoundary'

/**
 * Wraps the routed page (inside `Layout`, so the nav/theme survive a page crash). Keyed on the
 * pathname so navigating to another page resets a boundary that has already tripped.
 */
export function PageErrorBoundary({ children }: PropsWithChildren) {
  const { pathname } = useLocation()
  return <ErrorBoundary key={pathname}>{children}</ErrorBoundary>
}
