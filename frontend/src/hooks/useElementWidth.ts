import { useEffect, useRef, useState, type RefObject } from 'react'

/**
 * Tracks the content width of an element, in whole pixels, with a `ResizeObserver`. Returns the ref
 * to attach and the width, which is `null` until the first measurement and stays `null` where
 * `ResizeObserver` does not exist (jsdom), so a caller falls back to a default size. Used by charts
 * that size their drawing to the space they are given instead of scaling a fixed frame.
 */
export function useElementWidth<T extends HTMLElement>(): [RefObject<T | null>, number | null] {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState<number | null>(null)

  useEffect(() => {
    const element = ref.current
    if (!element || typeof ResizeObserver === 'undefined') return undefined
    const observer = new ResizeObserver((entries) => {
      const next = entries[0]?.contentRect.width
      if (next !== undefined) setWidth(Math.floor(next))
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return [ref, width]
}
