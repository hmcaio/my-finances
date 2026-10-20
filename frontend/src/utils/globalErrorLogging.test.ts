import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { installGlobalErrorLogging, reactRootErrorHandlers } from './globalErrorLogging'
import { logger } from './logger'

function dispatchUnhandledRejection(reason: unknown) {
  // jsdom doesn't implement PromiseRejectionEvent, so build the same shape by hand.
  const event = Object.assign(new Event('unhandledrejection'), { reason })
  window.dispatchEvent(event)
}

describe('installGlobalErrorLogging', () => {
  let errorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    errorSpy = vi.spyOn(logger, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('logs an uncaught window error with the Error object', () => {
    const uninstall = installGlobalErrorLogging()
    const failure = new Error('kaboom')

    window.dispatchEvent(new ErrorEvent('error', { error: failure, message: 'kaboom' }))

    expect(errorSpy).toHaveBeenCalledExactlyOnceWith('Uncaught error', failure)
    uninstall()
  })

  it('falls back to the event message when the error event carries no Error', () => {
    const uninstall = installGlobalErrorLogging()

    window.dispatchEvent(new ErrorEvent('error', { message: 'Script error.' }))

    expect(errorSpy).toHaveBeenCalledExactlyOnceWith('Uncaught error', 'Script error.')
    uninstall()
  })

  it('logs an unhandled promise rejection with its reason', () => {
    const uninstall = installGlobalErrorLogging()
    const reason = new Error('rejected')

    dispatchUnhandledRejection(reason)

    expect(errorSpy).toHaveBeenCalledExactlyOnceWith('Unhandled promise rejection', reason)
    uninstall()
  })

  it('stops logging once uninstalled', () => {
    const uninstall = installGlobalErrorLogging()
    uninstall()

    // No `error` object on purpose: with our listener gone, Vitest's own window error hook would
    // otherwise report a dispatched Error as an unhandled test error.
    window.dispatchEvent(new ErrorEvent('error', { message: 'late' }))
    dispatchUnhandledRejection(new Error('late'))

    expect(errorSpy).not.toHaveBeenCalled()
  })
})

describe('reactRootErrorHandlers', () => {
  let errorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    errorSpy = vi.spyOn(logger, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('logs an uncaught render error with its component stack', () => {
    const failure = new Error('render crash')

    reactRootErrorHandlers.onUncaughtError(failure, { componentStack: '\n    at Broken' })

    expect(errorSpy).toHaveBeenCalledExactlyOnceWith('Uncaught React error', failure, {
      componentStack: '\n    at Broken',
    })
  })

  it('logs an error caught by an error boundary with its component stack', () => {
    const failure = new Error('caught by boundary')

    reactRootErrorHandlers.onCaughtError(failure, { componentStack: '\n    at Page' })

    expect(errorSpy).toHaveBeenCalledExactlyOnceWith(
      'React error caught by an error boundary',
      failure,
      {
        componentStack: '\n    at Page',
      },
    )
  })

  it('logs a recoverable error', () => {
    const failure = new Error('hydration mismatch')

    reactRootErrorHandlers.onRecoverableError(failure, { componentStack: '\n    at Foo' })

    expect(errorSpy).toHaveBeenCalledExactlyOnceWith('Recoverable React error', failure, {
      componentStack: '\n    at Foo',
    })
  })
})
