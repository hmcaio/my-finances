import { useEffect, useState } from 'react'

/**
 * `value`, but only after it has stopped changing for `delayMs`. The Ledger's search box feeds
 * its keystrokes through this so one request fires per pause instead of one per character.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
