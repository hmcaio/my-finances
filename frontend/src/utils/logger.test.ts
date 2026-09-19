import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { logger, setLogLevel } from './logger'

// `.env.test` sets VITE_LOG_LEVEL=silent, so every test here picks its level explicitly: with
// vi.stubEnv for the env-driven cases, or setLogLevel for the programmatic override.
describe('logger', () => {
  let debugSpy: ReturnType<typeof vi.spyOn>
  let infoSpy: ReturnType<typeof vi.spyOn>
  let warnSpy: ReturnType<typeof vi.spyOn>
  let errorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    debugSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})
    infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {})
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllEnvs()
    setLogLevel(null)
    localStorage.clear()
  })

  it('delegates each method to the matching console method with the prefix and context', () => {
    vi.stubEnv('VITE_LOG_LEVEL', 'debug')
    const failure = new Error('boom')

    logger.debug('d', { a: 1 })
    logger.info('i')
    logger.warn('w', 'extra')
    logger.error('e', failure)

    expect(debugSpy).toHaveBeenCalledExactlyOnceWith('[my-finances] d', { a: 1 })
    expect(infoSpy).toHaveBeenCalledExactlyOnceWith('[my-finances] i')
    expect(warnSpy).toHaveBeenCalledExactlyOnceWith('[my-finances] w', 'extra')
    // An Error passed as context reaches console untouched, so devtools keeps its stack.
    expect(errorSpy).toHaveBeenCalledExactlyOnceWith('[my-finances] e', failure)
  })

  it('drops everything below the configured level', () => {
    vi.stubEnv('VITE_LOG_LEVEL', 'warn')

    logger.debug('d')
    logger.info('i')
    logger.warn('w')
    logger.error('e')

    expect(debugSpy).not.toHaveBeenCalled()
    expect(infoSpy).not.toHaveBeenCalled()
    expect(warnSpy).toHaveBeenCalledOnce()
    expect(errorSpy).toHaveBeenCalledOnce()
  })

  it('logs nothing at all when the level is silent', () => {
    vi.stubEnv('VITE_LOG_LEVEL', 'silent')

    logger.debug('d')
    logger.info('i')
    logger.warn('w')
    logger.error('e')

    expect(debugSpy).not.toHaveBeenCalled()
    expect(infoSpy).not.toHaveBeenCalled()
    expect(warnSpy).not.toHaveBeenCalled()
    expect(errorSpy).not.toHaveBeenCalled()
  })

  it('falls back to warn for an unknown VITE_LOG_LEVEL value', () => {
    vi.stubEnv('VITE_LOG_LEVEL', 'verbose')

    logger.info('i')
    logger.warn('w')

    expect(infoSpy).not.toHaveBeenCalled()
    expect(warnSpy).toHaveBeenCalledOnce()
  })

  it('falls back to warn when VITE_LOG_LEVEL is missing', () => {
    vi.stubEnv('VITE_LOG_LEVEL', undefined)

    logger.info('i')
    logger.warn('w')

    expect(infoSpy).not.toHaveBeenCalled()
    expect(warnSpy).toHaveBeenCalledOnce()
  })

  it('lets a valid localStorage logLevel win over the env level', () => {
    vi.stubEnv('VITE_LOG_LEVEL', 'silent')
    localStorage.setItem('logLevel', 'debug')

    logger.debug('d')

    expect(debugSpy).toHaveBeenCalledOnce()
  })

  it('ignores an invalid localStorage logLevel and keeps the env level', () => {
    vi.stubEnv('VITE_LOG_LEVEL', 'error')
    localStorage.setItem('logLevel', 'chatty')

    logger.warn('w')
    logger.error('e')

    expect(warnSpy).not.toHaveBeenCalled()
    expect(errorSpy).toHaveBeenCalledOnce()
  })

  it('keeps working when reading localStorage throws', () => {
    vi.stubEnv('VITE_LOG_LEVEL', 'info')
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })

    expect(() => logger.info('i')).not.toThrow()

    expect(infoSpy).toHaveBeenCalledOnce()
  })

  it('setLogLevel overrides both the env and localStorage until cleared with null', () => {
    vi.stubEnv('VITE_LOG_LEVEL', 'silent')
    localStorage.setItem('logLevel', 'silent')

    setLogLevel('info')
    logger.info('i')
    expect(infoSpy).toHaveBeenCalledOnce()

    setLogLevel(null)
    logger.info('again')
    expect(infoSpy).toHaveBeenCalledOnce()
  })
})
