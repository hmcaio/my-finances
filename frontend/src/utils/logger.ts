/**
 * The app's only logger, and the only file allowed to call `console.*` (ESLint `no-console` is an
 * error everywhere else; F016, ADR 0011). Browser logs are console-only - nothing is shipped to
 * the backend. Never pass request/response bodies, amounts, descriptions or notes as context.
 *
 * Level resolution, most specific first:
 *   1. `setLogLevel(level)` - a programmatic override (tests).
 *   2. `localStorage['logLevel']` - runtime override for the production build, where the level is
 *      baked in: `localStorage.setItem('logLevel', 'debug')` in devtools, no rebuild needed.
 *   3. `VITE_LOG_LEVEL` (`.env.development` debug, `.env.production` warn, `.env.test` silent).
 *   4. `warn`, when the env value is missing or unknown.
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'silent'

const SEVERITY: Record<LogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
  silent: 100,
}

const STORAGE_KEY = 'logLevel'
const PREFIX = '[my-finances]'
const FALLBACK_LEVEL: LogLevel = 'warn'

let programmaticLevel: LogLevel | null = null

function isLogLevel(value: unknown): value is LogLevel {
  return typeof value === 'string' && Object.hasOwn(SEVERITY, value)
}

function storedLevel(): LogLevel | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return isLogLevel(value) ? value : null
  } catch {
    // Storage can throw or be unavailable (private windows, blocked site data): just ignore it.
    return null
  }
}

function activeLevel(): LogLevel {
  if (programmaticLevel) return programmaticLevel
  const stored = storedLevel()
  if (stored) return stored
  const fromEnv: unknown = import.meta.env.VITE_LOG_LEVEL
  return isLogLevel(fromEnv) ? fromEnv : FALLBACK_LEVEL
}

/** Overrides the level for the rest of the session (`null` clears it). Mainly for tests. */
export function setLogLevel(level: LogLevel | null): void {
  programmaticLevel = level
}

function write(level: Exclude<LogLevel, 'silent'>, message: string, context: unknown[]): void {
  if (SEVERITY[level] < SEVERITY[activeLevel()]) return
  // Delegating to the matching console method (rather than formatting ourselves) keeps devtools'
  // stack traces and object inspection intact.
  console[level](`${PREFIX} ${message}`, ...context)
}

export const logger = {
  debug: (message: string, ...context: unknown[]) => write('debug', message, context),
  info: (message: string, ...context: unknown[]) => write('info', message, context),
  warn: (message: string, ...context: unknown[]) => write('warn', message, context),
  error: (message: string, ...context: unknown[]) => write('error', message, context),
}
