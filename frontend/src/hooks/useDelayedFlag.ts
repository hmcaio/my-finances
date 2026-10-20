import { useEffect, useState } from 'react'

/**
 * True only once `active` has been continuously true for `delayMs`. Used to hold back a loading
 * skeleton so a fast response (typical against the local backend) skips it instead of flashing it
 * for a few milliseconds before the real content replaces it.
 */
export function useDelayedFlag(active: boolean, delayMs = 150): boolean {
  const [elapsed, setElapsed] = useState(false)

  useEffect(() => {
    if (!active) return
    const timer = setTimeout(() => setElapsed(true), delayMs)
    return () => {
      clearTimeout(timer)
      setElapsed(false)
    }
  }, [active, delayMs])

  return active && elapsed
}
