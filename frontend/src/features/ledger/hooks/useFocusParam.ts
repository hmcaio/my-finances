import { useEffect, useEffectEvent } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * Handles a one-shot `?focus={id}` link: calls `onFocus(id)` when the param shows up, then drops
 * only the `focus` key (other params stay) with `replace: true`, so the URL is clean and back does
 * not replay it. Because the key is removed, opening the same link again is a fresh change and
 * works. `focus` is the already-parsed param value (`''` when absent).
 */
export function useFocusParam(focus: string, onFocus: (id: string) => void): void {
  const [, setSearchParams] = useSearchParams()
  const handleFocus = useEffectEvent(onFocus)

  useEffect(() => {
    if (!focus) return
    handleFocus(focus)
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous)
        next.delete('focus')
        return next
      },
      { replace: true },
    )
  }, [focus, setSearchParams])
}
