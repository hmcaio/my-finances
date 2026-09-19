import { logger } from './logger'

/**
 * Routes errors nothing else would record to `logger.error` (F016, ADR 0011): scripts that throw
 * outside React (`window` `error`) and promises nobody handled (`unhandledrejection`). Call once
 * from `main.tsx`; the returned function removes both listeners again (tests).
 */
export function installGlobalErrorLogging(): () => void {
  const onError = (event: ErrorEvent) => {
    logger.error('Uncaught error', event.error ?? event.message)
  }
  const onUnhandledRejection = (event: PromiseRejectionEvent) => {
    logger.error('Unhandled promise rejection', event.reason)
  }

  window.addEventListener('error', onError)
  window.addEventListener('unhandledrejection', onUnhandledRejection)
  return () => {
    window.removeEventListener('error', onError)
    window.removeEventListener('unhandledrejection', onUnhandledRejection)
  }
}

interface ReactErrorInfo {
  componentStack?: string | undefined
}

// Only the component stack is logged, not React's whole errorInfo (its `errorBoundary` is a live
// component instance).
function logReactError(message: string) {
  return (error: unknown, errorInfo: ReactErrorInfo) => {
    logger.error(message, error, { componentStack: errorInfo.componentStack })
  }
}

/**
 * `createRoot` options (React 19) so every render-time error reaches `logger.error` with its
 * component stack. `onCaughtError` receives everything an error boundary catches, which is why
 * `ErrorBoundary` itself doesn't log. Passing these replaces React's default `console.error`.
 */
export const reactRootErrorHandlers = {
  onUncaughtError: logReactError('Uncaught React error'),
  onCaughtError: logReactError('React error caught by an error boundary'),
  onRecoverableError: logReactError('Recoverable React error'),
}
